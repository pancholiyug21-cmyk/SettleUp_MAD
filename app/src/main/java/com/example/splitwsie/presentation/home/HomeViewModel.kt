package com.example.splitwsie.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitwsie.data.local.SessionManager
import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.Group
import com.example.splitwsie.domain.model.Settlement
import com.example.splitwsie.domain.repository.ExpenseRepository
import com.example.splitwsie.domain.repository.GroupRepository
import com.example.splitwsie.domain.repository.SettlementRepository
import com.example.splitwsie.domain.repository.UserRepository
import com.example.splitwsie.domain.model.ActivityItem
import com.example.splitwsie.domain.model.toActivityItem
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.usecase.BalanceCalculator
import com.example.splitwsie.domain.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One group with the signed-in user's position in it, so the list can show a per-group balance. */
data class GroupSummary(
    val group: Group,
    val myBalance: Double
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val groups: List<GroupSummary> = emptyList(),
    val recentActivities: List<ActivityItem> = emptyList(),
    val usersMap: Map<String, User> = emptyMap(),
    val youOwe: Double = 0.0,
    val youAreOwed: Double = 0.0,
    val pendingPaymentCount: Int = 0,
    val currentUserId: String = "",
    val userName: String = "",
    val error: String? = null
) {
    val netBalance: Double get() = youAreOwed - youOwe
}

sealed class HomeEvent {
    data class Error(val message: String) : HomeEvent()
    data class Info(val message: String) : HomeEvent()
    data class GroupJoined(val groupName: String) : HomeEvent()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    private val settlementRepository: SettlementRepository,
    private val userRepository: UserRepository,
    private val balanceCalculator: BalanceCalculator,
    private val sessionManager: SessionManager,
    private val recurrenceEvaluator: com.example.splitwsie.domain.usecase.RecurrenceEvaluator
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    init {
        observeHome()
    }

    /**
     * A single long-lived collection driven by the database.
     *
     * The previous implementation launched a fresh collector on every refresh() without
     * cancelling the old one, so each refresh added another permanent collector writing to the
     * same state -- a leak that also produced flickering, out-of-order updates.
     */
    private fun observeHome() {
        viewModelScope.launch {
            val currentUser = runCatching { userRepository.getCurrentUser() }.getOrNull()
            val userId = currentUser?.id.orEmpty()

            if (userId.isBlank()) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Your session has expired. Please sign in again.")
                }
                return@launch
            }

            // Evaluate recurrences before loading state
            runCatching { recurrenceEvaluator.evaluate() }

            _uiState.update { it.copy(currentUserId = userId, userName = currentUser?.name.orEmpty()) }

            combine(
                groupRepository.getGroupsForUser(userId),
                settlementRepository.observeForUser(userId),
                expenseRepository.getAllExpensesForUser(userId),
                _searchQuery
            ) { groups, settlements, allExpenses, query ->
                val allUsers = userRepository.getAllUsers().associateBy { it.id }
                val mappedActivities = (allExpenses.map { it.toActivityItem() } + settlements.map { it.toActivityItem() })
                    .sortedByDescending { it.timestamp }
                    .take(20)
                buildState(userId, groups, settlements, allExpenses, mappedActivities, allUsers, query)
            }
                .collect { computed ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            searchQuery = computed.query,
                            groups = computed.groups,
                            recentActivities = computed.recentActivities,
                            usersMap = computed.usersMap,
                            youOwe = computed.youOwe,
                            youAreOwed = computed.youAreOwed,
                            pendingPaymentCount = computed.pendingCount,
                            error = null
                        )
                    }
                }
        }
    }

    private data class ComputedHome(
        val query: String,
        val groups: List<GroupSummary>,
        val recentActivities: List<ActivityItem>,
        val usersMap: Map<String, User>,
        val youOwe: Double,
        val youAreOwed: Double,
        val pendingCount: Int
    )

    private suspend fun buildState(
        userId: String,
        groups: List<Group>,
        settlements: List<Settlement>,
        allExpenses: List<Expense>,
        activities: List<ActivityItem>,
        usersMap: Map<String, User>,
        query: String
    ): ComputedHome {
        val filteredGroups = if (query.isNotBlank()) {
            groups.filter { it.name.contains(query, ignoreCase = true) || it.description?.contains(query, ignoreCase = true) == true }
        } else groups

        val filteredActivities = if (query.isNotBlank()) {
            activities.filter {
                it.title?.contains(query, ignoreCase = true) == true ||
                usersMap[it.userId]?.name?.contains(query, ignoreCase = true) == true
            }
        } else activities

        val summaries = mutableListOf<GroupSummary>()

        for (group in filteredGroups) {
            val groupExpenses = allExpenses.filter { it.groupId == group.id }
            val groupSettlements = settlements.filter { it.groupId == group.id }

            summaries += GroupSummary(
                group = group.copy(totalExpenses = groupExpenses.sumOf { it.amount }),
                myBalance = balanceCalculator.netBalanceFor(groupExpenses, groupSettlements, userId)
            )
        }

        // Global pairwise balance computation
        val globalDebts = balanceCalculator.calculatePairwiseBalances(allExpenses, settlements)
        val owe = globalDebts.filter { it.fromUserId == userId }.sumOf { it.amount }
        val owed = globalDebts.filter { it.toUserId == userId }.sumOf { it.amount }

        return ComputedHome(
            query = query,
            groups = summaries,
            recentActivities = filteredActivities,
            usersMap = usersMap,
            youOwe = Money.round(owe),
            youAreOwed = Money.round(owed),
            pendingCount = settlements.count {
                it.status.isInFlight && (it.fromUserId == userId || it.toUserId == userId)
            }
        )
    }

    /**
     * Joins a group by its shared invite code.
     *
     * Accepts the invite code, falling back to a raw group id so codes shared before invite
     * codes existed still work.
     */
    fun joinGroup(code: String) {
        viewModelScope.launch {
            val trimmed = code.trim()
            if (trimmed.isBlank()) {
                _events.emit(HomeEvent.Error("Enter a group code"))
                return@launch
            }
            try {
                val userId = sessionManager.currentUserId
                if (userId.isNullOrBlank()) {
                    _events.emit(HomeEvent.Error("Your session has expired. Please sign in again."))
                    return@launch
                }

                val group = groupRepository.getGroupByInviteCode(trimmed)
                    ?: groupRepository.getGroupById(trimmed)

                if (group == null) {
                    _events.emit(HomeEvent.Error("No group found for code \"$trimmed\""))
                    return@launch
                }

                if (groupRepository.isMember(group.id, userId)) {
                    _events.emit(HomeEvent.Info("You are already in ${group.name}"))
                    return@launch
                }

                groupRepository.addMemberToGroup(group.id, userId)
                _events.emit(HomeEvent.GroupJoined(group.name))
            } catch (e: Exception) {
                _events.emit(HomeEvent.Error("Could not join that group: ${e.message ?: "unknown error"}"))
            }
        }
    }
}
