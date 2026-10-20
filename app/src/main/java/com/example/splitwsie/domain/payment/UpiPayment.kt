package com.example.splitwsie.domain.payment

import android.content.Intent
import android.net.Uri
import com.example.splitwsie.domain.util.Money

/**
 * Builds and parses NPCI UPI deep links (`upi://pay`).
 *
 * This is a real payment rail: the intent is handled by the user's own UPI app (GPay, PhonePe,
 * Paytm, BHIM, ...) and moves real money out of their bank account. It needs no merchant
 * account, API key or gateway credential, which is why it is the default method here.
 *
 * Caveat that the rest of the app is built around: the response comes back through the client,
 * so it can be spoofed by a hostile app and is occasionally lost or wrong even when honest.
 * NPCI requires server-side verification for merchant flows. See [PaymentVerifier].
 */
object UpiPayment {

    const val UPI_SCHEME = "upi"
    private const val CURRENCY_INR = "INR"

    /** Statuses the UPI spec can return in the response payload. */
    object Status {
        const val SUCCESS = "SUCCESS"
        const val SUBMITTED = "SUBMITTED"
        const val FAILURE = "FAILURE"
    }

    /**
     * A VPA looks like `name@bank`. Deliberately permissive on the handle but strict on shape,
     * because a malformed VPA silently sends money nowhere or to the wrong person.
     */
    private val VPA_REGEX = Regex("""^[a-zA-Z0-9._-]{2,256}@[a-zA-Z][a-zA-Z0-9._-]{1,64}$""")

    fun isValidVpa(vpa: String?): Boolean =
        !vpa.isNullOrBlank() && VPA_REGEX.matches(vpa.trim())

    /**
     * Builds the payment intent.
     *
     * @param transactionRef our own reference (`tr`), which is how we correlate the response
     *   back to a settlement row even if the UPI app returns nothing else useful.
     */
    fun buildIntent(
        payeeVpa: String,
        payeeName: String,
        amount: Double,
        transactionRef: String,
        note: String
    ): Intent {
        require(isValidVpa(payeeVpa)) { "Invalid UPI ID" }
        require(amount > 0 && amount <= Money.MAX_AMOUNT) { "Invalid amount" }

        val uri = Uri.Builder()
            .scheme(UPI_SCHEME)
            .authority("pay")
            // Uri.Builder percent-encodes these, so names with spaces or '&' cannot
            // break out of the query string.
            .appendQueryParameter("pa", payeeVpa.trim())
            .appendQueryParameter("pn", payeeName.take(50))
            .appendQueryParameter("tr", transactionRef)
            .appendQueryParameter("tn", note.take(50))
            .appendQueryParameter("am", "%.2f".format(Money.round(amount)))
            .appendQueryParameter("cu", CURRENCY_INR)
            .build()

        return Intent(Intent.ACTION_VIEW, uri)
    }

    /**
     * Parses the `response` extra returned by the UPI app, e.g.
     * `txnId=AXI123&responseCode=00&Status=SUCCESS&txnRef=SETL123`.
     *
     * Keys are matched case-insensitively because vendors are inconsistent about casing.
     */
    fun parseResponse(response: String?): UpiResponse {
        if (response.isNullOrBlank()) {
            return UpiResponse(status = null, txnId = null, txnRef = null, responseCode = null, raw = response)
        }

        val fields = response.split('&')
            .mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) null else part.substring(0, index).trim().lowercase() to
                    part.substring(index + 1).trim()
            }
            .toMap()

        return UpiResponse(
            status = fields["status"]?.uppercase(),
            txnId = fields["txnid"]?.takeIf { it.isNotBlank() },
            txnRef = fields["txnref"]?.takeIf { it.isNotBlank() },
            responseCode = fields["responsecode"]?.takeIf { it.isNotBlank() },
            raw = response
        )
    }
}

data class UpiResponse(
    val status: String?,
    val txnId: String?,
    val txnRef: String?,
    val responseCode: String?,
    val raw: String?
) {
    val isSuccess: Boolean get() = status == UpiPayment.Status.SUCCESS
    val isSubmitted: Boolean get() = status == UpiPayment.Status.SUBMITTED
    val isFailure: Boolean get() = status == UpiPayment.Status.FAILURE

    /** No parseable status at all — treated as unknown, never as success. */
    val isUnknown: Boolean get() = status == null
}
