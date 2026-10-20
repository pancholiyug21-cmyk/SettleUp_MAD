package com.example.splitwsie.domain.usecase

import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.Group
import com.example.splitwsie.domain.model.PaymentMethod
import com.example.splitwsie.domain.model.Settlement
import com.example.splitwsie.domain.model.SettlementStatus
import com.example.splitwsie.domain.payment.PaymentResult
import com.example.splitwsie.domain.payment.PaymentVerifier
import com.example.splitwsie.domain.util.Money
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reasons a settlement can be refused before any money moves.
 */
sealed class SettlementRejection(val message: String) {
    data object NotAuthorised : SettlementRejection(
        "You can only settle debts that you owe."
    )
    data object PayeeNotInGroup : SettlementRejection(
        "That person is no longer a member of this group."
    )
    data object PayerNotInGroup : SettlementRejection(
        "You are no longer a member of this group."
    )
    data object SelfPayment : SettlementRejection(
        "You cannot pay yourself."
    )
    data object AmountNotPositive : SettlementRejection(
        "Enter an amount greater than zero."
    )
    data object AmountTooLarge : SettlementRejection(
        "That amount is larger than this app supports."
    )
    data class ExceedsOutstanding(val outstanding: Double) : SettlementRejection(
        if (outstanding <= 0) "This debt is already settled."
        else "You only owe ${Money.format(outstanding)}. Reduce the amount to continue."
    )
    data class AlreadyInFlight(val settlement: Settlement) : SettlementRejection(
        "A payment of ${Money.format(settlement.amount)} to this person is already in progress. " +
            "Resolve it before starting another."
    )
}

sealed class SettlementCheck {
    data class Allowed(val amount: Double) : SettlementCheck()
    data class Rejected(val reason: SettlementRejection) : SettlementCheck()
}

/**
 * Owns every state change a settlement can undergo.
 *
 * All validation lives here rather than in the UI, so the same rules apply no matter which
 * screen or payment method initiated the payment, and so they can be lifted into a server
 * unchanged.
 *
 * Note on trust: these checks run on the client. On a device-only build they prevent honest
 * mistakes -- wrong amount, paying a debt you do not owe, double-paying -- but they are not a
 * security boundary against a user who modifies the app or its database. Enforcing them
 * against a hostile client requires the server described in PAYMENTS.md.
 */
@Singleton
class SettlementEngine @Inject constructor(
    private val balanceCalculator: BalanceCalculator
) {

    /**
     * Validates a proposed payment against the live balance.
     *
     * @param actingUserId the signed-in user. A payment is only allowed if they are the payer,
     *   which is what stops one member settling a debt on someone else's behalf.
     */
    fun check(
        group: Group?,
        expenses: List<Expense>,
        settlements: List<Settlement>,
        actingUserId: String,
        fromUserId: String,
        toUserId: String,
        amount: Double
    ): SettlementCheck {
        if (fromUserId.isBlank() || toUserId.isBlank() || actingUserId.isBlank()) {
            return SettlementCheck.Rejected(SettlementRejection.NotAuthorised)
        }
        if (fromUserId != actingUserId) {
            return SettlementCheck.Rejected(SettlementRejection.NotAuthorised)
        }
        if (fromUserId == toUserId) {
            return SettlementCheck.Rejected(SettlementRejection.SelfPayment)
        }
        if (group != null) {
            val memberIds = group.members.map { it.id }.toSet()
            if (fromUserId !in memberIds) {
                return SettlementCheck.Rejected(SettlementRejection.PayerNotInGroup)
            }
            if (toUserId !in memberIds) {
                return SettlementCheck.Rejected(SettlementRejection.PayeeNotInGroup)
            }
        }
        if (amount <= 0 || Money.isZero(amount)) {
            return SettlementCheck.Rejected(SettlementRejection.AmountNotPositive)
        }
        if (amount > Money.MAX_AMOUNT) {
            return SettlementCheck.Rejected(SettlementRejection.AmountTooLarge)
        }

        settlements.firstOrNull {
            it.status.isInFlight && it.fromUserId == fromUserId && it.toUserId == toUserId
        }?.let { return SettlementCheck.Rejected(SettlementRejection.AlreadyInFlight(it)) }

        val outstanding = balanceCalculator.outstandingBetween(
            expenses = expenses,
            settlements = settlements,
            userId = fromUserId,
            otherUserId = toUserId
        )

        // One-paisa tolerance, so a legitimate "pay it all off" is never blocked by a
        // sub-paisa discrepancy.
        if (Money.toPaise(amount) > Money.toPaise(outstanding) + 1) {
            return SettlementCheck.Rejected(SettlementRejection.ExceedsOutstanding(outstanding))
        }

        return SettlementCheck.Allowed(Money.round(amount))
    }

    /**
     * Deterministic idempotency key for a payment attempt.
     *
     * Keyed on the debt (group + payer + payee + amount) plus [attemptToken]. A retry of the
     * same attempt -- an Activity recreated, a callback delivered twice -- reuses the key and
     * therefore the existing settlement row. A deliberate new attempt after a failure passes a
     * fresh token and gets a new row.
     */
    fun idempotencyKey(
        groupId: String,
        fromUserId: String,
        toUserId: String,
        amount: Double,
        attemptToken: String
    ): String = listOf(
        groupId,
        fromUserId,
        toUserId,
        Money.toPaise(amount).toString(),
        attemptToken
    ).joinToString(separator = ":")

    /** Reference passed to the payment rail. Kept short: UPI apps truncate long `tr` values. */
    fun transactionRef(settlementId: String): String =
        "STL" + settlementId.replace("-", "").take(TXN_REF_LENGTH).uppercase()

    /**
     * Applies a payment outcome to a settlement.
     *
     * Terminal settlements are returned unchanged, so a duplicated success callback cannot
     * apply the same payment to the balance twice.
     */
    suspend fun resolve(
        settlement: Settlement,
        result: PaymentResult,
        verifier: PaymentVerifier
    ): ResolvedSettlement {
        if (settlement.status.isTerminal) {
            return ResolvedSettlement(
                status = settlement.status,
                providerTxnId = settlement.providerTxnId,
                notes = settlement.verificationNotes ?: "Already resolved.",
                wasDuplicate = true
            )
        }

        val outcome = verifier.verify(settlement.id, result)
        val providerTxnId = when (result) {
            is PaymentResult.Success -> result.providerTxnId
            is PaymentResult.Submitted -> result.providerTxnId
            is PaymentResult.Failed -> result.providerTxnId
            else -> null
        }

        val status = when {
            outcome.confirmed -> SettlementStatus.VERIFIED
            outcome.pending -> SettlementStatus.PENDING_VERIFICATION
            result is PaymentResult.Cancelled -> SettlementStatus.CANCELLED
            else -> SettlementStatus.FAILED
        }

        val assurance = if (status == SettlementStatus.VERIFIED && !verifier.isServerVerified) {
            "${outcome.notes} - ${verifier.assuranceDescription}"
        } else {
            outcome.notes
        }

        return ResolvedSettlement(
            status = status,
            providerTxnId = providerTxnId,
            notes = assurance,
            wasDuplicate = false
        )
    }

    /**
     * A manually recorded payment (cash, or a bank transfer made outside the app).
     *
     * Recorded as VERIFIED because the user is asserting first-hand that the money changed
     * hands -- the same trust model Splitwise uses for "settle up" -- and the note records
     * that no payment was processed by the app.
     */
    fun resolveManual(method: PaymentMethod, reference: String?): ResolvedSettlement =
        ResolvedSettlement(
            status = SettlementStatus.VERIFIED,
            providerTxnId = reference?.takeIf { it.isNotBlank() },
            notes = "Recorded manually as paid by ${method.displayName.lowercase()}. " +
                "No payment was processed by the app.",
            wasDuplicate = false
        )

    private companion object {
        const val TXN_REF_LENGTH = 20
    }
}

data class ResolvedSettlement(
    val status: SettlementStatus,
    val providerTxnId: String?,
    val notes: String,
    val wasDuplicate: Boolean
)
