package com.example.splitwsie.data.local.dao

import androidx.room.*
import com.example.splitwsie.data.local.entity.SettlementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettlementDao {

    /**
     * ABORT (not REPLACE) so that a duplicate idempotency key fails loudly instead of
     * silently overwriting an existing payment record.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(settlement: SettlementEntity)

    @Update
    suspend fun update(settlement: SettlementEntity)

    @Query("SELECT * FROM settlements WHERE id = :id")
    suspend fun getById(id: String): SettlementEntity?

    @Query("SELECT * FROM settlements WHERE idempotencyKey = :key")
    suspend fun getByIdempotencyKey(key: String): SettlementEntity?

    @Query("SELECT * FROM settlements WHERE groupId = :groupId ORDER BY date DESC")
    fun observeForGroup(groupId: String): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements ORDER BY date DESC")
    fun observeAll(): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements WHERE groupId = :groupId")
    suspend fun getForGroup(groupId: String): List<SettlementEntity>

    @Query(
        """
        SELECT * FROM settlements
        WHERE (fromUserId = :userId OR toUserId = :userId)
        ORDER BY date DESC
        """
    )
    fun observeForUser(userId: String): Flow<List<SettlementEntity>>

    /** In-flight payments block a second attempt at the same debt. */
    @Query(
        """
        SELECT * FROM settlements
        WHERE groupId = :groupId AND fromUserId = :fromUserId AND toUserId = :toUserId
          AND status IN ('INITIATED', 'PENDING_VERIFICATION')
        ORDER BY date DESC
        """
    )
    suspend fun getInFlight(groupId: String, fromUserId: String, toUserId: String): List<SettlementEntity>

    @Query("SELECT * FROM settlements WHERE status IN ('INITIATED', 'PENDING_VERIFICATION') ORDER BY date DESC")
    suspend fun getAllInFlight(): List<SettlementEntity>

    @Query("DELETE FROM settlements WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String): Int

    @Query("DELETE FROM settlements WHERE id = :id")
    suspend fun deleteById(id: String)
}
