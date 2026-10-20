package com.example.splitwsie.presentation.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitwsie.data.local.SessionManager
import com.example.splitwsie.domain.model.Comment
import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.repository.ExpenseRepository
import com.example.splitwsie.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExpenseDetailsUiState(
    val isLoading: Boolean = true,
    val expense: Expense? = null,
    val groupMembers: List<User> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val currentUserId: String = "",
    val error: String? = null
)

@HiltViewModel
class ExpenseDetailsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val groupRepository: GroupRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseDetailsUiState())
    val uiState: StateFlow<ExpenseDetailsUiState> = _uiState.asStateFlow()

    fun loadExpense(expenseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val currentUserId = sessionManager.currentUserId ?: return@launch
            
            try {
                val expense = expenseRepository.getExpenseById(expenseId)
                if (expense == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Expense not found") }
                    return@launch
                }

                val members = if (expense.groupId != null) {
                    val group = groupRepository.getGroupById(expense.groupId)
                    group?.members ?: emptyList()
                } else emptyList()

                _uiState.update { 
                    it.copy(
                        expense = expense, 
                        groupMembers = members,
                        currentUserId = currentUserId,
                        isLoading = false
                    ) 
                }

                expenseRepository.getCommentsForExpense(expenseId).collect { comments ->
                    _uiState.update { it.copy(comments = comments) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun addComment(expenseId: String, text: String) {
        if (text.isBlank()) return
        val currentUserId = sessionManager.currentUserId ?: return
        viewModelScope.launch {
            try {
                expenseRepository.addComment(expenseId, text, currentUserId)
            } catch (e: Exception) {
                // Ignore for now
            }
        }
    }
}
