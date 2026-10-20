package com.example.splitwsie.presentation.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitwsie.data.local.SessionManager
import com.example.splitwsie.domain.model.Debt
import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.Group
import com.example.splitwsie.domain.model.Settlement
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.model.ExpenseFilter
import com.example.splitwsie.domain.repository.ExpenseRepository
import com.example.splitwsie.domain.repository.GroupRepository
import com.example.splitwsie.domain.repository.SettlementRepository
import com.example.splitwsie.domain.repository.UserRepository
import com.example.splitwsie.domain.model.ActivityItem
import com.example.splitwsie.domain.model.toActivityItem
import com.example.splitwsie.domain.usecase.BalanceCalculator
import com.example.splitwsie.domain.util.InviteCodes
import com.example.splitwsie.domain.util.Money
import com.example.splitwsie.domain.util.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class GroupDetailsUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val expenses: List<Expense> = emptyList(),
    val settlements: List<Settlement> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val totalExpenses: Double = 0.0,
    val currentUserBalance: Double = 0.0,
    val currentUserId: String = "",
    val isOwner: Boolean = false,
    val activities: List<ActivityItem> = emptyList(),
    val usersMap: Map<String, User> = emptyMap(),
    val expenseFilter: ExpenseFilter = ExpenseFilter(),
    val error: String? = null
) {
    val members: List<User> get() = group?.members.orEmpty()

    fun memberName(userId: String): String =
        members.firstOrNull { it.id == userId }?.name ?: "Unknown member"
}

sealed class GroupCreationEvent {
    data class Success(val groupId: String) : GroupCreationEvent()
    data class Error(val message: String) : GroupCreationEvent()
}

sealed class GroupDetailsEvent {
    data class Message(val text: String) : GroupDetailsEvent()
    data class Error(val text: String) : GroupDetailsEvent()
    data class ExportSummary(val text: String) : GroupDetailsEvent()
    data object GroupDeleted : GroupDetailsEvent()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    private val settlementRepository: SettlementRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val balanceCalculator: BalanceCalculator
) : ViewModel() {

    private val _detailsState = MutableStateFlow(GroupDetailsUiState())
    val detailsState: StateFlow<GroupDetailsUiState> = _detailsState.asStateFlow()

    private val _creationEvent = MutableSharedFlow<GroupCreationEvent>()
    val creationEvent: SharedFlow<GroupCreationEvent> = _creationEvent.asSharedFlow()

    private val _detailsEvent = MutableSharedFlow<GroupDetailsEvent>()
    val detailsEvent: SharedFlow<GroupDetailsEvent> = _detailsEvent.asSharedFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _expenseFilter = MutableStateFlow(ExpenseFilter())

    private var observingGroupId: String? = null

    fun updateExpenseFilter(filter: ExpenseFilter) {
        _expenseFilter.value = filter
    }

    /**
     * Starts observing a group. Guarded against re-entry so a recomposition that re-runs the
     * LaunchedEffect does not stack up a second collector on the same data.
     */
    fun loadGroupDetails(groupId: String) {
        if (observingGroupId == groupId) return
        observingGroupId = groupId

        viewModelScope.launch {
            val userId = sessionManager.currentUserId.orEmpty()
            _detailsState.update { it.copy(isLoading = true, currentUserId = userId) }

            combine(
                expenseRepository.getExpensesForGroup(groupId),
                settlementRepository.observeForGroup(groupId),
                _expenseFilter
            ) { expenses, settlements, filter ->
                Triple(expenses, settlements, filter)
            }
                .collect { (allExpenses, settlements, filter) ->
                    val group = groupRepository.getGroupById(groupId)
                    if (group == null) {
                        _detailsState.update {
                            it.copy(isLoading = false, error = "This group no longer exists.")
                        }
                        return@collect
                    }

                    val debts = if (group.simplifyDebts) {
                        balanceCalculator.calculateBalances(allExpenses, settlements)
                    } else {
                        balanceCalculator.calculatePairwiseBalances(allExpenses, settlements)
                    }

                    val filteredExpenses = allExpenses.filter { filter.matches(it) }

                    _detailsState.update {
                        it.copy(
                            isLoading = false,
                            group = group,
                            expenses = filteredExpenses,
                            settlements = settlements,
                            debts = debts,
                            totalExpenses = Money.round(allExpenses.sumOf { e -> e.amount }),
                            currentUserBalance = balanceCalculator.netBalanceFor(
                                allExpenses, settlements, userId
                            ),
                            isOwner = group.createdByUserId == userId,
                            activities = (allExpenses.map { it.toActivityItem() } + settlements.map { it.toActivityItem() })
                                .sortedByDescending { it.timestamp },
                            usersMap = userRepository.getAllUsers().associateBy { u -> u.id },
                            expenseFilter = filter,
                            error = null
                        )
                    }
                }
        }
    }

    fun createGroup(name: String, description: String, category: String) {
        viewModelScope.launch {
            val nameError = Validators.validateGroupName(name)
            if (nameError != null) {
                _creationEvent.emit(GroupCreationEvent.Error(nameError))
                return@launch
            }

            _isCreating.value = true
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser == null) {
                    _creationEvent.emit(
                        GroupCreationEvent.Error("Your session has expired. Please sign in again.")
                    )
                    return@launch
                }

                val groupId = UUID.randomUUID().toString()
                val newGroup = Group(
                    id = groupId,
                    name = name.trim(),
                    description = description.trim().takeIf { it.isNotBlank() },
                    members = listOf(currentUser),
                    // Generated up front so the group is shareable the moment it exists.
                    inviteCode = generateUniqueInviteCode(),
                    createdByUserId = currentUser.id,
                    category = category,
                    iconUrl = null
                )

                groupRepository.createGroup(newGroup)
                _creationEvent.emit(GroupCreationEvent.Success(groupId))
            } catch (e: Exception) {
                _creationEvent.emit(
                    GroupCreationEvent.Error("Could not create the group: ${e.message ?: "unknown error"}")
                )
            } finally {
                _isCreating.value = false
            }
        }
    }

    fun updateGroup(groupId: String, name: String, description: String, category: String, simplifyDebts: Boolean) {
        viewModelScope.launch {
            val nameError = Validators.validateGroupName(name)
            if (nameError != null) {
                _detailsEvent.emit(GroupDetailsEvent.Error(nameError))
                return@launch
            }
            try {
                val group = groupRepository.getGroupById(groupId) ?: return@launch
                if (group.createdByUserId != sessionManager.currentUserId) {
                    _detailsEvent.emit(GroupDetailsEvent.Error("Only the group creator can edit settings."))
                    return@launch
                }

                val updatedGroup = group.copy(
                    name = name.trim(),
                    description = description.trim().takeIf { it.isNotBlank() },
                    category = category,
                    simplifyDebts = simplifyDebts
                )
                groupRepository.updateGroup(updatedGroup)
                refreshGroup(groupId)
                _detailsEvent.emit(GroupDetailsEvent.Message("Group settings updated"))
            } catch (e: Exception) {
                _detailsEvent.emit(
                    GroupDetailsEvent.Error("Could not update the group: ${e.message ?: "unknown error"}")
                )
            }
        }
    }

    private suspend fun generateUniqueInviteCode(): String {
        repeat(10) {
            val code = InviteCodes.generate()
            if (groupRepository.getGroupByInviteCode(code) == null) return code
        }
        // Astronomically unlikely; fall back to something guaranteed unique rather than
        // handing out a code that already belongs to another group.
        return InviteCodes.fallback()
    }

    /**
     * Adds a member to the group.
     *
     * Reuses an existing profile when the email already exists on the device, so the same
     * person in two groups is one user with one balance instead of two duplicates.
     */
    fun addNewMember(groupId: String, name: String, email: String) {
        viewModelScope.launch {
            val nameError = Validators.validateName(name)
            val emailError = Validators.validateEmail(email)
            if (nameError != null || emailError != null) {
                _detailsEvent.emit(GroupDetailsEvent.Error(nameError ?: emailError.orEmpty()))
                return@launch
            }

            try {
                val normalisedEmail = email.trim().lowercase()
                val existing = userRepository.findByEmail(normalisedEmail)

                val userId = if (existing != null) {
                    if (groupRepository.isMember(groupId, existing.id)) {
                        _detailsEvent.emit(
                            GroupDetailsEvent.Error("${existing.name} is already in this group.")
                        )
                        return@launch
                    }
                    existing.id
                } else {
                    val newUser = User(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        email = normalisedEmail
                    )
                    userRepository.createUser(newUser)
                    newUser.id
                }

                groupRepository.addMemberToGroup(groupId, userId)
                refreshGroup(groupId)
                _detailsEvent.emit(GroupDetailsEvent.Message("Added ${existing?.name ?: name.trim()}"))
            } catch (e: Exception) {
                _detailsEvent.emit(
                    GroupDetailsEvent.Error("Could not add that member: ${e.message ?: "unknown error"}")
                )
            }
        }
    }

    /**
     * Removes a member.
     *
     * Refused while they still have a non-zero balance: removing them would delete the only
     * record of who owes whom while their splits stay attached to existing expenses.
     */
    fun removeMember(groupId: String, userId: String) {
        viewModelScope.launch {
            try {
                val state = _detailsState.value
                if (userId == state.currentUserId) {
                    _detailsEvent.emit(
                        GroupDetailsEvent.Error("Use \"Leave group\" to remove yourself.")
                    )
                    return@launch
                }
                if (!state.isOwner) {
                    _detailsEvent.emit(
                        GroupDetailsEvent.Error("Only the person who created this group can remove members.")
                    )
                    return@launch
                }

                val balance = balanceCalculator.netBalanceFor(
                    state.expenses, state.settlements, userId
                )
                if (!Money.isZero(balance)) {
                    _detailsEvent.emit(
                        GroupDetailsEvent.Error(
                            "${state.memberName(userId)} still has a balance of " +
                                "${Money.format(kotlin.math.abs(balance))}. Settle up first."
                        )
                    )
                    return@launch
                }

                groupRepository.removeMemberFromGroup(groupId, userId)
                refreshGroup(groupId)
                _detailsEvent.emit(GroupDetailsEvent.Message("Removed ${state.memberName(userId)}"))
            } catch (e: Exception) {
                _detailsEvent.emit(
                    GroupDetailsEvent.Error("Could not remove that member: ${e.message ?: "unknown error"}")
                )
            }
        }
    }

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            try {
                val state = _detailsState.value
                val me = state.currentUserId
                val balance = balanceCalculator.netBalanceFor(state.expenses, state.settlements, me)
                if (!Money.isZero(balance)) {
                    _detailsEvent.emit(
                        GroupDetailsEvent.Error(
                            "You still have a balance of ${Money.format(kotlin.math.abs(balance))} " +
                                "in this group. Settle up before leaving."
                        )
                    )
                    return@launch
                }
                groupRepository.removeMemberFromGroup(groupId, me)
                _detailsEvent.emit(GroupDetailsEvent.GroupDeleted)
            } catch (e: Exception) {
                _detailsEvent.emit(
                    GroupDetailsEvent.Error("Could not leave the group: ${e.message ?: "unknown error"}")
                )
            }
        }
    }

    /**
     * Deletes the group and all of its expenses, splits and settlements.
     *
     * Restricted to the creator, and refused while anyone still owes anyone -- deleting then
     * would erase debts that people still expect to be paid.
     */
    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            try {
                val state = _detailsState.value
                if (!state.isOwner) {
                    _detailsEvent.emit(
                        GroupDetailsEvent.Error("Only the person who created this group can delete it.")
                    )
                    return@launch
                }
                if (state.debts.isNotEmpty()) {
                    _detailsEvent.emit(
                        GroupDetailsEvent.Error(
                            "There are still unsettled balances in this group. Settle up before deleting."
                        )
                    )
                    return@launch
                }
                groupRepository.deleteGroup(groupId)
                observingGroupId = null
                _detailsEvent.emit(GroupDetailsEvent.GroupDeleted)
            } catch (e: Exception) {
                _detailsEvent.emit(
                    GroupDetailsEvent.Error("Could not delete the group: ${e.message ?: "unknown error"}")
                )
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            try {
                expenseRepository.deleteExpense(expenseId)
                _detailsEvent.emit(GroupDetailsEvent.Message("Expense deleted"))
            } catch (e: Exception) {
                _detailsEvent.emit(
                    GroupDetailsEvent.Error("Could not delete the expense: ${e.message ?: "unknown error"}")
                )
            }
        }
    }

    /** Membership changes are not part of the expense/settlement flows, so refresh explicitly. */
    private suspend fun refreshGroup(groupId: String) {
        val updated = groupRepository.getGroupById(groupId)
        _detailsState.update { it.copy(group = updated) }
    }

    fun exportGroupSummary() {
        viewModelScope.launch {
            val state = _detailsState.value
            if (state.group == null) return@launch

            val sb = StringBuilder()
            sb.append("📊 Summary for Group: ${state.group.name.uppercase()}\n")
            sb.append("Total Expenses: ${Money.format(state.totalExpenses)}\n")
            sb.append("------------------------\n\n")

            sb.append("💰 Current Balances:\n")
            if (state.debts.isEmpty()) {
                sb.append("All settled up!\n")
            } else {
                state.debts.forEach { debt ->
                    val from = state.memberName(debt.fromUserId)
                    val to = state.memberName(debt.toUserId)
                    sb.append("- $from owes $to ${Money.format(debt.amount)}\n")
                }
            }
            sb.append("\n------------------------\n\n")

            sb.append("🧾 Recent Expenses:\n")
            if (state.expenses.isEmpty()) {
                sb.append("No expenses yet.\n")
            } else {
                val recent = state.expenses.sortedByDescending { it.date }.take(20)
                recent.forEach { exp ->
                    val paidBy = state.memberName(exp.paidById)
                    val dateStr = android.text.format.DateFormat.format("MMM dd", exp.date).toString()
                    sb.append("- $dateStr: ${exp.title} (${Money.format(exp.amount)}) paid by $paidBy\n")
                }
            }

            _detailsEvent.emit(GroupDetailsEvent.ExportSummary(sb.toString()))
        }
    }
}
