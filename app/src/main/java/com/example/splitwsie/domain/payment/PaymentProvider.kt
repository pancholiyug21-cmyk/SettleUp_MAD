package com.example.splitwsie.domain.payment

import com.example.splitwsie.domain.model.PaymentMethod

/**
 * Outcome of a payment attempt, as reported by whatever rail handled it.
 *
 * Deliberately distinguishes [Submitted] (bank is processing; outcome unknown) from
 * [Success]. Collapsing the two is how apps end up marking unpaid debts as settled.
 */
sealed class PaymentResult {
    data class Success(val providerTxnId: String?, val providerRefId: String?) : PaymentResult()
    data class Submitted(val providerTxnId: String?, val providerRefId: String?) : PaymentResult()
    data class Failed(val reason: String, val providerTxnId: String? = null) : PaymentResult()
    data object Cancelled : PaymentResult()

    /** The payment app returned something we could not interpret. Never treated as success. */
    data class Unknown(val raw: String?) : PaymentResult()
}

/**
 * Everything needed to hand a payment off to an external app or gateway.
 */
data class PaymentRequest(
    val settlementId: String,
    val payeeVpa: String?,
    val payeeName: String,
    val amount: Double,
    val note: String,
    /** Passed to the rail as our reference so the response can be correlated back. */
    val transactionRef: String,
    val method: PaymentMethod
)

/**
 * Confirms whether a reported payment actually happened.
 *
 * The UPI intent response arrives through the client and is therefore not trustworthy on its
 * own: a malicious app can return `Status=SUCCESS` without any money moving. NPCI's own
 * guidance is that the merchant must confirm the transaction server-side.
 *
 * [DeviceReportedVerifier] is the honest local implementation — it trusts the response and
 * says so. A production deployment replaces it with a server-backed implementation; see
 * `PAYMENTS.md` for the required endpoint contract and configuration.
 */
interface PaymentVerifier {

    /** Human-readable description of what this verifier actually proves. */
    val assuranceDescription: String

    /** True when settlements confirmed by this verifier may be treated as VERIFIED. */
    val isServerVerified: Boolean

    suspend fun verify(settlementId: String, result: PaymentResult): VerificationOutcome
}

data class VerificationOutcome(
    val confirmed: Boolean,
    val pending: Boolean,
    val notes: String
)

/**
 * Verifier used when no payment backend is configured.
 *
 * It accepts what the UPI app reported and records, on the settlement itself, that the
 * confirmation is device-reported rather than server-verified. It never upgrades an unknown
 * or submitted response into a confirmation.
 */
class DeviceReportedVerifier : PaymentVerifier {

    override val assuranceDescription: String =
        "Confirmed by the UPI app on this device. Not independently verified against the bank."

    override val isServerVerified: Boolean = false

    override suspend fun verify(settlementId: String, result: PaymentResult): VerificationOutcome =
        when (result) {
            is PaymentResult.Success -> VerificationOutcome(
                confirmed = true,
                pending = false,
                notes = "Device-reported success from UPI app" +
                    (result.providerTxnId?.let { " (txn $it)" } ?: "")
            )

            is PaymentResult.Submitted -> VerificationOutcome(
                confirmed = false,
                pending = true,
                notes = "Submitted to the bank. Awaiting confirmation before the balance updates."
            )

            is PaymentResult.Failed -> VerificationOutcome(
                confirmed = false,
                pending = false,
                notes = "Payment failed: ${result.reason}"
            )

            PaymentResult.Cancelled -> VerificationOutcome(
                confirmed = false,
                pending = false,
                notes = "Cancelled before authorisation."
            )

            is PaymentResult.Unknown -> VerificationOutcome(
                confirmed = false,
                pending = true,
                notes = "The payment app did not report an outcome. Check your bank before retrying."
            )
        }
}
