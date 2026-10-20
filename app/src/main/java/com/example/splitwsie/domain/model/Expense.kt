package com.example.splitwsie.domain.model

import java.util.Date

data class Expense(
    val id: String,
    val groupId: String?,
    val title: String,
    val amount: Double,
    val category: String,
    val date: Date,
    val paidById: String,
    val splits: List<Split>,
    val notes: String? = null,
    val receiptImageUrl: String? = null,
    val status: ExpenseStatus = ExpenseStatus.ACTIVE,
    val repeatInterval: RepeatInterval = RepeatInterval.NONE,
    val nextRepeatDate: Date? = null
)

enum class ExpenseStatus {
    ACTIVE,
    PARTIALLY_SETTLED,
    SETTLED
}
