package com.example.splitwsie.domain.model

import java.util.Date

/**
 * Lifecycle of a settlement (a payment from one member to another).
 *
 * Only [VERIFIED] settlements reduce a balance. Everything else is visible to the user
 * so they do not pay twice, but is deliberately excluded from balance arithmetic.
 */
enum class SettlementStatus {
    INITIATED,
    ORDER_CREATED,
    CHECKOUT_OPENED,
    CLIENT_SUCCESS,
    CLIENT_FAILURE,
    PENDING_VERIFICATION,
    VERIFYING,
    PAYMENT_VERIFIED, // New state for Phase 4: server verified payment, but settlement incomplete
    VERIFIED,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == VERIFIED || this == FAILED || this == CANCELLED

    /** In-flight settlements block a second payment for the same debt. */
    val isInFlight: Boolean
        get() = this == INITIATED || this == ORDER_CREATED || this == CHECKOUT_OPENED || this == CLIENT_SUCCESS || this == PENDING_VERIFICATION || this == VERIFYING || this == PAYMENT_VERIFIED
}

enum class PaymentMethod {
    /** Real money movement through the user's UPI app (GPay / PhonePe / Paytm / BHIM). */
    UPI_INTENT,

    /** Razorpay standard checkout flow. */
    RAZORPAY,

    /** Money already changed hands outside the app; recorded manually. */
    CASH,

    /** Bank transfer made outside the app; recorded manually with a reference number. */
    BANK_TRANSFER,

    /** Simulated payment. Debug builds only — never moves money. */
    SANDBOX;

    val movesRealMoney: Boolean
        get() = this == UPI_INTENT

    val displayName: String
        get() = when (this) {
            UPI_INTENT -> "UPI"
            RAZORPAY -> "Razorpay"
            CASH -> "Cash"
            BANK_TRANSFER -> "Bank transfer"
            SANDBOX -> "Sandbox (test)"
        }
}

/**
 * A payment from [fromUserId] to [toUserId] settling part or all of a debt.
 *
 * [idempotencyKey] is unique in the database. It is derived from the debt being settled plus
 * the initiating attempt, so a retry after a lost callback reuses the same row instead of
 * creating a second payment.
 */
data class Settlement(
    val id: String,
    val groupId: String,
    val fromUserId: String,
    val toUserId: String,
    val amount: Double,
    val date: Date,
    val method: PaymentMethod,
    val status: SettlementStatus = SettlementStatus.INITIATED,
    val idempotencyKey: String,
    val providerTxnId: String? = null,
    /** Our own reference passed to the payment app (`tr` in the UPI spec) or razorpay_order_id. */
    val providerRefId: String? = null,
    /** Razorpay specific order ID, if applicable */
    val razorpayOrderId: String? = null,
    /** Backend payment document ID, if applicable */
    val paymentId: String? = null,
    /** Set when the settlement targets one specific expense rather than the net debt. */
    val expenseId: String? = null,
    val note: String? = null,
    val verificationNotes: String? = null,
    val updatedAt: Date = date
) {
    /** Only verified settlements move balances. */
    val countsTowardsBalance: Boolean
        get() = status == SettlementStatus.VERIFIED
}
