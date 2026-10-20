package com.example.splitwsie.presentation.payment

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitwsie.data.local.SessionManager
import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.Group
import com.example.splitwsie.domain.model.PaymentMethod
import com.example.splitwsie.domain.model.Settlement
import com.example.splitwsie.domain.model.SettlementStatus
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.payment.PaymentResult
import com.example.splitwsie.domain.payment.PaymentVerifier
import com.example.splitwsie.domain.payment.RazorpayCheckoutManager
import com.example.splitwsie.domain.payment.UpiPayment
import com.example.splitwsie.domain.repository.ExpenseRepository
import com.example.splitwsie.domain.repository.GroupRepository
import com.example.splitwsie.domain.repository.SettlementRepository
import com.example.splitwsie.domain.repository.UserRepository
import com.example.splitwsie.domain.usecase.BalanceCalculator
import com.example.splitwsie.domain.usecase.SettlementCheck
import com.example.splitwsie.domain.usecase.SettlementEngine
import com.example.splitwsie.domain.util.Money
import com.example.splitwsie.di.SandboxPaymentsEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Where the payment flow currently is. Drives which screen state is shown. */
enum class PaymentPhase {
    LOADING,

    /** Reviewing the amount and method, nothing initiated yet. */
    REVIEW,

    /** Settlement row written; waiting for the external payment app to come back. */
    AWAITING_PROVIDER,

    /** Cloud function is creating a Razorpay order. */
    CREATING_ORDER,

    /** Razorpay checkout is opened. */
    CHECKOUT_OPENED,

    /** Client reported success, waiting for backend signature validation. */
    VERIFYING,

    /** Confirmed. Balance has moved. */
    SUCCESS,

    /** Submitted to the bank; outcome unknown, balance deliberately unchanged. */
    PENDING,

    FAILED,

    /** Nothing to pay, or the payment is not allowed. */
    BLOCKED
}

data class PaymentUiState(
    val phase: PaymentPhase = PaymentPhase.LOADING,
    val payee: User? = null,
    val payeeName: String = "",
    val group: Group? = null,
    /** Full amount currently owed. The user may choose to pay less than this. */
    val outstanding: Double = 0.0,
    /** What the user has typed / chosen to pay. */
    val amountInput: String = "",
    val selectedMethod: PaymentMethod = PaymentMethod.UPI_INTENT,
    val availableMethods: List<PaymentMethod> = emptyList(),
    /** Expenses that make up this debt, shown so the user can see what they are paying for. */
    val relatedExpenses: List<Expense> = emptyList(),
    val manualReference: String = "",
    val settlement: Settlement? = null,
    /** An earlier payment left unresolved by a crash or a lost callback. */
    val unresolvedSettlement: Settlement? = null,
    val statusNote: String? = null,
    val error: String? = null,
    val isSubmitting: Boolean = false,
    /** True when the payment rail confirmed only on this device, not against a server. */
    val isDeviceReportedOnly: Boolean = true
) {
    val amount: Double get() = amountInput.toDoubleOrNull() ?: 0.0

    val payeeVpa: String? get() = payee?.upiId?.takeIf { UpiPayment.isValidVpa(it) }

    val canUseUpi: Boolean get() = payeeVpa != null

    val isAmountValid: Boolean
        get() = amount > 0 &&
            !Money.isZero(amount) &&
            Money.toPaise(amount) <= Money.toPaise(outstanding) + 1
}

sealed class PaymentEffect {
    /** The screen must launch this with an activity-result contract. */
    data class LaunchUpiApp(val intent: Intent, val settlementId: String) : PaymentEffect()

    data class LaunchRazorpay(
        val orderId: String,
        val keyId: String,
        val amount: Long,
        val currency: String,
        val settlementId: String
    ) : PaymentEffect()

    data class Message(val text: String) : PaymentEffect()
    data object Settled : PaymentEffect()
}

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val settlementRepository: SettlementRepository,
    private val balanceCalculator: BalanceCalculator,
    private val settlementEngine: SettlementEngine,
    private val paymentVerifier: PaymentVerifier,
    private val sessionManager: SessionManager,
    private val razorpayCheckoutManager: RazorpayCheckoutManager,
    @SandboxPaymentsEnabled private val sandboxEnabled: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<PaymentEffect>()
    val effects: SharedFlow<PaymentEffect> = _effects.asSharedFlow()

    private var groupId: String = ""
    private var toUserId: String = ""
    private var expenseId: String? = null

    /**
     * Distinguishes one payment attempt from the next. Held in the ViewModel so a
     * configuration change reuses the same attempt (and therefore the same settlement row),
     * while an explicit retry after a failure starts a fresh one.
     */
    private var attemptToken: String = UUID.randomUUID().toString()

    private val currentUserId: String get() = sessionManager.currentUserId.orEmpty()

    fun load(groupId: String, toUserId: String, expenseId: String?) {
        this.groupId = groupId
        this.toUserId = toUserId
        this.expenseId = expenseId
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = PaymentPhase.LOADING, error = null) }
            try {
                val me = currentUserId
                if (me.isBlank()) {
                    _uiState.update {
                        it.copy(phase = PaymentPhase.BLOCKED, error = "Your session has expired. Please sign in again.")
                    }
                    return@launch
                }

                val group = groupRepository.getGroupById(groupId)
                if (group == null) {
                    _uiState.update {
                        it.copy(phase = PaymentPhase.BLOCKED, error = "This group no longer exists.")
                    }
                    return@launch
                }

                val payee = userRepository.getUserById(toUserId)
                if (payee == null) {
                    _uiState.update {
                        it.copy(phase = PaymentPhase.BLOCKED, error = "That person could not be found.")
                    }
                    return@launch
                }

                val expenses = expenseRepository.getExpensesForGroup(groupId).first()
                val settlements = settlementRepository.getForGroup(groupId)

                // The amount always comes from the live balance, never from the caller. That
                // removes any way for a stale screen or a crafted deep link to settle an
                // amount that does not match what is actually owed.
                val outstanding = balanceCalculator.outstandingBetween(
                    expenses = expenses,
                    settlements = settlements,
                    userId = me,
                    otherUserId = toUserId
                )

                val unresolved = settlements.firstOrNull {
                    it.status.isInFlight && it.fromUserId == me && it.toUserId == toUserId
                }

                val related = expenses.filter { expense ->
                    expense.paidById == toUserId && expense.splits.any { it.userId == me && it.amountOwed > 0 }
                }

                val methods = buildList {
                    add(PaymentMethod.RAZORPAY)
                    add(PaymentMethod.UPI_INTENT)
                    add(PaymentMethod.CASH)
                    add(PaymentMethod.BANK_TRANSFER)
                    if (sandboxEnabled) add(PaymentMethod.SANDBOX)
                }

                val phase = when {
                    unresolved != null -> PaymentPhase.REVIEW
                    Money.isZero(outstanding) -> PaymentPhase.BLOCKED
                    else -> PaymentPhase.REVIEW
                }

                _uiState.update {
                    it.copy(
                        phase = phase,
                        payee = payee,
                        payeeName = payee.name,
                        group = group,
                        outstanding = outstanding,
                        amountInput = if (Money.isZero(outstanding)) "" else "%.2f".format(outstanding),
                        availableMethods = methods,
                        selectedMethod = PaymentMethod.RAZORPAY,
                        relatedExpenses = related,
                        unresolvedSettlement = unresolved,
                        isDeviceReportedOnly = !paymentVerifier.isServerVerified,
                        error = if (Money.isZero(outstanding)) {
                            "You are all settled up with ${payee.name}."
                        } else {
                            null
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        phase = PaymentPhase.BLOCKED,
                        error = "Could not load this payment: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun onAmountChanged(value: String) {
        // Single decimal point, at most two decimals -- rejected at input rather than
        // silently rounded later.
        val cleaned = value.filter { it.isDigit() || it == '.' }
        val parts = cleaned.split('.')
        val normalised = when {
            parts.size <= 1 -> cleaned
            else -> parts[0] + "." + parts.drop(1).joinToString("").take(2)
        }
        _uiState.update { it.copy(amountInput = normalised, error = null) }
    }

    fun onMethodSelected(method: PaymentMethod) {
        _uiState.update { it.copy(selectedMethod = method, error = null) }
    }

    fun onManualReferenceChanged(value: String) {
        _uiState.update { it.copy(manualReference = value.take(40)) }
    }

    fun payFullAmount() {
        _uiState.update { it.copy(amountInput = "%.2f".format(it.outstanding), error = null) }
    }

    /**
     * Validates and creates the settlement row, then either hands off to the payment app or
     * records a manual payment.
     */
    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                val me = currentUserId
                val expenses = expenseRepository.getExpensesForGroup(groupId).first()
                val settlements = settlementRepository.getForGroup(groupId)

                when (
                    val check = settlementEngine.check(
                        group = state.group,
                        expenses = expenses,
                        settlements = settlements,
                        actingUserId = me,
                        fromUserId = me,
                        toUserId = toUserId,
                        amount = state.amount
                    )
                ) {
                    is SettlementCheck.Rejected -> {
                        _uiState.update {
                            it.copy(isSubmitting = false, error = check.reason.message)
                        }
                        return@launch
                    }

                    is SettlementCheck.Allowed -> {
                        val amount = check.amount
                        val key = settlementEngine.idempotencyKey(
                            groupId = groupId,
                            fromUserId = me,
                            toUserId = toUserId,
                            amount = amount,
                            attemptToken = attemptToken
                        )

                        val settlement = settlementRepository.initiate(
                            groupId = groupId,
                            fromUserId = me,
                            toUserId = toUserId,
                            amount = amount,
                            method = state.selectedMethod,
                            idempotencyKey = key,
                            providerRefId = null,
                            expenseId = expenseId,
                            note = state.manualReference.takeIf { it.isNotBlank() }
                        )

                        // The same key already resolved -- this is a duplicate submit.
                        if (settlement.status.isTerminal) {
                            _uiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    settlement = settlement,
                                    phase = if (settlement.status == SettlementStatus.VERIFIED) {
                                        PaymentPhase.SUCCESS
                                    } else {
                                        PaymentPhase.FAILED
                                    },
                                    statusNote = "This payment was already recorded."
                                )
                            }
                            return@launch
                        }

                        when (state.selectedMethod) {
                            PaymentMethod.RAZORPAY -> launchRazorpayCheckout(settlement, state)
                            PaymentMethod.UPI_INTENT -> launchUpi(settlement, state)
                            PaymentMethod.CASH, PaymentMethod.BANK_TRANSFER ->
                                recordManual(settlement, state.selectedMethod, state.manualReference)
                            PaymentMethod.SANDBOX -> completeSandbox(settlement)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        error = "Could not start this payment: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    private suspend fun launchRazorpayCheckout(settlement: Settlement, state: PaymentUiState) {
        _uiState.update { it.copy(phase = PaymentPhase.CREATING_ORDER) }

        val amountPaise = Money.toPaise(settlement.amount)
        val result = razorpayCheckoutManager.createPaymentOrder(
            amountPaise = amountPaise,
            settlementId = settlement.id,
            receiverId = toUserId,
            groupId = groupId
        )

        if (result.success && result.orderId != null && result.keyId != null) {
            val updatedSettlement = settlementRepository.updateStatus(
                settlementId = settlement.id,
                status = SettlementStatus.ORDER_CREATED,
                razorpayOrderId = result.orderId,
                paymentId = result.paymentId
            )

            _uiState.update {
                it.copy(
                    phase = PaymentPhase.CHECKOUT_OPENED,
                    settlement = updatedSettlement ?: settlement
                )
            }

            _effects.emit(
                PaymentEffect.LaunchRazorpay(
                    orderId = result.orderId,
                    keyId = result.keyId,
                    amount = result.amount ?: amountPaise,
                    currency = result.currency ?: "INR",
                    settlementId = settlement.id
                )
            )
        } else {
            settlementRepository.updateStatus(
                settlementId = settlement.id,
                status = SettlementStatus.FAILED,
                verificationNotes = "Failed to create Razorpay order: ${result.errorMessage}"
            )
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    phase = PaymentPhase.FAILED,
                    error = result.errorMessage ?: "Failed to generate secure payment order"
                )
            }
        }
    }

    private suspend fun launchUpi(settlement: Settlement, state: PaymentUiState) {
        val vpa = state.payeeVpa
        if (vpa == null) {
            // Void the row we just created so it does not sit in the list as in-flight.
            settlementRepository.updateStatus(
                settlementId = settlement.id,
                status = SettlementStatus.CANCELLED,
                verificationNotes = "Payee has no valid UPI ID."
            )
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    error = "${state.payeeName} has not added a UPI ID. Ask them to add one, " +
                        "or record the payment as cash or a bank transfer."
                )
            }
            return
        }

        val ref = settlementEngine.transactionRef(settlement.id)
        val intent = UpiPayment.buildIntent(
            payeeVpa = vpa,
            payeeName = state.payeeName,
            amount = settlement.amount,
            transactionRef = ref,
            note = "Settle: ${state.group?.name.orEmpty().take(20)}"
        )

        _uiState.update {
            it.copy(
                isSubmitting = false,
                phase = PaymentPhase.AWAITING_PROVIDER,
                settlement = settlement.copy(providerRefId = ref)
            )
        }
        _effects.emit(PaymentEffect.LaunchUpiApp(intent, settlement.id))
    }

    private suspend fun recordManual(
        settlement: Settlement,
        method: PaymentMethod,
        reference: String
    ) {
        val resolved = settlementEngine.resolveManual(method, reference)

        // 1. Call the backend to record the settlement authoritatively
        val result = settlementRepository.recordManualSettlement(
            settlementId = settlement.id,
            groupId = settlement.groupId,
            toUserId = settlement.toUserId,
            amount = settlement.amount,
            method = method,
            providerTxnId = resolved.providerTxnId,
            note = resolved.notes
        )

        if (result.isSuccess) {
            // Wait for FirestoreSnapshotListener to sync it down to Room,
            // or we can optimistically update it here if we want immediate UI feedback.
            // Since the user is online and it succeeded, we update it locally so the UI updates instantly.
            val updated = settlementRepository.updateStatus(
                settlementId = settlement.id,
                status = resolved.status,
                providerTxnId = resolved.providerTxnId,
                verificationNotes = resolved.notes
            )
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    phase = PaymentPhase.SUCCESS,
                    settlement = updated,
                    statusNote = resolved.notes
                )
            }
            _effects.emit(PaymentEffect.Settled)
        } else {
            // Failed to record on backend (e.g., offline or validation failed)
            val errorMsg = result.exceptionOrNull()?.message ?: "Network error. Try again when online."

            // Mark the local initiated row as failed or cancelled so it doesn't stay in flight
            settlementRepository.updateStatus(
                settlementId = settlement.id,
                status = SettlementStatus.FAILED,
                verificationNotes = "Failed to sync manual settlement: $errorMsg"
            )

            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    phase = PaymentPhase.FAILED,
                    error = errorMsg
                )
            }
        }
    }

    private suspend fun completeSandbox(settlement: Settlement) {
        val result = PaymentResult.Success(
            providerTxnId = "SANDBOX-" + settlement.id.take(8).uppercase(),
            providerRefId = settlementEngine.transactionRef(settlement.id)
        )
        applyResult(settlement.id, result, extraNote = "Sandbox payment. No money was transferred.")
    }

    /** Called by the screen with whatever the UPI app returned. */
    fun onUpiResult(settlementId: String, resultOk: Boolean, response: String?) {
        viewModelScope.launch {
            val parsed = UpiPayment.parseResponse(response)
            val result = when {
                parsed.isSuccess -> PaymentResult.Success(parsed.txnId, parsed.txnRef)
                parsed.isSubmitted -> PaymentResult.Submitted(parsed.txnId, parsed.txnRef)
                parsed.isFailure -> PaymentResult.Failed(
                    reason = parsed.responseCode?.let { "declined (code $it)" } ?: "declined by the bank",
                    providerTxnId = parsed.txnId
                )
                // No response payload and the activity was cancelled: the user backed out.
                parsed.isUnknown && !resultOk -> PaymentResult.Cancelled
                else -> PaymentResult.Unknown(parsed.raw)
            }
            applyResult(settlementId, result)
        }
    }

    /** Called by the screen with Razorpay checkout result. */
    fun onRazorpayResult(settlementId: String, success: Boolean, razorpayPaymentId: String?, signature: String?, errorMsg: String?) {
        viewModelScope.launch {
            if (success && razorpayPaymentId != null && signature != null) {
                // Client side success. Transition to VERIFYING.
                _uiState.update { it.copy(phase = PaymentPhase.VERIFYING, statusNote = "Verifying payment...") }

                val settlement = settlementRepository.getById(settlementId)
                if (settlement == null || settlement.paymentId == null || settlement.razorpayOrderId == null) {
                    applyResult(settlementId, PaymentResult.Failed("Local payment record missing.", razorpayPaymentId))
                    return@launch
                }

                // Verify with backend
                val verification = razorpayCheckoutManager.verifyPayment(
                    paymentId = settlement.paymentId,
                    razorpayPaymentId = razorpayPaymentId,
                    razorpayOrderId = settlement.razorpayOrderId,
                    signature = signature
                )

                if (verification.success) {
                    // Phase 4: We mark it VERIFIED. The backend server confirmed it.
                    val updated = settlementRepository.updateStatus(
                        settlementId = settlementId,
                        status = SettlementStatus.VERIFIED,
                        providerTxnId = razorpayPaymentId,
                        verificationNotes = "Payment verified by server."
                    )
                    _uiState.update {
                        it.copy(
                            phase = PaymentPhase.SUCCESS,
                            settlement = updated,
                            statusNote = "Payment verified by server.",
                            error = null
                        )
                    }
                    _effects.emit(PaymentEffect.Settled)
                } else {
                    val result = PaymentResult.Failed(
                        reason = verification.errorMessage ?: "Verification failed.",
                        providerTxnId = razorpayPaymentId
                    )
                    applyResult(settlementId, result)
                }
            } else {
                val result = PaymentResult.Failed(
                    reason = errorMsg ?: "Razorpay checkout failed or was cancelled.",
                    providerTxnId = razorpayPaymentId
                )
                applyResult(settlementId, result)
            }
        }
    }

    /** The UPI app was never reached (not installed, or the launch failed). */
    fun onUpiUnavailable(settlementId: String) {
        viewModelScope.launch {
            settlementRepository.updateStatus(
                settlementId = settlementId,
                status = SettlementStatus.CANCELLED,
                verificationNotes = "No UPI app available to handle the payment."
            )
            _uiState.update {
                it.copy(
                    phase = PaymentPhase.REVIEW,
                    isSubmitting = false,
                    error = "No UPI app was found on this device. Install one, or record the " +
                        "payment as cash or a bank transfer."
                )
            }
        }
    }

    private suspend fun applyResult(
        settlementId: String,
        result: PaymentResult,
        extraNote: String? = null
    ) {
        val settlement = settlementRepository.getById(settlementId)
        if (settlement == null) {
            _uiState.update {
                it.copy(phase = PaymentPhase.FAILED, error = "This payment record could not be found.")
            }
            return
        }

        val resolved = settlementEngine.resolve(settlement, result, paymentVerifier)
        val notes = listOfNotNull(resolved.notes, extraNote).joinToString(" ")

        val updated = settlementRepository.updateStatus(
            settlementId = settlementId,
            status = resolved.status,
            providerTxnId = resolved.providerTxnId,
            verificationNotes = notes
        )

        val phase = when (resolved.status) {
            SettlementStatus.VERIFIED -> PaymentPhase.SUCCESS
            SettlementStatus.PENDING_VERIFICATION -> PaymentPhase.PENDING
            else -> PaymentPhase.FAILED
        }

        _uiState.update {
            it.copy(
                isSubmitting = false,
                phase = phase,
                settlement = updated,
                statusNote = notes,
                error = null
            )
        }

        if (phase == PaymentPhase.SUCCESS) _effects.emit(PaymentEffect.Settled)
    }

    /**
     * Retry after a failure. A new attempt token means a new settlement row, so the failed
     * attempt stays in the history rather than being overwritten.
     */
    fun retry() {
        attemptToken = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(phase = PaymentPhase.REVIEW, settlement = null, statusNote = null, error = null)
        }
        refresh()
    }

    /** Resolve a payment left in-flight by a crash or a lost callback. */
    fun resolveUnresolved(settlementId: String, didPay: Boolean) {
        viewModelScope.launch {
            settlementRepository.updateStatus(
                settlementId = settlementId,
                status = if (didPay) SettlementStatus.VERIFIED else SettlementStatus.CANCELLED,
                verificationNotes = if (didPay) {
                    "Confirmed by the payer after an interrupted payment. Not verified against the bank."
                } else {
                    "Marked as not completed by the payer after an interrupted payment."
                }
            )
            _effects.emit(
                PaymentEffect.Message(
                    if (didPay) "Marked as paid." else "Payment discarded."
                )
            )
            refresh()
        }
    }
}
