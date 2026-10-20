package com.example.splitwsie.domain.repository

import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.Comment
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getExpensesForGroup(groupId: String): Flow<List<Expense>>
    fun getAllExpensesForUser(userId: String): Flow<List<Expense>>
    suspend fun getExpenseById(expenseId: String): Expense?
    suspend fun addExpense(expense: Expense)
    suspend fun deleteExpense(expenseId: String)
    suspend fun deleteExpensesForGroup(groupId: String)

    /** Used to check whether a member can be removed without orphaning their splits. */
    suspend fun countSplitsForUser(userId: String): Int
    
    fun getCommentsForExpense(expenseId: String): Flow<List<Comment>>
    
    suspend fun addComment(expenseId: String, text: String, userId: String)
}
