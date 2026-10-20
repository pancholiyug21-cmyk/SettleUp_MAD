package com.example.splitwsie.presentation.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitwsie.data.local.SessionManager
import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.ExpenseCategory
import com.example.splitwsie.domain.model.Split
import com.example.splitwsie.domain.model.SplitType
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.repository.ExpenseRepository
import com.example.splitwsie.domain.repository.GroupRepository
import com.example.splitwsie.domain.repository.UserRepository
import com.example.splitwsie.domain.repository.FileRepository
import com.example.splitwsie.domain.util.Money
import com.example.splitwsie.domain.util.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class AddExpenseUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val members: List<User> = emptyList(),
    val error: String? = null,
    val savedSuccessfully: Boolean = false,
    val deletedSuccessfully: Boolean = false,
    val expenseToEdit: Expense? = null,
    val currentUserId: String = ""
)

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val fileRepository: FileRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(currentUserId = sessionManager.currentUserId.orEmpty()) }
    }

    fun loadMembers(groupId: String?, friendId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                if (groupId != null) {
                    val group = groupRepository.getGroupById(groupId)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            members = group?.members.orEmpty(),
                            error = if (group == null) "This group no longer exists." else it.error
                        )
                    }
                } else if (friendId != null) {
                    val friend = userRepository.getUserById(friendId)
                    val me = userRepository.getCurrentUser()
                    if (friend != null && me != null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                members = listOf(me, friend),
                                error = null
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, error = "User not found.")
                        }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "No context provided.") }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Could not load members: ${e.message}")
                }
            }
        }
    }

    fun loadExpenseForEdit(expenseId: String) {
        viewModelScope.launch {
            try {
                val expense = expenseRepository.getExpenseById(expenseId)
                if (expense == null) {
                    _uiState.update { it.copy(error = "That expense no longer exists.") }
                } else {
                    _uiState.update { it.copy(expenseToEdit = expense) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Could not load the expense: ${e.message}") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    @Suppress("LongParameterList")
    fun addExpense(
        groupId: String?,
        expenseId: String? = null,
        title: String,
        amountText: String,
        category: ExpenseCategory,
        notes: String?,
        splitType: SplitType,
        selectedMemberIds: List<String>,
        exactAmounts: Map<String, Double> = emptyMap(),
        percentages: Map<String, Double> = emptyMap(),
        shares: Map<String, Double> = emptyMap(),
        paidById: String? = null,
        receiptImageUrl: String? = null,
        repeatInterval: com.example.splitwsie.domain.model.RepeatInterval = com.example.splitwsie.domain.model.RepeatInterval.NONE
    ) {
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val currentUserId = sessionManager.currentUserId
                if (currentUserId.isNullOrBlank()) {
                    fail("Your session has expired. Please sign in again.")
                    return@launch
                }

                Validators.validateExpenseTitle(title)?.let { fail(it); return@launch }
                Validators.validateAmount(amountText)?.let { fail(it); return@launch }
                val amount = amountText.toDouble()

                if (selectedMemberIds.isEmpty()) {
                    fail("Select at least one person to split this with.")
                    return@launch
                }

                // Everyone in the split, and the payer, must actually be in the group. Without
                // this a member removed mid-edit would keep a share of the expense that no
                // screen could ever show or settle.
                val memberIds = _uiState.value.members.map { it.id }.toSet()
                if (memberIds.isNotEmpty()) {
                    val unknown = selectedMemberIds.filterNot { it in memberIds }
                    if (unknown.isNotEmpty()) {
                        fail("Someone in this split is no longer a valid member.")
                        return@launch
                    }
                    val payer = paidById ?: currentUserId
                    if (payer !in memberIds) {
                        fail("The person who paid is no longer a valid member.")
                        return@launch
                    }
                }

                val splits = buildSplits(
                    amount = amount,
                    splitType = splitType,
                    memberIds = selectedMemberIds,
                    exactAmounts = exactAmounts,
                    percentages = percentages,
                    shares = shares
                ).getOrElse { error ->
                    fail(error.message ?: "Those split amounts do not add up.")
                    return@launch
                }

                val existing = _uiState.value.expenseToEdit
                val expenseIdToUse = expenseId ?: UUID.randomUUID().toString()

                // Handle Receipt Upload & Replacement
                var finalReceiptUrl: String? = receiptImageUrl?.trim()?.takeIf { it.isNotBlank() }
                val oldReceiptUrl = existing?.receiptImageUrl

                // If user selected a new local image
                if (finalReceiptUrl?.startsWith("content://") == true) {
                    val uploadResult = fileRepository.uploadExpenseReceipt(groupId, expenseIdToUse, finalReceiptUrl)
                    if (uploadResult.isSuccess) {
                        finalReceiptUrl = uploadResult.getOrNull()
                    } else {
                        // If offline/failed, keep the content:// URI. It will not be synced to Firestore
                        // by ExpenseRepositoryImpl but will be available locally.
                        finalReceiptUrl = receiptImageUrl
                    }
                }

                // If the receipt was removed or replaced, delete the old one from Storage
                if (oldReceiptUrl != null && oldReceiptUrl != finalReceiptUrl && oldReceiptUrl.startsWith("http")) {
                    fileRepository.deleteExpenseReceipt(oldReceiptUrl)
                }

                val expense = Expense(
                    id = expenseIdToUse,
                    groupId = groupId,
                    title = title.trim(),
                    amount = Money.round(amount),
                    category = category.name,
                    // Editing keeps the original date so an edited expense does not jump to
                    // the top of the activity feed as if it were new.
                    date = existing?.date ?: Date(),
                    paidById = paidById ?: currentUserId,
                    notes = notes?.trim()?.takeIf { it.isNotBlank() },
                    receiptImageUrl = finalReceiptUrl,
                    splits = splits,
                    repeatInterval = repeatInterval,
                    nextRepeatDate = repeatInterval.calculateNextDate(existing?.date ?: Date())
                )

                expenseRepository.addExpense(expense)
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                fail("Could not save the expense: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        if (_uiState.value.isDeleting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, error = null) }
            try {
                val expense = _uiState.value.expenseToEdit
                if (expense?.id == expenseId && expense.receiptImageUrl?.startsWith("http") == true) {
                    fileRepository.deleteExpenseReceipt(expense.receiptImageUrl)
                }
                expenseRepository.deleteExpense(expenseId)
                _uiState.update { it.copy(isDeleting = false, deletedSuccessfully = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isDeleting = false, error = "Could not delete the expense: ${e.message}")
                }
            }
        }
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(isSaving = false, error = message) }
    }

    /**
     * Builds splits that sum to exactly the expense amount.
     *
     * All arithmetic is in paise. An equal split of ₹100 three ways previously produced three
     * shares of 33.333..., which summed to less than the amount and left a permanent residue
     * in every balance the expense touched.
     */
    internal fun buildSplits(
        amount: Double,
        splitType: SplitType,
        memberIds: List<String>,
        exactAmounts: Map<String, Double>,
        percentages: Map<String, Double>,
        shares: Map<String, Double> = emptyMap()
    ): Result<List<Split>> {
        val totalPaise = Money.toPaise(amount)

        return when (splitType) {
            SplitType.EQUAL -> {
                val shares = Money.splitEqually(totalPaise, memberIds.size)
                Result.success(
                    memberIds.mapIndexed { index, userId ->
                        Split(
                            userId = userId,
                            amountOwed = Money.fromPaise(shares[index]),
                            splitType = SplitType.EQUAL
                        )
                    }
                )
            }

            SplitType.EXACT -> {
                val entered = memberIds.map { Money.toPaise(exactAmounts[it] ?: 0.0) }
                if (entered.any { it < 0 }) {
                    return Result.failure(IllegalArgumentException("Amounts cannot be negative."))
                }
                val sum = entered.sum()
                if (sum != totalPaise) {
                    return Result.failure(
                        IllegalArgumentException(
                            "The split amounts add up to ${Money.format(Money.fromPaise(sum))}, " +
                                "but the expense is ${Money.format(amount)}."
                        )
                    )
                }
                Result.success(
                    memberIds.mapIndexed { index, userId ->
                        Split(
                            userId = userId,
                            amountOwed = Money.fromPaise(entered[index]),
                            splitType = SplitType.EXACT
                        )
                    }
                )
            }

            SplitType.PERCENTAGE -> {
                val entered = memberIds.map { percentages[it] ?: 0.0 }
                if (entered.any { it < 0 }) {
                    return Result.failure(IllegalArgumentException("Percentages cannot be negative."))
                }
                val total = entered.sum()
                if (kotlin.math.abs(total - 100.0) > 0.01) {
                    return Result.failure(
                        IllegalArgumentException(
                            "Percentages add up to %.2f%%, but they must total 100%%.".format(total)
                        )
                    )
                }
                val shares = Money.splitByPercentage(totalPaise, entered)
                Result.success(
                    memberIds.mapIndexed { index, userId ->
                        Split(
                            userId = userId,
                            amountOwed = Money.fromPaise(shares[index]),
                            splitType = SplitType.PERCENTAGE,
                            percentage = entered[index]
                        )
                    }
                )
            }

            SplitType.SHARES -> {
                val entered = memberIds.map { shares[it] ?: 0.0 }
                if (entered.any { it < 0 }) {
                    return Result.failure(IllegalArgumentException("Shares cannot be negative."))
                }
                val total = entered.sum()
                if (total <= 0) {
                    return Result.failure(IllegalArgumentException("Total shares must be greater than zero."))
                }
                val amounts = Money.splitByShares(totalPaise, entered)
                Result.success(
                    memberIds.mapIndexed { index, userId ->
                        Split(
                            userId = userId,
                            amountOwed = Money.fromPaise(amounts[index]),
                            splitType = SplitType.SHARES,
                            shares = entered[index]
                        )
                    }
                )
            }
        }
    }
}
