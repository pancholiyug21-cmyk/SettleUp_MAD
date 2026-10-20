package com.example.splitwsie.domain.model

/**
 * Expense categories.
 *
 * The `category` column already existed and was written to the database, but the UI hard-coded
 * every expense to "General" with no way to change it, so the field carried no information.
 */
enum class ExpenseCategory(val displayName: String, val emoji: String) {
    GENERAL("General", "🧾"),
    FOOD("Food & Drink", "🍽️"),
    GROCERIES("Groceries", "🛒"),
    TRANSPORT("Transport", "🚕"),
    STAY("Stay", "🏨"),
    ENTERTAINMENT("Entertainment", "🎬"),
    SHOPPING("Shopping", "🛍️"),
    UTILITIES("Utilities", "💡"),
    HEALTH("Health", "💊"),
    OTHER("Other", "✨");

    companion object {
        /**
         * Unknown or legacy values fall back to [GENERAL] rather than throwing, so an expense
         * saved by an older build (which stored the literal string "General") still loads.
         */
        fun fromName(value: String?): ExpenseCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: entries.firstOrNull { it.displayName.equals(value, ignoreCase = true) }
                ?: GENERAL
    }
}
