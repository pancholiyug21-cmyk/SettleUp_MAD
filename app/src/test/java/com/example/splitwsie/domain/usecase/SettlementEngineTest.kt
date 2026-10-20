package com.example.splitwsie.domain.usecase

import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.Group
import com.example.splitwsie.domain.model.PaymentMethod
import com.example.splitwsie.domain.model.Settlement
import com.example.splitwsie.domain.model.SettlementStatus
import com.example.splitwsie.domain.model.Split
import com.example.splitwsie.domain.model.SplitType
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.payment.DeviceReportedVerifier
import com.example.splitwsie.domain.payment.PaymentResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SettlementEngineTest {

    private val engine = SettlementEngine(BalanceCalculator())
    private val verifier = DeviceReportedVerifier()

    private val group = Group(
        id = "g1",
        name = "Trip",
        members = listOf(
            User("A", "Ana", "ana@x.com"),
            User("B", "Ben", "ben@x.com")
        ),
        createdByUserId = "A"
    )

    /** B owes A 100. */
    private val expenses = listOf(
        Expense(
            id = "e1",
            groupId = "g1",
            title = "Dinner",
            amount = 100.0,
            category = "GENERAL",
            date = Date(),
            paidById = "A",
            splits = listOf(Split("B", 100.0, SplitType.EXACT))
        )
    )

    private fun check(
        actingUser: String = "B",
        from: String = "B",
        to: String = "A",
        amount: Double = 100.0,
        settlements: List<Settlement> = emptyList()
    ) = engine.check(group, expenses, settlements, actingUser, from, to, amount)

    private fun settlement(
        status: SettlementStatus = SettlementStatus.INITIATED,
        amount: Double = 100.0
    ) = Settlement(
        id = "s1",
        groupId = "g1",
        fromUserId = "B",
        toUserId = "A",
        amount = amount,
        date = Date(),
        method = PaymentMethod.UPI_INTENT,
        status = status,
        idempotencyKey = "key-1"
    )

    // --- Validation --------------------------------------------------------------------

    @Test
    fun `a debtor may settle their own debt`() {
        val result = check()
        assertTrue("expected allowed, got $result", result is SettlementCheck.Allowed)
        assertEquals(100.0, (result as SettlementCheck.Allowed).amount, 0.001)
    }

    @Test
    fun `a partial payment is allowed`() {
        assertTrue(check(amount = 40.0) is SettlementCheck.Allowed)
    }

    @Test
    fun `paying more than is owed is refused`() {
        val result = check(amount = 150.0)
        assertTrue(result is SettlementCheck.Rejected)
        assertTrue(
            (result as SettlementCheck.Rejected).reason is SettlementRejection.ExceedsOutstanding
        )
    }

    @Test
    fun `settling a debt that is not yours is refused`() {
        // A tries to record that B paid them.
        val result = check(actingUser = "A", from = "B", to = "A")
        assertEquals(
            SettlementRejection.NotAuthorised,
            (result as SettlementCheck.Rejected).reason
        )
    }

    @Test
    fun `zero and negative amounts are refused`() {
        assertEquals(
            SettlementRejection.AmountNotPositive,
            (check(amount = 0.0) as SettlementCheck.Rejected).reason
        )
        assertEquals(
            SettlementRejection.AmountNotPositive,
            (check(amount = -10.0) as SettlementCheck.Rejected).reason
        )
    }

    @Test
    fun `absurdly large amounts are refused`() {
        assertTrue((check(amount = 1e12) as SettlementCheck.Rejected).reason is SettlementRejection.AmountTooLarge)
    }

    @Test
    fun `paying yourself is refused`() {
        assertEquals(
            SettlementRejection.SelfPayment,
            (check(actingUser = "B", from = "B", to = "B") as SettlementCheck.Rejected).reason
        )
    }

    @Test
    fun `paying a non-member is refused`() {
        assertEquals(
            SettlementRejection.PayeeNotInGroup,
            (check(to = "Z") as SettlementCheck.Rejected).reason
        )
    }

    @Test
    fun `settling an already-settled debt is refused`() {
        val settled = listOf(settlement(SettlementStatus.VERIFIED))
        val result = check(settlements = settled, amount = 100.0)
        assertTrue((result as SettlementCheck.Rejected).reason is SettlementRejection.ExceedsOutstanding)
    }

    @Test
    fun `a second payment is refused while one is still in flight`() {
        for (status in listOf(SettlementStatus.INITIATED, SettlementStatus.PENDING_VERIFICATION)) {
            val result = check(settlements = listOf(settlement(status)))
            assertTrue(
                "$status must block a second payment",
                (result as SettlementCheck.Rejected).reason is SettlementRejection.AlreadyInFlight
            )
        }
    }

    @Test
    fun `a failed payment does not block a retry`() {
        assertTrue(check(settlements = listOf(settlement(SettlementStatus.FAILED))) is SettlementCheck.Allowed)
    }

    // --- Idempotency -------------------------------------------------------------------

    @Test
    fun `the same attempt produces the same idempotency key`() {
        val a = engine.idempotencyKey("g1", "B", "A", 100.0, "token-1")
        val b = engine.idempotencyKey("g1", "B", "A", 100.0, "token-1")
        assertEquals(a, b)
    }

    @Test
    fun `a new attempt or a different amount produces a different key`() {
        val base = engine.idempotencyKey("g1", "B", "A", 100.0, "token-1")
        assertNotEquals(base, engine.idempotencyKey("g1", "B", "A", 100.0, "token-2"))
        assertNotEquals(base, engine.idempotencyKey("g1", "B", "A", 99.0, "token-1"))
        assertNotEquals(base, engine.idempotencyKey("g2", "B", "A", 100.0, "token-1"))
    }

    // --- Payment outcome handling ------------------------------------------------------

    @Test
    fun `a reported success verifies the settlement`() = runBlocking {
        val resolved = engine.resolve(settlement(), PaymentResult.Success("TXN1", "REF1"), verifier)
        assertEquals(SettlementStatus.VERIFIED, resolved.status)
        assertEquals("TXN1", resolved.providerTxnId)
        assertFalse(resolved.wasDuplicate)
    }

    @Test
    fun `a submitted payment stays pending and does not verify`() = runBlocking {
        val resolved = engine.resolve(settlement(), PaymentResult.Submitted("TXN1", "REF1"), verifier)
        assertEquals(SettlementStatus.PENDING_VERIFICATION, resolved.status)
    }

    @Test
    fun `a failed payment is marked failed`() = runBlocking {
        val resolved = engine.resolve(settlement(), PaymentResult.Failed("declined"), verifier)
        assertEquals(SettlementStatus.FAILED, resolved.status)
    }

    @Test
    fun `a cancelled payment is marked cancelled`() = runBlocking {
        val resolved = engine.resolve(settlement(), PaymentResult.Cancelled, verifier)
        assertEquals(SettlementStatus.CANCELLED, resolved.status)
    }

    @Test
    fun `an unreadable response is never treated as success`() = runBlocking {
        val resolved = engine.resolve(settlement(), PaymentResult.Unknown("garbage"), verifier)
        assertNotEquals(SettlementStatus.VERIFIED, resolved.status)
        assertEquals(SettlementStatus.PENDING_VERIFICATION, resolved.status)
    }

    @Test
    fun `a duplicate callback on a verified settlement changes nothing`() = runBlocking {
        val alreadyVerified = settlement(SettlementStatus.VERIFIED)
        val resolved = engine.resolve(alreadyVerified, PaymentResult.Success("TXN2", "REF2"), verifier)

        assertTrue(resolved.wasDuplicate)
        assertEquals(SettlementStatus.VERIFIED, resolved.status)
        // The second transaction id must not overwrite the first.
        assertNotEquals("TXN2", resolved.providerTxnId)
    }

    @Test
    fun `a late success on a cancelled settlement does not revive it`() = runBlocking {
        val resolved = engine.resolve(
            settlement(SettlementStatus.CANCELLED),
            PaymentResult.Success("TXN3", "REF3"),
            verifier
        )
        assertTrue(resolved.wasDuplicate)
        assertEquals(SettlementStatus.CANCELLED, resolved.status)
    }

    @Test
    fun `a device-confirmed payment records that it was not server verified`() = runBlocking {
        val resolved = engine.resolve(settlement(), PaymentResult.Success("TXN1", "REF1"), verifier)
        assertTrue(
            "notes should disclose the assurance level, got: ${resolved.notes}",
            resolved.notes.contains("not independently verified", ignoreCase = true)
        )
    }

    @Test
    fun `a manually recorded payment is verified and says so`() {
        val resolved = engine.resolveManual(PaymentMethod.CASH, "UTR123")
        assertEquals(SettlementStatus.VERIFIED, resolved.status)
        assertEquals("UTR123", resolved.providerTxnId)
        assertTrue(resolved.notes.contains("Recorded manually"))
    }

    @Test
    fun `transaction reference is derived from the settlement id`() {
        val ref = engine.transactionRef("0f8b7c1e-1234-4a5b-9c8d-abcdef012345")
        assertTrue(ref.startsWith("STL"))
        assertFalse(ref.contains("-"))
        assertTrue("UPI truncates long refs", ref.length <= 35)
    }
}
