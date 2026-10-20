package com.example.splitwsie.presentation.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object Auth : Screen("auth")
    data object Home : Screen("home")
    data object Profile : Screen("profile")
    data object CreateGroup : Screen("create_group")
    data object SettleUp : Screen("settle_up")
    data object PaymentHistory : Screen("payment_history")
    data object Friends : Screen("friends")
    data object Analytics : Screen("analytics")

    data object GroupDetails : Screen("group_details/{groupId}") {
        fun createRoute(groupId: String) = "group_details/${groupId.encode()}"
    }

    data object GroupSettings : Screen("group_settings/{groupId}") {
        fun createRoute(groupId: String) = "group_settings/${groupId.encode()}"
    }

    data object ExpenseDetails : Screen("expense_details/{expenseId}") {
        fun createRoute(expenseId: String) = "expense_details/${expenseId.encode()}"
    }

    data object AddExpense : Screen("add_expense?groupId={groupId}&friendId={friendId}&expenseId={expenseId}") {
        fun createRoute(groupId: String? = null, friendId: String? = null, expenseId: String? = null) = buildString {
            append("add_expense")
            val params = mutableListOf<String>()
            if (groupId != null) params.add("groupId=${groupId.encode()}")
            if (friendId != null) params.add("friendId=${friendId.encode()}")
            if (expenseId != null) params.add("expenseId=${expenseId.encode()}")
            if (params.isNotEmpty()) {
                append("?")
                append(params.joinToString("&"))
            }
        }
    }

    /**
     * The payment route deliberately carries no amount and no payee name.
     *
     * Both are read from the database by the ViewModel instead, so the amount is always the
     * live outstanding balance. Previously the amount travelled through the route as a Float,
     * which lost precision on large values and let a stale back-stack entry settle an amount
     * that no longer matched the debt.
     */
    data object Payment : Screen("payment/{groupId}/{toUserId}?expenseId={expenseId}") {
        fun createRoute(groupId: String, toUserId: String, expenseId: String? = null) = buildString {
            append("payment/${groupId.encode()}/${toUserId.encode()}")
            if (expenseId != null) append("?expenseId=${expenseId.encode()}")
        }
    }
}

/**
 * Percent-encodes a value before it is spliced into a route.
 *
 * Ids are UUIDs today, but any user-supplied value containing '/', '?' or '&' would otherwise
 * silently corrupt the route and fail to match its destination.
 */
private fun String.encode(): String = Uri.encode(this)
