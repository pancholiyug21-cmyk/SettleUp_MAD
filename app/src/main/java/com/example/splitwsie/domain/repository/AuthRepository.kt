package com.example.splitwsie.domain.repository

import com.example.splitwsie.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /**
     * Attempts to sign in the user.
     * In a real implementation, this would handle Firebase Authentication.
     */
    suspend fun signIn(email: String, password: String): Result<String>

    /**
     * Signs up a new user.
     */
    suspend fun signUp(email: String, password: String): Result<String>

    /**
     * Signs out the current user.
     */
    suspend fun signOut(): Result<Unit>

    /**
     * Observes the current authenticated user's ID.
     */
    fun observeAuthState(): Flow<String?>

    /**
     * Gets the currently authenticated user's ID synchronously.
     */
    fun getCurrentUserId(): String?
}
