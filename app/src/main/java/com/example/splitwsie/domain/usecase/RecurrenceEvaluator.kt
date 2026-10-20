package com.example.splitwsie.domain.usecase

import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.RepeatInterval
import com.example.splitwsie.domain.repository.ExpenseRepository
import com.example.splitwsie.domain.repository.UserRepository
import kotlinx.coroutines.flow.firstOrNull
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class RecurrenceEvaluator @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val userRepository: UserRepository
) {
    suspend fun evaluate() {
        val user = userRepository.getCurrentUser() ?: return
        
        // We only fetch once instead of collecting to avoid an infinite loop of new inserts
        val expenses = expenseRepository.getAllExpensesForUser(user.id).firstOrNull() ?: return
        
        val now = Date()
        
        // Find recurring expenses that are due
        val dueExpenses = expenses.filter { 
            it.repeatInterval != RepeatInterval.NONE && 
            it.nextRepeatDate != null && 
            it.nextRepeatDate.before(now)
        }
        
        for (expense in dueExpenses) {
            var currentDate = expense.nextRepeatDate!!
            var nextDate = expense.repeatInterval.calculateNextDate(currentDate)
            
            // Loop in case the app was unopened for a long time and multiple recurrences passed
            while (currentDate.before(now)) {
                // 1. Create a new expense instance based on this one
                val newExpense = expense.copy(
                    id = UUID.randomUUID().toString(),
                    date = currentDate,
                    // The new expense shouldn't automatically recur itself; it's just a generated instance.
                    // Only the ORIGINAL expense acts as the recurring template.
                    // But wait, Splitwise usually makes the LATEST expense the template.
                    // Let's make the new one the template, and turn off recurrence on the old one.
                    repeatInterval = expense.repeatInterval,
                    nextRepeatDate = nextDate
                )
                
                // Add the new expense
                expenseRepository.addExpense(newExpense)
                
                // Advance dates
                currentDate = nextDate ?: break
                if (currentDate.before(now)) {
                    nextDate = expense.repeatInterval.calculateNextDate(currentDate)
                }
            }
            
            // 2. Turn off recurrence on the OLD expense since the new one is now the active template
            val updatedOldExpense = expense.copy(
                repeatInterval = RepeatInterval.NONE,
                nextRepeatDate = null
            )
            expenseRepository.addExpense(updatedOldExpense)
        }
    }
}
