package com.example.splitwsie.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.splitwsie.data.local.entity.FriendEntity
import com.example.splitwsie.data.local.entity.UserEntity

@Dao
interface FriendDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendRequest(friend: FriendEntity)

    @Update
    suspend fun updateFriendRequest(friend: FriendEntity)

    @Query("SELECT * FROM friends WHERE (requesterId = :userId1 AND receiverId = :userId2) OR (requesterId = :userId2 AND receiverId = :userId1) LIMIT 1")
    suspend fun getFriendRelationship(userId1: String, userId2: String): FriendEntity?

    @Query("SELECT * FROM friends WHERE receiverId = :userId AND status = 'PENDING'")
    suspend fun getPendingRequestsForUser(userId: String): List<FriendEntity>
    
    @Query("SELECT * FROM friends WHERE requesterId = :userId AND status = 'PENDING'")
    suspend fun getSentRequestsForUser(userId: String): List<FriendEntity>

    @Query("""
        SELECT u.* FROM users u 
        INNER JOIN friends f ON (u.id = f.receiverId OR u.id = f.requesterId) 
        WHERE (f.requesterId = :userId OR f.receiverId = :userId) 
        AND f.status = 'ACCEPTED' 
        AND u.id != :userId
    """)
    suspend fun getAcceptedFriendsForUser(userId: String): List<UserEntity>

    @Query("DELETE FROM friends WHERE (requesterId = :userId1 AND receiverId = :userId2) OR (requesterId = :userId2 AND receiverId = :userId1)")
    suspend fun deleteFriendRelationship(userId1: String, userId2: String)
}
