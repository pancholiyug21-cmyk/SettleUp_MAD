package com.example.splitwsie.domain.repository

import com.example.splitwsie.domain.model.PaymentMethod
import com.example.splitwsie.domain.model.Settlement
import com.example.splitwsie.domain.model.SettlementStatus
import kotlinx.coroutines.flow.Flow

interface SettlementRepository {

    fun observeForGroup(groupId: String): Flow<List<Settlement>>

    fun observeAll(): Flow<List<Settlement>>

    fun observeForUser(userId: String): Flow<List<Settlement>>

    suspend fun getForGroup(groupId: String): List<Settlement>

    suspend fun getById(id: String): Settlement?

    /**
     * Creates (or returns the existing) settlement for [idempotencyKey].
     *
     * Re-calling with the same key after a lost callback returns the original row instead of
     * creating a second payment, which is what makes retries safe.
     */
    suspend fun initiate(
        groupId: String,
        fromUserId: String,
        toUserId: String,
        amount: Double,
        method: PaymentMethod,
        idempotencyKey: String,
        providerRefId: String?,
        expenseId: String?,
        note: String?
    ): Settlement

    suspend fun updateStatus(
        settlementId: String,
        status: SettlementStatus,
        providerTxnId: String? = null,
        verificationNotes: String? = null,
        razorpayOrderId: String? = null,
        paymentId: String? = null
    ): Settlement?

    /** Sends a manual settlement intent to the backend. */
    suspend fun recordManualSettlement(
        settlementId: String,
        groupId: String,
        toUserId: String,
        amount: Double,
        method: PaymentMethod,
        providerTxnId: String?,
        note: String?
    ): Result<Unit>

    /** Payments left hanging by a crash / process death, so the user can resolve them. */
    suspend fun getInFlight(groupId: String, fromUserId: String, toUserId: String): List<Settlement>

    suspend fun getAllInFlight(): List<Settlement>

    suspend fun deleteForGroup(groupId: String)
}
