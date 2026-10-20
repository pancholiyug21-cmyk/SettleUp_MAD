package com.example.splitwsie.data.repository

import com.example.splitwsie.data.local.SettleDatabase
import com.example.splitwsie.data.local.dao.CommentDao
import com.example.splitwsie.data.local.dao.ExpenseDao
import com.example.splitwsie.data.local.entity.CommentEntity
import com.example.splitwsie.data.local.entity.ExpenseEntity
import com.example.splitwsie.data.local.entity.SplitEntity
import com.example.splitwsie.domain.model.Comment
import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.ExpenseStatus
import com.example.splitwsie.domain.model.RepeatInterval
import com.example.splitwsie.domain.model.Split
import com.example.splitwsie.domain.repository.ExpenseRepository
import com.example.splitwsie.domain.util.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID
import javax.inject.Inject

import com.example.splitwsie.data.remote.model.FirestoreExpense
import com.example.splitwsie.data.remote.model.FirestoreSplit
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val commentDao: CommentDao,
    private val database: SettleDatabase,
    private val firestore: FirebaseFirestore
) : ExpenseRepository {

    override fun getExpensesForGroup(groupId: String): Flow<List<Expense>> = callbackFlow {
        val roomJob = launch {
            expenseDao.getExpensesForGroup(groupId).map { entities ->
                if (entities.isEmpty()) return@map emptyList()
                val splitsByExpense = expenseDao.getSplitsForGroup(groupId).groupBy { it.expenseId }
                entities.map { entity -> entity.toDomain(splitsByExpense[entity.id].orEmpty()) }
            }.collect {
                send(it)
            }
        }

        val listener = firestore.collection("expenses")
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    launch {
                        snapshot.documentChanges.forEach { change ->
                            val doc = change.document

                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                expenseDao.deleteExpenseWithSplits(doc.id)
                                return@forEach
                            }

                            val firestoreExpense = doc.toObject(FirestoreExpense::class.java)

                            val expenseEntity = ExpenseEntity(
                                id = firestoreExpense.expenseId.ifEmpty { doc.id },
                                groupId = firestoreExpense.groupId,
                                title = firestoreExpense.title,
                                amount = firestoreExpense.amountPaise / 100.0,
                                category = firestoreExpense.category,
                                date = firestoreExpense.date,
                                paidById = firestoreExpense.paidById,
                                notes = firestoreExpense.notes,
                                receiptImageUrl = firestoreExpense.receiptImageUrl,
                                status = firestoreExpense.status,
                                repeatInterval = firestoreExpense.repeatInterval,
                                nextRepeatDate = firestoreExpense.nextRepeatDate
                            )

                            val splitEntities = firestoreExpense.splits.map { split ->
                                SplitEntity(
                                    expenseId = expenseEntity.id,
                                    userId = split.userId,
                                    amountOwed = split.amountOwedPaise / 100.0,
                                    splitType = runCatching { com.example.splitwsie.domain.model.SplitType.valueOf(split.splitType) }.getOrDefault(com.example.splitwsie.domain.model.SplitType.EQUAL),
                                    percentage = split.percentage,
                                    shares = split.shares
                                )
                            }

                            expenseDao.upsertExpenseWithSplits(expenseEntity, splitEntities)
                        }
                    }
                }
            }

        awaitClose {
            listener.remove()
            roomJob.cancel()
        }
    }

    override fun getAllExpensesForUser(userId: String): Flow<List<Expense>> {
        return expenseDao.getAllExpensesForUser(userId).map { entities ->
            if (entities.isEmpty()) return@map emptyList()
            val splitsByExpense = expenseDao.getSplitsForUser(userId).groupBy { it.expenseId }
            entities.map { entity -> entity.toDomain(splitsByExpense[entity.id].orEmpty()) }
        }
    }

    override suspend fun getExpenseById(expenseId: String): Expense? {
        val entity = expenseDao.getExpenseById(expenseId) ?: return null
        return entity.toDomain(expenseDao.getSplitsForExpense(expenseId))
    }

    override suspend fun addExpense(expense: Expense) {
        val isNew = expenseDao.getExpenseById(expense.id) == null

        expenseDao.upsertExpenseWithSplits(
            expense = expense.toEntity(),
            splits = expense.splits.map { it.toEntity(expense.id) }
        )

        // Sync to Firestore (fire and forget for offline queuing)
        val firestoreExpense = FirestoreExpense(
            expenseId = expense.id,
            groupId = expense.groupId ?: "",
            title = expense.title,
            amountPaise = (expense.amount * 100).toLong(),
            category = expense.category,
            date = expense.date,
            paidById = expense.paidById,
            notes = expense.notes,
            receiptImageUrl = if (expense.receiptImageUrl?.startsWith("content://") == true) null else expense.receiptImageUrl,
            status = expense.status.name,
            repeatInterval = expense.repeatInterval.name,
            nextRepeatDate = expense.nextRepeatDate,
            splits = expense.splits.map { split ->
                FirestoreSplit(
                    userId = split.userId,
                    amountOwedPaise = (split.amountOwed * 100).toLong(),
                    splitType = split.splitType.name,
                    percentage = split.percentage,
                    shares = split.shares
                )
            }
        )
        firestore.collection("expenses").document(expense.id).set(firestoreExpense)
            .addOnFailureListener { e ->
                // Handled natively by Firebase offline persistence.
                // It will queue the write and send it when reconnected.
            }
    }

    override suspend fun deleteExpense(expenseId: String) {
        expenseDao.deleteExpenseWithSplits(expenseId)

        // Sync to Firestore (fire and forget)
        firestore.collection("expenses").document(expenseId).delete()
            .addOnFailureListener {
                // Handled by Firebase offline persistence.
            }
    }

    override suspend fun deleteExpensesForGroup(groupId: String) {
        expenseDao.deleteSplitsForGroup(groupId)
        expenseDao.deleteExpensesForGroup(groupId)
    }

    override suspend fun countSplitsForUser(userId: String): Int {
        return expenseDao.countSplitsForUser(userId)
    }

    override fun getCommentsForExpense(expenseId: String): Flow<List<Comment>> {
        return commentDao.getCommentsForExpense(expenseId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addComment(expenseId: String, text: String, userId: String) {
        val entity = CommentEntity(
            id = UUID.randomUUID().toString(),
            expenseId = expenseId,
            userId = userId,
            text = text,
            timestamp = Date()
        )
        commentDao.insertComment(entity)
    }

    private fun ExpenseEntity.toDomain(splits: List<SplitEntity>) = Expense(
        id = id,
        groupId = groupId,
        title = title,
        amount = amount,
        category = category,
        date = date,
        paidById = paidById,
        notes = notes,
        receiptImageUrl = receiptImageUrl,
        splits = splits.map { it.toDomain() },
        // An unrecognised status must not crash the whole expense list; fall back to ACTIVE,
        // which is the only value that keeps the expense visible and countable.
        status = runCatching { ExpenseStatus.valueOf(status) }.getOrDefault(ExpenseStatus.ACTIVE),
        repeatInterval = runCatching { RepeatInterval.valueOf(repeatInterval) }.getOrDefault(RepeatInterval.NONE),
        nextRepeatDate = nextRepeatDate
    )

    private fun SplitEntity.toDomain() = Split(
        userId = userId,
        amountOwed = amountOwed,
        splitType = splitType,
        percentage = percentage,
        shares = shares
    )

    private fun Expense.toEntity() = ExpenseEntity(
        id = id,
        groupId = groupId,
        title = title,
        amount = Money.round(amount),
        category = category,
        date = date,
        paidById = paidById,
        notes = notes,
        receiptImageUrl = receiptImageUrl,
        status = status.name,
        repeatInterval = repeatInterval.name,
        nextRepeatDate = nextRepeatDate
    )

    private fun Split.toEntity(expenseId: String) = SplitEntity(
        expenseId = expenseId,
        userId = userId,
        amountOwed = Money.round(amountOwed),
        splitType = splitType,
        percentage = percentage,
        shares = shares
    )

    private fun CommentEntity.toDomain() = Comment(
        id = id,
        expenseId = expenseId,
        userId = userId,
        text = text,
        timestamp = timestamp
    )
}
