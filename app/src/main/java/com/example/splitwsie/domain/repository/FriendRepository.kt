package com.example.splitwsie.domain.repository

import com.example.splitwsie.domain.model.FriendRelationship
import com.example.splitwsie.domain.model.User
import kotlinx.coroutines.flow.Flow

interface FriendRepository {
    suspend fun sendFriendRequest(requesterId: String, receiverId: String)
    suspend fun acceptFriendRequest(requesterId: String, receiverId: String)
    suspend fun rejectFriendRequest(requesterId: String, receiverId: String)
    suspend fun cancelFriendRequest(requesterId: String, receiverId: String)
    suspend fun removeFriend(userId1: String, userId2: String)
    suspend fun getPendingRequestsForUser(userId: String): List<FriendRelationship>
    suspend fun getSentRequestsForUser(userId: String): List<FriendRelationship>
    suspend fun getAcceptedFriendsForUser(userId: String): List<User>
    suspend fun getFriendRelationship(userId1: String, userId2: String): FriendRelationship?
}
