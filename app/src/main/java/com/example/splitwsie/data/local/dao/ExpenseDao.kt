package com.example.splitwsie.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.splitwsie.data.local.entity.ExpenseEntity
import com.example.splitwsie.data.local.entity.SplitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplits(splits: List<SplitEntity>)

    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY date DESC")
    fun getExpensesForGroup(groupId: String): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT DISTINCT e.* FROM expenses e 
        LEFT JOIN splits s ON e.id = s.expenseId 
        WHERE e.paidById = :userId OR s.userId = :userId 
        ORDER BY e.date DESC
        """
    )
    fun getAllExpensesForUser(userId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun getExpenseById(expenseId: String): ExpenseEntity?

    @Query("SELECT * FROM splits WHERE expenseId = :expenseId")
    suspend fun getSplitsForExpense(expenseId: String): List<SplitEntity>

    /**
     * Loads every split for a group in one query.
     *
     * Replaces a per-expense lookup that ran inside the Flow's map, which issued one query per
     * expense on every single emission -- so a group with 40 expenses did 41 queries each time
     * anything in the group changed.
     */
    @Query(
        """
        SELECT s.* FROM splits s
        INNER JOIN expenses e ON e.id = s.expenseId
        WHERE e.groupId = :groupId
        """
    )
    suspend fun getSplitsForGroup(groupId: String): List<SplitEntity>

    @Query(
        """
        SELECT s.* FROM splits s
        WHERE s.expenseId IN (
            SELECT DISTINCT e.id FROM expenses e 
            LEFT JOIN splits s2 ON e.id = s2.expenseId 
            WHERE e.paidById = :userId OR s2.userId = :userId
        )
        """
    )
    suspend fun getSplitsForUser(userId: String): List<SplitEntity>

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpense(expenseId: String): Int

    @Query("DELETE FROM splits WHERE expenseId = :expenseId")
    suspend fun deleteSplitsForExpense(expenseId: String): Int

    @Query("DELETE FROM expenses WHERE groupId = :groupId")
    suspend fun deleteExpensesForGroup(groupId: String): Int

    @Query(
        """
        DELETE FROM splits WHERE expenseId IN (SELECT id FROM expenses WHERE groupId = :groupId)
        """
    )
    suspend fun deleteSplitsForGroup(groupId: String): Int

    @Query("SELECT SUM(amount) FROM expenses WHERE groupId = :groupId")
    suspend fun getTotalExpensesForGroup(groupId: String): Double?

    @Query("SELECT COUNT(*) FROM splits WHERE userId = :userId")
    suspend fun countSplitsForUser(userId: String): Int

    /**
     * Writes an expense and its splits atomically.
     *
     * Without the transaction a crash between the two writes left an expense with no splits:
     * the payer was credited the full amount while nobody was debited, silently inventing
     * money in every balance that included it.
     */
    @Transaction
    suspend fun upsertExpenseWithSplits(expense: ExpenseEntity, splits: List<SplitEntity>) {
        deleteSplitsForExpense(expense.id)
        insertExpense(expense)
        if (splits.isNotEmpty()) insertSplits(splits)
    }

    @Transaction
    suspend fun deleteExpenseWithSplits(expenseId: String) {
        deleteSplitsForExpense(expenseId)
        deleteExpense(expenseId)
    }
}
