package com.example.splitwsie.domain.model

data class ExpenseFilter(
    val query: String = "",
    val categoryId: String? = null,
    val memberId: String? = null
) {
    val isActive: Boolean
        get() = query.isNotBlank() || categoryId != null || memberId != null

    fun matches(expense: Expense): Boolean {
        if (query.isNotBlank() && !expense.title.contains(query, ignoreCase = true)) {
            return false
        }
        if (categoryId != null && expense.category != categoryId) {
            return false
        }
        if (memberId != null && expense.paidById != memberId) {
            return false
        }
        return true
    }
}
