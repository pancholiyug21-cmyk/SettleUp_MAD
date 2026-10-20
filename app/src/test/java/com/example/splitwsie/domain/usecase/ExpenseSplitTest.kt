package com.example.splitwsie.domain.usecase

import com.example.splitwsie.domain.model.SplitType
import com.example.splitwsie.domain.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the split arithmetic that ExpenseViewModel.buildSplits delegates to.
 *
 * The invariant under test throughout: the shares must sum to exactly the expense amount, in
 * paise. Anything else silently creates or destroys money in every balance the expense feeds.
 */
class ExpenseSplitTest {

    private fun equalShares(amount: Double, people: Int): List<Double> =
        Money.splitEqually(Money.toPaise(amount), people).map { Money.fromPaise(it) }

    private fun percentageShares(amount: Double, percentages: List<Double>): List<Double> =
        Money.splitByPercentage(Money.toPaise(amount), percentages).map { Money.fromPaise(it) }

    @Test
    fun `equal split of an indivisible amount still sums to the total`() {
        val shares = equalShares(100.0, 3)
        assertEquals(100.0, shares.sum(), 0.0001)
        assertEquals(listOf(33.34, 33.33, 33.33), shares)
    }

    @Test
    fun `equal split between two people`() {
        assertEquals(listOf(50.0, 50.0), equalShares(100.0, 2))
    }

    @Test
    fun `equal split for a single person gives them everything`() {
        assertEquals(listOf(75.5), equalShares(75.5, 1))
    }

    @Test
    fun `equal split across a large group sums exactly`() {
        for (people in 1..25) {
            assertEquals(
                "split of 1000 across $people people",
                1000.0,
                equalShares(1000.0, people).sum(),
                0.0001
            )
        }
    }

    @Test
    fun `equal split of a decimal amount sums exactly`() {
        assertEquals(0.10, equalShares(0.10, 3).sum(), 0.0001)
        assertEquals(1234.57, equalShares(1234.57, 7).sum(), 0.0001)
    }

    @Test
    fun `percentage split sums to the total even with repeating decimals`() {
        assertEquals(100.0, percentageShares(100.0, listOf(33.33, 33.33, 33.34)).sum(), 0.0001)
        assertEquals(100.0, percentageShares(100.0, listOf(50.0, 50.0)).sum(), 0.0001)
        assertEquals(99.99, percentageShares(99.99, listOf(70.0, 30.0)).sum(), 0.0001)
    }

    @Test
    fun `percentage split assigns proportional shares`() {
        val shares = percentageShares(200.0, listOf(25.0, 75.0))
        assertEquals(50.0, shares[0], 0.0001)
        assertEquals(150.0, shares[1], 0.0001)
    }

    @Test
    fun `exact split is accepted only when it adds up`() {
        val total = Money.toPaise(100.0)
        assertEquals(total, listOf(30.0, 30.0, 40.0).sumOf { Money.toPaise(it) })
        // A split that is one paisa short must be detectable in paise, not lost to rounding.
        assertTrue(listOf(30.0, 30.0, 39.99).sumOf { Money.toPaise(it) } != total)
    }

    @Test
    fun `split type names are stable for persistence`() {
        // These strings are written to the database; renaming them would orphan existing rows.
        assertEquals("EQUAL", SplitType.EQUAL.name)
        assertEquals("EXACT", SplitType.EXACT.name)
        assertEquals("PERCENTAGE", SplitType.PERCENTAGE.name)
        assertEquals("SHARES", SplitType.SHARES.name)
    }

    private fun shareShares(amount: Double, shares: List<Double>): List<Double> =
        Money.splitByShares(Money.toPaise(amount), shares).map { Money.fromPaise(it) }

    @Test
    fun `shares split assigns proportional shares`() {
        val shares = shareShares(300.0, listOf(1.0, 2.0))
        assertEquals(100.0, shares[0], 0.0001)
        assertEquals(200.0, shares[1], 0.0001)

        val unevenShares = shareShares(100.0, listOf(1.0, 2.0, 3.0))
        assertEquals(100.0, unevenShares.sum(), 0.0001)
        // 16.666, 33.333, 50
        assertEquals(16.67, unevenShares[0], 0.0001)
        assertEquals(33.33, unevenShares[1], 0.0001)
        assertEquals(50.00, unevenShares[2], 0.0001)
    }

    @Test
    fun `shares split sums to the total exactly`() {
        assertEquals(1000.0, shareShares(1000.0, listOf(2.0, 3.0, 5.0)).sum(), 0.0001)
        assertEquals(100.0, shareShares(100.0, listOf(1.0, 1.0, 1.0)).sum(), 0.0001)
        assertEquals(999.0, shareShares(999.0, listOf(7.0)).sum(), 0.0001)
    }
}
