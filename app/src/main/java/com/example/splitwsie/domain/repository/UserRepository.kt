package com.example.splitwsie.domain.repository

import com.example.splitwsie.domain.model.User

interface UserRepository {
    suspend fun getCurrentUser(): User?
    suspend fun saveCurrentUser(user: User)
    suspend fun getUserById(userId: String): User?
    suspend fun getUsersByIds(userIds: List<String>): List<User>
    suspend fun getAllUsers(): List<User>
    suspend fun findByEmail(email: String): User?
    suspend fun createUser(user: User)
    suspend fun updateUser(user: User)
}
