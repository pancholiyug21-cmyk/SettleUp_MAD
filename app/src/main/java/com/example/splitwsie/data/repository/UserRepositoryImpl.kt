package com.example.splitwsie.data.repository

import com.example.splitwsie.data.local.SessionManager
import com.example.splitwsie.data.local.dao.UserDao
import com.example.splitwsie.data.local.entity.UserEntity
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.repository.UserRepository
import com.example.splitwsie.data.remote.model.FirestoreUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val sessionManager: SessionManager,
    private val firestore: FirebaseFirestore
) : UserRepository {

    override suspend fun getCurrentUser(): User? {
        val userId = sessionManager.currentUserId ?: return null
        return userDao.getUserById(userId)?.toDomain()
    }

    override suspend fun saveCurrentUser(user: User) {
        // Persist the row before the session points at it, so a crash in between can never
        // leave a session id referring to a user that does not exist.
        userDao.insertUser(user.toEntity())
        sessionManager.currentUserId = user.id
        sessionManager.currentUserName = user.name
        sessionManager.currentUserEmail = user.email

        // Sync to Firestore
        try {
            firestore.collection("users").document(user.id).set(user.toFirestoreUser()).await()
        } catch (e: Exception) {
            // In a production app, we would queue this for offline sync if it fails.
            // For now, we swallow the error so local functionality continues.
        }
    }

    override suspend fun getUserById(userId: String): User? =
        userDao.getUserById(userId)?.toDomain()

    override suspend fun getUsersByIds(userIds: List<String>): List<User> {
        if (userIds.isEmpty()) return emptyList()
        return userDao.getUsersByIds(userIds).map { it.toDomain() }
    }

    override suspend fun getAllUsers(): List<User> =
        userDao.getAllUsers().map { it.toDomain() }

    override suspend fun findByEmail(email: String): User? =
        userDao.findByEmail(email.trim()).let { it?.toDomain() }

    override suspend fun createUser(user: User) {
        userDao.insertUser(user.toEntity())
        try {
            firestore.collection("users").document(user.id).set(user.toFirestoreUser()).await()
        } catch (e: Exception) {
            // Ignore for offline support in this phase
        }
    }

    override suspend fun updateUser(user: User) {
        userDao.updateUser(user.toEntity())
        if (sessionManager.currentUserId == user.id) {
            sessionManager.currentUserName = user.name
            sessionManager.currentUserEmail = user.email
        }
        try {
            firestore.collection("users").document(user.id).update(
                mapOf(
                    "displayName" to user.name,
                    "email" to user.email,
                    "phoneNumber" to user.phoneNumber,
                    "upiId" to user.upiId,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            // Ignore for offline support
        }
    }

    private fun UserEntity.toDomain() = User(
        id = id,
        name = name,
        email = email,
        phoneNumber = phoneNumber,
        upiId = upiId,
        bankAccount = bankAccount,
        defaultCurrency = defaultCurrency
    )

    private fun User.toEntity() = UserEntity(
        id = id,
        name = name.trim(),
        email = email.trim().lowercase(),
        phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotBlank() },
        upiId = upiId?.trim()?.takeIf { it.isNotBlank() },
        bankAccount = bankAccount?.trim()?.takeIf { it.isNotBlank() },
        defaultCurrency = defaultCurrency
    )

    private fun User.toFirestoreUser() = FirestoreUser(
        uid = id,
        displayName = name.trim(),
        email = email.trim().lowercase(),
        phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotBlank() },
        upiId = upiId?.trim()?.takeIf { it.isNotBlank() }
    )
}
