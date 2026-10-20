package com.example.splitwsie.presentation.settle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitwsie.data.local.SessionManager
import com.example.splitwsie.domain.model.Group
import com.example.splitwsie.domain.model.Settlement
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.repository.ExpenseRepository
import com.example.splitwsie.domain.repository.GroupRepository
import com.example.splitwsie.domain.repository.SettlementRepository
import com.example.splitwsie.domain.repository.UserRepository
import com.example.splitwsie.domain.usecase.BalanceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One debt the signed-in user owes, resolved to display names. */
data class PayableDebt(
    val groupId: String,
    val groupName: String,
    val toUserId: String,
    val toUserName: String,
    val toUserUpiId: String?,
    val amount: Double
)

data class SettleUpUiState(
    val isLoading: Boolean = true,
    val debts: List<PayableDebt> = emptyList(),
    val totalOwed: Double = 0.0,
    val error: String? = null
)

data class SettlementHistoryItem(
    val settlement: Settlement,
    val counterpartyName: String,
    val groupName: String,
    /** True when the signed-in user was the payer. */
    val outgoing: Boolean
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val items: List<SettlementHistoryItem> = emptyList(),
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettleUpViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    private val settlementRepository: SettlementRepository,
    private val userRepository: UserRepository,
    private val balanceCalculator: BalanceCalculator,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettleUpUiState())
    val uiState: StateFlow<SettleUpUiState> = _uiState.asStateFlow()

    private val _historyState = MutableStateFlow(HistoryUiState())
    val historyState: StateFlow<HistoryUiState> = _historyState.asStateFlow()

    init {
        observeDebts()
        observeHistory()
    }

    /**
     * Recomputed from a single combined flow rather than a manual refresh, so adding an
     * expense or completing a payment updates this list without anyone remembering to call
     * refresh().
     */
    private fun observeDebts() {
        viewModelScope.launch {
            val me = sessionManager.currentUserId.orEmpty()
            if (me.isBlank()) {
                _uiState.update { it.copy(isLoading = false, error = "Please sign in again.") }
                return@launch
            }

            combine(
                groupRepository.getAllGroups(),
                settlementRepository.observeAll()
            ) { groups, settlements -> groups to settlements }
                .flatMapLatest { (groups, settlements) ->
                    kotlinx.coroutines.flow.flow {
                        val payable = mutableListOf<PayableDebt>()
                        for (group in groups) {
                            val expenses = expenseRepository.getExpensesForGroup(group.id).first()
                            val groupSettlements = settlements.filter { it.groupId == group.id }
                            val debts = balanceCalculator.calculateBalances(expenses, groupSettlements)
                            debts.filter { it.fromUserId == me }.forEach { debt ->
                                val payee = resolveUser(group, debt.toUserId)
                                payable += PayableDebt(
                                    groupId = group.id,
                                    groupName = group.name,
                                    toUserId = debt.toUserId,
                                    toUserName = payee?.name ?: "Unknown member",
                                    toUserUpiId = payee?.upiId,
                                    amount = debt.amount
                                )
                            }
                        }
                        emit(payable.sortedByDescending { it.amount })
                    }
                }
                .collect { payable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            debts = payable,
                            totalOwed = payable.sumOf { debt -> debt.amount },
                            error = null
                        )
                    }
                }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            val me = sessionManager.currentUserId.orEmpty()
            if (me.isBlank()) {
                _historyState.update { it.copy(isLoading = false, error = "Please sign in again.") }
                return@launch
            }

            combine(
                settlementRepository.observeForUser(me),
                groupRepository.getAllGroups()
            ) { settlements, groups ->
                val groupsById = groups.associateBy { it.id }
                settlements.map { settlement ->
                    val outgoing = settlement.fromUserId == me
                    val counterpartyId = if (outgoing) settlement.toUserId else settlement.fromUserId
                    val group = groupsById[settlement.groupId]
                    SettlementHistoryItem(
                        settlement = settlement,
                        counterpartyName = group?.members
                            ?.firstOrNull { it.id == counterpartyId }?.name
                            ?: "Unknown member",
                        groupName = group?.name ?: "Deleted group",
                        outgoing = outgoing
                    )
                }
            }.collect { items ->
                _historyState.update { it.copy(isLoading = false, items = items, error = null) }
            }
        }
    }

    /**
     * Falls back to a direct lookup when the payee has been removed from the group but the
     * debt survives, so the row shows a name rather than a raw id.
     */
    private suspend fun resolveUser(group: Group, userId: String): User? =
        group.members.firstOrNull { it.id == userId } ?: userRepository.getUserById(userId)
}
