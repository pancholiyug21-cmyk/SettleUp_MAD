package com.example.splitwsie.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `equal split always sums back to the total`() {
        // 100 / 3 is the classic case: 33.33 x 3 loses a paisa.
        val shares = Money.splitEqually(Money.toPaise(100.0), 3)
        assertEquals(10_000L, shares.sum())
        assertEquals(listOf(3334L, 3333L, 3333L), shares)
    }

    @Test
    fun `equal split handles amounts that divide cleanly`() {
        val shares = Money.splitEqually(Money.toPaise(90.0), 3)
        assertEquals(listOf(3000L, 3000L, 3000L), shares)
        assertEquals(9_000L, shares.sum())
    }

    @Test
    fun `equal split across many people still sums exactly`() {
        val total = Money.toPaise(1000.0)
        for (count in 1..37) {
            assertEquals(
                "split into $count parts must sum back to the total",
                total,
                Money.splitEqually(total, count).sum()
            )
        }
    }

    @Test
    fun `equal split of one paisa gives it to a single person`() {
        val shares = Money.splitEqually(1L, 3)
        assertEquals(listOf(1L, 0L, 0L), shares)
        assertEquals(1L, shares.sum())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `equal split rejects zero people`() {
        Money.splitEqually(1000L, 0)
    }

    @Test
    fun `percentage split sums back to the total despite rounding`() {
        val total = Money.toPaise(100.0)
        val shares = Money.splitByPercentage(total, listOf(33.33, 33.33, 33.34))
        assertEquals(total, shares.sum())
    }

    @Test
    fun `percentage split with uneven thirds still balances`() {
        val total = Money.toPaise(0.10)
        val shares = Money.splitByPercentage(total, listOf(33.0, 33.0, 34.0))
        assertEquals(total, shares.sum())
    }

    @Test
    fun `paise conversion round trips`() {
        assertEquals(12_345L, Money.toPaise(123.45))
        assertEquals(123.45, Money.fromPaise(12_345L), 0.0)
        // 0.1 + 0.2 is the canonical floating point trap.
        assertEquals(30L, Money.toPaise(0.1 + 0.2))
    }

    @Test
    fun `rounding snaps to paise`() {
        assertEquals(10.13, Money.round(10.125001), 0.0)
        assertEquals(10.12, Money.round(10.124), 0.0)
    }

    @Test
    fun `isZero tolerates sub-paisa residue but not a real paisa`() {
        assertTrue(Money.isZero(0.0))
        assertTrue(Money.isZero(0.0001))
        assertTrue(Money.isZero(-0.0001))
        assertFalse(Money.isZero(0.01))
        assertFalse(Money.isZero(-0.01))
    }
}
