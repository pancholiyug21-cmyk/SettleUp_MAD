package com.example.splitwsie.domain.repository

import com.example.splitwsie.domain.model.Group
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun getAllGroups(): Flow<List<Group>>

    /** Groups the given user actually belongs to. */
    fun getGroupsForUser(userId: String): Flow<List<Group>>

    suspend fun getGroupById(groupId: String): Group?

    /** Resolves a shareable invite code to a group. */
    suspend fun getGroupByInviteCode(inviteCode: String): Group?

    suspend fun createGroup(group: Group)
    suspend fun updateGroup(group: Group)
    suspend fun addMemberToGroup(groupId: String, userId: String)
    suspend fun removeMemberFromGroup(groupId: String, userId: String)
    suspend fun isMember(groupId: String, userId: String): Boolean
    suspend fun deleteGroup(groupId: String)
}
