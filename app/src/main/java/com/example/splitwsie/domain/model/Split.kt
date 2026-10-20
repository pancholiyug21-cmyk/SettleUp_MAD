package com.example.splitwsie.domain.model

/**
 * How an expense is divided.
 *
 * Modelled as an enum (previously a sealed class of objects) so `when` blocks over it are
 * exhaustive at compile time and a new split type cannot be silently unhandled.
 * The persisted names are unchanged, so existing rows keep working.
 */
enum class SplitType {
    EQUAL,
    EXACT,
    PERCENTAGE,
    SHARES;

    val displayName: String
        get() = when (this) {
            EQUAL -> "Equal"
            EXACT -> "Exact"
            PERCENTAGE -> "%"
            SHARES -> "Shares"
        }
}

data class Split(
    val userId: String,
    val amountOwed: Double,
    val splitType: SplitType = SplitType.EQUAL,
    /** Only meaningful when [splitType] is [SplitType.PERCENTAGE]. */
    val percentage: Double? = null,
    /** Only meaningful when [splitType] is [SplitType.SHARES]. */
    val shares: Double? = null
)
