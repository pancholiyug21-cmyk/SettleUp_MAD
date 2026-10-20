package com.example.splitwsie.domain.usecase

import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.ExpenseCategory
import com.example.splitwsie.domain.model.Split
import com.example.splitwsie.domain.model.SplitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date
import java.util.UUID

class ExpenseReceiptTest {

    @Test
    fun `expense created with no receipt has null receiptUrl`() {
        val expense = createExpense(receiptImageUrl = null)
        assertNull(expense.receiptImageUrl)
    }

    @Test
    fun `expense created with receipt has correct receiptUrl`() {
        val url = "https://firebasestorage.googleapis.com/v0/b/splitwsie.appspot.com/o/receipts%2Fgroup1%2Fexp1%2Ffile1.jpg"
        val expense = createExpense(receiptImageUrl = url)
        assertEquals(url, expense.receiptImageUrl)
    }

    @Test
    fun `expense edit preserving receipt keeps url`() {
        val originalUrl = "https://firebasestorage.googleapis.com/v0/b/example/old.jpg"
        val expense = createExpense(receiptImageUrl = originalUrl)

        val edited = expense.copy(amount = 500.0)
        assertEquals(originalUrl, edited.receiptImageUrl)
    }

    @Test
    fun `expense edit replacing receipt updates url`() {
        val originalUrl = "https://firebasestorage.googleapis.com/v0/b/example/old.jpg"
        val expense = createExpense(receiptImageUrl = originalUrl)

        val newUrl = "https://firebasestorage.googleapis.com/v0/b/example/new.jpg"
        val edited = expense.copy(receiptImageUrl = newUrl)
        assertEquals(newUrl, edited.receiptImageUrl)
    }

    @Test
    fun `expense edit removing receipt sets url to null`() {
        val originalUrl = "https://firebasestorage.googleapis.com/v0/b/example/old.jpg"
        val expense = createExpense(receiptImageUrl = originalUrl)

        val edited = expense.copy(receiptImageUrl = null)
        assertNull(edited.receiptImageUrl)
    }

    @Test
    fun `receipt reference mapping preserves url`() {
        // This validates that the domain model correctly holds the URL for Firestore mapping
        val url = "https://firebasestorage.googleapis.com/v0/b/example/receipt.jpg"
        val expense = createExpense(receiptImageUrl = url)

        assertEquals(url, expense.receiptImageUrl)
    }

    private fun createExpense(receiptImageUrl: String?): Expense {
        return Expense(
            id = UUID.randomUUID().toString(),
            groupId = "group1",
            title = "Dinner",
            amount = 100.0,
            category = ExpenseCategory.FOOD.name,
            date = Date(),
            paidById = "user1",
            splits = listOf(
                Split("user1", 50.0, SplitType.EQUAL),
                Split("user2", 50.0, SplitType.EQUAL)
            ),
            receiptImageUrl = receiptImageUrl
        )
    }
}
