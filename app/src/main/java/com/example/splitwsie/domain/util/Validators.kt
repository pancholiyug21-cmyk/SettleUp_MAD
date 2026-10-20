package com.example.splitwsie.domain.util

/**
 * Input validation shared by every screen, so the same rule is not re-implemented
 * (and re-implemented differently) in each form.
 *
 * Each function returns a user-facing error message, or null when the value is acceptable.
 */
object Validators {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val PHONE_REGEX = Regex("^\\+?[0-9]{7,15}$")

    const val MAX_NAME_LENGTH = 60
    const val MAX_TITLE_LENGTH = 80
    const val MAX_NOTES_LENGTH = 300
    const val MAX_GROUP_NAME_LENGTH = 50
    const val MAX_DESCRIPTION_LENGTH = 200

    fun validateName(value: String): String? = when {
        value.isBlank() -> "Enter a name"
        value.trim().length < 2 -> "Name must be at least 2 characters"
        value.trim().length > MAX_NAME_LENGTH -> "Name is too long"
        else -> null
    }

    fun validateEmail(value: String): String? = when {
        value.isBlank() -> "Enter an email address"
        !EMAIL_REGEX.matches(value.trim()) -> "Enter a valid email address"
        else -> null
    }

    fun validateOptionalPhone(value: String): String? = when {
        value.isBlank() -> null
        !PHONE_REGEX.matches(value.trim()) -> "Enter a valid phone number"
        else -> null
    }

    fun validateGroupName(value: String): String? = when {
        value.isBlank() -> "Enter a group name"
        value.trim().length < 2 -> "Group name must be at least 2 characters"
        value.trim().length > MAX_GROUP_NAME_LENGTH -> "Group name is too long"
        else -> null
    }

    fun validateExpenseTitle(value: String): String? = when {
        value.isBlank() -> "Describe what this expense was for"
        value.trim().length > MAX_TITLE_LENGTH -> "Description is too long"
        else -> null
    }

    /**
     * Validates a typed amount. Rejects unparseable input, zero, negatives and absurd values
     * rather than letting them reach the balance calculation.
     */
    fun validateAmount(raw: String): String? {
        val amount = raw.toDoubleOrNull()
        return when {
            raw.isBlank() -> "Enter an amount"
            amount == null || amount.isNaN() || amount.isInfinite() -> "Enter a valid amount"
            amount <= 0 -> "Amount must be greater than zero"
            Money.isZero(amount) -> "Amount must be at least ₹0.01"
            amount > Money.MAX_AMOUNT -> "Amount is too large"
            else -> null
        }
    }

    /**
     * Keeps a decimal text field to a single decimal point and at most two decimal places,
     * so the user cannot type an amount that silently rounds when saved.
     */
    fun sanitiseDecimalInput(value: String, maxDecimals: Int = 2): String {
        val filtered = value.filter { it.isDigit() || it == '.' }
        val parts = filtered.split('.')
        return if (parts.size <= 1) {
            filtered.take(12)
        } else {
            (parts[0].take(12) + "." + parts.drop(1).joinToString("").take(maxDecimals))
        }
    }
}
