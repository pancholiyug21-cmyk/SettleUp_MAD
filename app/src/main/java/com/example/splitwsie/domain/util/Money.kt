package com.example.splitwsie.domain.util

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * All monetary arithmetic in the app goes through this object.
 *
 * Rationale: Double arithmetic on currency accumulates drift (e.g. 100.0 / 3 * 3 != 100.0),
 * which previously caused balances that never reached exactly zero and "settled" groups that
 * still showed a stray ₹0.01 debt. Amounts are therefore converted to integer paise for every
 * calculation and only converted back for storage/display.
 */
object Money {

    /** Smallest amount the app treats as non-zero. Anything below this is considered settled. */
    const val EPSILON = 0.005

    /** Largest single expense/settlement the app accepts (₹1 crore). Guards against typos and overflow. */
    const val MAX_AMOUNT = 10_000_000.0

    fun toPaise(amount: Double): Long = (amount * 100.0).roundToLong()

    fun fromPaise(paise: Long): Double = paise / 100.0

    /** Rounds to 2 decimal places (paise precision). */
    fun round(amount: Double): Double = fromPaise(toPaise(amount))

    /** True when the amount is effectively zero. */
    fun isZero(amount: Double): Boolean = abs(amount) < EPSILON

    /**
     * Splits [totalPaise] into [count] shares that sum back to exactly [totalPaise].
     * The remainder is distributed one paisa at a time to the first shares, so
     * ₹100 across 3 people becomes 33.34 / 33.33 / 33.33 rather than 33.33 x 3 = 99.99.
     */
    fun splitEqually(totalPaise: Long, count: Int): List<Long> {
        require(count > 0) { "Cannot split between zero people" }
        val base = totalPaise / count
        val remainder = (totalPaise % count).toInt()
        return List(count) { index -> if (index < remainder) base + 1 else base }
    }

    /**
     * Distributes [totalPaise] according to [percentages] (which must sum to 100).
     * Any rounding remainder is added to the largest share so the result sums exactly.
     */
    fun splitByPercentage(totalPaise: Long, percentages: List<Double>): List<Long> {
        require(percentages.isNotEmpty()) { "Cannot split between zero people" }
        val raw = percentages.map { pct -> ((totalPaise * pct) / 100.0).roundToLong() }
        val diff = totalPaise - raw.sum()
        if (diff == 0L) return raw
        val targetIndex = raw.indices.maxByOrNull { raw[it] } ?: 0
        return raw.mapIndexed { index, value -> if (index == targetIndex) value + diff else value }
    }

    /** Formats for display, e.g. 1234.5 -> "₹1,234.50". */
    fun format(amount: Double): String =
        "₹%,.2f".format(round(amount))

    /**
     * Distributes [totalPaise] proportionally according to the provided [shares].
     * Any rounding remainder is added to the largest share so the result sums exactly.
     */
    fun splitByShares(totalPaise: Long, shares: List<Double>): List<Long> {
        require(shares.isNotEmpty()) { "Cannot split between zero people" }
        val totalShares = shares.sum()
        if (totalShares <= 0) return List(shares.size) { 0L }
        val raw = shares.map { share -> ((totalPaise * share) / totalShares).roundToLong() }
        val diff = totalPaise - raw.sum()
        if (diff == 0L) return raw
        val targetIndex = raw.indices.maxByOrNull { raw[it] } ?: 0
        return raw.mapIndexed { index, value -> if (index == targetIndex) value + diff else value }
    }
}
