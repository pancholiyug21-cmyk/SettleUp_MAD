package com.example.splitwsie.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.ExpenseCategory
import com.example.splitwsie.domain.repository.ExpenseRepository
import com.example.splitwsie.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class CategorySpending(
    val category: ExpenseCategory,
    val totalAmount: Double,
    val percentage: Float
)

data class MonthlySpending(
    val monthYear: String,
    val totalAmount: Double
)

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val totalSpentAllTime: Double = 0.0,
    val totalSpentThisMonth: Double = 0.0,
    val spendingByCategory: List<CategorySpending> = emptyList(),
    val spendingByMonth: List<MonthlySpending> = emptyList()
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState(isLoading = true))
    val uiState: StateFlow<AnalyticsUiState> = _uiState

    init {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            val userId = user?.id ?: return@launch
            
            expenseRepository.getAllExpensesForUser(userId).collect { expenses ->
                calculateAnalytics(userId, expenses)
            }
        }
    }

    private fun calculateAnalytics(userId: String, expenses: List<Expense>) {
        if (expenses.isEmpty()) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        var totalSpentAllTime = 0.0
        var totalSpentThisMonth = 0.0
        val categoryTotals = mutableMapOf<String, Double>()
        val monthlyTotals = mutableMapOf<String, Double>()

        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        for (expense in expenses) {
            // User's personal spending is their exact share in the expense
            val myShare = expense.splits.find { it.userId == userId }?.amountOwed ?: 0.0
            if (myShare == 0.0) continue

            totalSpentAllTime += myShare

            // Check if expense is in current month
            calendar.time = expense.date
            val expMonth = calendar.get(Calendar.MONTH)
            val expYear = calendar.get(Calendar.YEAR)

            if (expMonth == currentMonth && expYear == currentYear) {
                totalSpentThisMonth += myShare
            }

            // Category Breakdown
            categoryTotals[expense.category] = (categoryTotals[expense.category] ?: 0.0) + myShare

            // Monthly Breakdown (Format: "MMM yyyy")
            val monthName = getMonthName(expMonth)
            val monthKey = "$monthName $expYear"
            // Ensure sorting works by using a sortable key temporarily, or just sort at the end based on date
            // For simplicity, we can sort the final list later.
        }

        // Calculate Category Percentages
        val spendingByCategory = categoryTotals.mapNotNull { (categoryName, total) ->
            if (total > 0) {
                CategorySpending(
                    category = ExpenseCategory.fromName(categoryName),
                    totalAmount = total,
                    percentage = (total / totalSpentAllTime).toFloat()
                )
            } else null
        }.sortedByDescending { it.totalAmount }

        // Recalculate Monthly Breakdown over last 6 months properly
        // We'll iterate through expenses again and bucket them
        for (expense in expenses) {
            val myShare = expense.splits.find { it.userId == userId }?.amountOwed ?: 0.0
            if (myShare == 0.0) continue
            calendar.time = expense.date
            val key = String.format("%04d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
            monthlyTotals[key] = (monthlyTotals[key] ?: 0.0) + myShare
        }
        
        // Take last 6 months
        val sortedMonths = monthlyTotals.keys.sorted().takeLast(6)
        val spendingByMonth = sortedMonths.map { key ->
            val parts = key.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt() - 1
            MonthlySpending(
                monthYear = "${getMonthName(month)} $year",
                totalAmount = monthlyTotals[key] ?: 0.0
            )
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                totalSpentAllTime = totalSpentAllTime,
                totalSpentThisMonth = totalSpentThisMonth,
                spendingByCategory = spendingByCategory,
                spendingByMonth = spendingByMonth
            )
        }
    }

    private fun getMonthName(month: Int): String {
        return arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[month]
    }
}
