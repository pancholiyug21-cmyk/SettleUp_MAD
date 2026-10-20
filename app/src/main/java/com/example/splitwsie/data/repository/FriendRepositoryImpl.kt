package com.example.splitwsie.data.repository

import com.example.splitwsie.data.local.dao.FriendDao
import com.example.splitwsie.data.local.entity.FriendEntity
import com.example.splitwsie.data.local.entity.UserEntity
import com.example.splitwsie.domain.model.FriendRelationship
import com.example.splitwsie.domain.model.FriendStatus
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.repository.FriendRepository
import javax.inject.Inject

class FriendRepositoryImpl @Inject constructor(
    private val friendDao: FriendDao
) : FriendRepository {

    override suspend fun sendFriendRequest(requesterId: String, receiverId: String) {
        val existing = friendDao.getFriendRelationship(requesterId, receiverId)
        if (existing == null) {
            friendDao.insertFriendRequest(
                FriendEntity(
                    requesterId = requesterId,
                    receiverId = receiverId,
                    status = FriendStatus.PENDING.name,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun acceptFriendRequest(requesterId: String, receiverId: String) {
        val existing = friendDao.getFriendRelationship(requesterId, receiverId)
        if (existing != null && existing.status == FriendStatus.PENDING.name) {
            friendDao.updateFriendRequest(existing.copy(status = FriendStatus.ACCEPTED.name))
        }
    }

    override suspend fun rejectFriendRequest(requesterId: String, receiverId: String) {
        val existing = friendDao.getFriendRelationship(requesterId, receiverId)
        if (existing != null && existing.status == FriendStatus.PENDING.name) {
            friendDao.updateFriendRequest(existing.copy(status = FriendStatus.REJECTED.name))
        }
    }

    override suspend fun cancelFriendRequest(requesterId: String, receiverId: String) {
        friendDao.deleteFriendRelationship(requesterId, receiverId)
    }

    override suspend fun removeFriend(userId1: String, userId2: String) {
        friendDao.deleteFriendRelationship(userId1, userId2)
    }

    override suspend fun getPendingRequestsForUser(userId: String): List<FriendRelationship> {
        return friendDao.getPendingRequestsForUser(userId).map { it.toDomain() }
    }

    override suspend fun getSentRequestsForUser(userId: String): List<FriendRelationship> {
        return friendDao.getSentRequestsForUser(userId).map { it.toDomain() }
    }

    override suspend fun getAcceptedFriendsForUser(userId: String): List<User> {
        return friendDao.getAcceptedFriendsForUser(userId).map { it.toDomain() }
    }

    override suspend fun getFriendRelationship(userId1: String, userId2: String): FriendRelationship? {
        return friendDao.getFriendRelationship(userId1, userId2)?.toDomain()
    }

    private fun FriendEntity.toDomain() = FriendRelationship(
        requesterId = requesterId,
        receiverId = receiverId,
        status = FriendStatus.valueOf(status),
        timestamp = timestamp
    )

    private fun UserEntity.toDomain() = User(
        id = id,
        name = name,
        email = email,
        phoneNumber = phoneNumber,
        upiId = upiId,
        bankAccount = bankAccount,
        defaultCurrency = defaultCurrency
    )
}
