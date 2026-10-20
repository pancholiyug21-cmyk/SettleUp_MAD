package com.example.splitwsie.presentation.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitwsie.data.local.SessionManager
import com.example.splitwsie.domain.model.FriendRelationship
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.repository.FriendRepository
import com.example.splitwsie.domain.repository.UserRepository
import com.example.splitwsie.domain.repository.ExpenseRepository
import com.example.splitwsie.domain.repository.SettlementRepository
import com.example.splitwsie.domain.usecase.BalanceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendsState(
    val currentUserId: String = "",
    val searchResults: List<User> = emptyList(),
    val friends: List<FriendUiModel> = emptyList(),
    val pendingRequests: List<FriendRequestUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = ""
)

data class FriendUiModel(
    val user: User,
    val balance: Double
)

data class FriendRequestUiModel(
    val requester: User,
    val relationship: FriendRelationship
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val friendRepository: FriendRepository,
    private val userRepository: UserRepository,
    private val expenseRepository: ExpenseRepository,
    private val settlementRepository: SettlementRepository,
    private val balanceCalculator: BalanceCalculator
) : ViewModel() {

    private val _state = MutableStateFlow(FriendsState())
    val state: StateFlow<FriendsState> = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val currentUserId = sessionManager.currentUserId ?: return
        _state.update { it.copy(currentUserId = currentUserId, isLoading = true) }
        
        viewModelScope.launch {
            val friends = friendRepository.getAcceptedFriendsForUser(currentUserId)
            val pendingRels = friendRepository.getPendingRequestsForUser(currentUserId)
            
            // Calculate balances
            val allExpenses = expenseRepository.getAllExpensesForUser(currentUserId).first()
            val settlements = settlementRepository.observeAll().first()
            val pairwiseDebts = balanceCalculator.calculatePairwiseBalances(allExpenses, settlements)
            
            val friendUiModels = friends.map { friend ->
                val amountOwedToMe = pairwiseDebts.firstOrNull { it.fromUserId == friend.id && it.toUserId == currentUserId }?.amount ?: 0.0
                val amountIOweThem = pairwiseDebts.firstOrNull { it.fromUserId == currentUserId && it.toUserId == friend.id }?.amount ?: 0.0
                val netBalance = amountOwedToMe - amountIOweThem
                FriendUiModel(friend, netBalance)
            }
            
            val pendingUiModels = pendingRels.mapNotNull { rel ->
                val requester = userRepository.getUserById(rel.requesterId)
                if (requester != null) {
                    FriendRequestUiModel(requester, rel)
                } else null
            }
            
            _state.update { 
                it.copy(
                    friends = friendUiModels,
                    pendingRequests = pendingUiModels,
                    isLoading = false
                ) 
            }
        }
    }

    fun searchUsers(query: String) {
        _state.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        
        viewModelScope.launch {
            val allUsers = userRepository.getAllUsers()
            val currentUserId = _state.value.currentUserId
            val friends = _state.value.friends.map { it.user.id }
            
            val results = allUsers.filter {
                it.id != currentUserId && 
                it.id !in friends &&
                (it.name.contains(query, ignoreCase = true) || it.email.contains(query, ignoreCase = true))
            }
            _state.update { it.copy(searchResults = results) }
        }
    }

    fun sendRequest(receiverId: String) {
        val currentUserId = _state.value.currentUserId
        if (currentUserId.isEmpty()) return
        
        viewModelScope.launch {
            friendRepository.sendFriendRequest(currentUserId, receiverId)
            searchUsers(_state.value.searchQuery)
        }
    }

    fun acceptRequest(requesterId: String) {
        val currentUserId = _state.value.currentUserId
        if (currentUserId.isEmpty()) return
        
        viewModelScope.launch {
            friendRepository.acceptFriendRequest(requesterId, currentUserId)
            loadData()
        }
    }

    fun rejectRequest(requesterId: String) {
        val currentUserId = _state.value.currentUserId
        if (currentUserId.isEmpty()) return
        
        viewModelScope.launch {
            friendRepository.rejectFriendRequest(requesterId, currentUserId)
            loadData()
        }
    }

    fun removeFriend(friendId: String) {
        val currentUserId = _state.value.currentUserId
        if (currentUserId.isEmpty()) return
        
        viewModelScope.launch {
            friendRepository.removeFriend(currentUserId, friendId)
            loadData()
            searchUsers(_state.value.searchQuery)
        }
    }
}
