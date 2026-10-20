package com.example.splitwsie.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.splitwsie.data.local.entity.GroupEntity
import com.example.splitwsie.data.local.entity.MemberEntity
import com.example.splitwsie.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: MemberEntity)

    @Query("SELECT * FROM groups ORDER BY name ASC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    /**
     * Only the groups the user is actually a member of.
     *
     * The home screen previously listed every group in the database regardless of membership,
     * so switching profiles showed another person's groups.
     */
    @Query(
        """
        SELECT g.* FROM groups g
        INNER JOIN group_members gm ON g.id = gm.groupId
        WHERE gm.userId = :userId
        ORDER BY g.name ASC
        """
    )
    fun getGroupsForUser(userId: String): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE inviteCode = :inviteCode LIMIT 1")
    suspend fun getGroupByInviteCode(inviteCode: String): GroupEntity?

    @Query(
        """
        SELECT u.* FROM users u
        INNER JOIN group_members gm ON u.id = gm.userId
        WHERE gm.groupId = :groupId
        ORDER BY u.name ASC
        """
    )
    suspend fun getMembersOfGroup(groupId: String): List<UserEntity>

    /** Members of several groups at once, so a group list is not one query per group. */
    @Query(
        """
        SELECT gm.groupId AS groupId, u.* FROM users u
        INNER JOIN group_members gm ON u.id = gm.userId
        WHERE gm.groupId IN (:groupIds)
        ORDER BY u.name ASC
        """
    )
    suspend fun getMembersOfGroups(groupIds: List<String>): List<GroupMemberRow>

    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun countMembership(groupId: String, userId: String): Int

    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String): Int

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteMembersOfGroup(groupId: String): Int

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun deleteMember(groupId: String, userId: String): Int
}

/** Flat row used to fan group members out per group in a single query. */
data class GroupMemberRow(
    val groupId: String,
    val id: String,
    val name: String,
    val email: String,
    val phoneNumber: String?,
    val upiId: String?,
    val bankAccount: String?,
    val defaultCurrency: String
)
