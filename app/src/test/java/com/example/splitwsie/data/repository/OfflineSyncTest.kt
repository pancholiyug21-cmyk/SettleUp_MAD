package com.example.splitwsie.data.repository

import com.example.splitwsie.data.local.SettleDatabase

import com.example.splitwsie.data.local.dao.CommentDao
import com.example.splitwsie.data.local.dao.ExpenseDao
import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.ExpenseCategory
import com.example.splitwsie.domain.model.Split
import com.example.splitwsie.domain.model.SplitType
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import java.util.Date
import java.util.UUID

class OfflineSyncTest {

    @Test
    fun `addExpense updates Room immediately without waiting for Firestore`() {
        runBlocking {
        val mockDao = mock(ExpenseDao::class.java)
        val mockCommentDao = mock(CommentDao::class.java)

        val mockDb = mock(SettleDatabase::class.java)
        val mockFirestore = mock(FirebaseFirestore::class.java)

        val mockCollection = mock(CollectionReference::class.java)
        val mockDoc = mock(DocumentReference::class.java)
        val mockTask = mock(Task::class.java) as Task<Void>

        `when`(mockFirestore.collection("expenses")).thenReturn(mockCollection)
        `when`(mockCollection.document(any())).thenReturn(mockDoc)
        `when`(mockDoc.set(any())).thenReturn(mockTask)

        val repo = ExpenseRepositoryImpl(
            mockDao, mockCommentDao, mockDb, mockFirestore
        )

        val expense = Expense(
            id = UUID.randomUUID().toString(),
            groupId = "group1",
            title = "Test Offline",
            amount = 100.0,
            category = ExpenseCategory.GENERAL.name,
            date = Date(),
            paidById = "user1",
            splits = listOf(Split("user1", 100.0, SplitType.EQUAL))
        )

        repo.addExpense(expense)

        // Verifies Room was updated immediately
        verify(mockDao).upsertExpenseWithSplits(any(), any())

        // Verifies Firestore was called but we do not await it
        verify(mockDoc).set(any())
        }
    }

    @Test
    fun `deleteExpense updates Room immediately and calls Firestore`() {
        runBlocking {
        val mockDao = mock(ExpenseDao::class.java)
        val mockCommentDao = mock(CommentDao::class.java)

        val mockDb = mock(SettleDatabase::class.java)
        val mockFirestore = mock(FirebaseFirestore::class.java)

        val mockCollection = mock(CollectionReference::class.java)
        val mockDoc = mock(DocumentReference::class.java)
        val mockTask = mock(Task::class.java) as Task<Void>

        `when`(mockFirestore.collection("expenses")).thenReturn(mockCollection)
        `when`(mockCollection.document("exp1")).thenReturn(mockDoc)
        `when`(mockDoc.delete()).thenReturn(mockTask)

        val repo = ExpenseRepositoryImpl(
            mockDao, mockCommentDao, mockDb, mockFirestore
        )

        repo.deleteExpense("exp1")

        // Verifies Room was updated immediately
        verify(mockDao).deleteExpenseWithSplits("exp1")
        // Verifies Firestore delete was called
        verify(mockDoc).delete()
        }
    }
}
