package com.example.splitwsie.domain.model

import java.util.Date

data class ActivityItem(
    val id: String,
    val timestamp: Date,
    val type: ActivityType,
    val userId: String,
    val targetUserId: String? = null,
    val groupId: String? = null,
    val expenseId: String? = null,
    val settlementId: String? = null,
    val amount: Double? = null,
    val title: String? = null
)

enum class ActivityType {
    EXPENSE_ADDED,
    EXPENSE_EDITED,
    SETTLED,
    GROUP_CREATED,
    JOINED_GROUP;

    companion object {
        fun fromName(value: String): ActivityType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EXPENSE_ADDED
    }
}

fun Expense.toActivityItem(): ActivityItem = ActivityItem(
    id = "act_exp_${id}",
    timestamp = date,
    type = ActivityType.EXPENSE_ADDED,
    userId = paidById,
    groupId = groupId,
    expenseId = id,
    amount = amount,
    title = title
)

fun Settlement.toActivityItem(): ActivityItem = ActivityItem(
    id = "act_set_${id}",
    timestamp = updatedAt.takeIf { it.time > 0 } ?: date,
    type = ActivityType.SETTLED,
    userId = fromUserId,
    targetUserId = toUserId,
    groupId = groupId,
    settlementId = id,
    amount = amount
)
