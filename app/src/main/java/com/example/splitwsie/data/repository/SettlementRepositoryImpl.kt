package com.example.splitwsie.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.example.splitwsie.data.local.dao.SettlementDao
import com.example.splitwsie.data.local.entity.SettlementEntity
import com.example.splitwsie.data.remote.model.FirestoreSettlement
import com.example.splitwsie.domain.model.PaymentMethod
import com.example.splitwsie.domain.model.Settlement
import com.example.splitwsie.domain.model.SettlementStatus
import com.example.splitwsie.domain.repository.SettlementRepository
import com.example.splitwsie.domain.util.Money
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class SettlementRepositoryImpl @Inject constructor(
    private val settlementDao: SettlementDao,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions
) : SettlementRepository {

    override fun observeForGroup(groupId: String): Flow<List<Settlement>> = callbackFlow {
        val roomJob = launch {
            settlementDao.observeForGroup(groupId).map { list -> list.map { it.toDomain() } }.collect {
                send(it)
            }
        }

        val listener = firestore.collection("settlements")
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    launch {
                        snapshot.documentChanges.forEach { change ->
                            val doc = change.document

                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                settlementDao.deleteById(doc.id)
                                return@forEach
                            }

                            val firestoreSettlement = doc.toObject(FirestoreSettlement::class.java)
                            val entity = firestoreSettlement.toEntity(doc.id)
                            try {
                                settlementDao.insert(entity)
                            } catch (e: SQLiteConstraintException) {
                                settlementDao.update(entity)
                            }
                        }
                    }
                }
            }

        awaitClose {
            listener.remove()
            roomJob.cancel()
        }
    }

    override fun observeAll(): Flow<List<Settlement>> =
        settlementDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeForUser(userId: String): Flow<List<Settlement>> = callbackFlow {
        val roomJob = launch {
            settlementDao.observeForUser(userId).map { list -> list.map { it.toDomain() } }.collect {
                send(it)
            }
        }

        val listener = firestore.collection("settlements")
            .where(
                Filter.or(
                    Filter.equalTo("fromUserId", userId),
                    Filter.equalTo("toUserId", userId)
                )
            )
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    launch {
                        snapshot.documentChanges.forEach { change ->
                            val doc = change.document

                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                settlementDao.deleteById(doc.id)
                                return@forEach
                            }

                            val firestoreSettlement = doc.toObject(FirestoreSettlement::class.java)
                            val entity = firestoreSettlement.toEntity(doc.id)
                            try {
                                settlementDao.insert(entity)
                            } catch (e: SQLiteConstraintException) {
                                settlementDao.update(entity)
                            }
                        }
                    }
                }
            }

        awaitClose {
            listener.remove()
            roomJob.cancel()
        }
    }

    override suspend fun getForGroup(groupId: String): List<Settlement> =
        settlementDao.getForGroup(groupId).map { it.toDomain() }

    override suspend fun getById(id: String): Settlement? =
        settlementDao.getById(id)?.toDomain()

    override suspend fun initiate(
        groupId: String,
        fromUserId: String,
        toUserId: String,
        amount: Double,
        method: PaymentMethod,
        idempotencyKey: String,
        providerRefId: String?,
        expenseId: String?,
        note: String?
    ): Settlement {
        // Fast path: the key was already used, so this is a retry of the same logical payment.
        settlementDao.getByIdempotencyKey(idempotencyKey)?.let { return it.toDomain() }

        val now = Date()
        val entity = SettlementEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            fromUserId = fromUserId,
            toUserId = toUserId,
            amount = Money.round(amount),
            date = now,
            method = method.name,
            status = SettlementStatus.INITIATED.name,
            idempotencyKey = idempotencyKey,
            providerRefId = providerRefId,
            razorpayOrderId = null,
            expenseId = expenseId,
            note = note,
            verificationNotes = null,
            paymentId = null,
            updatedAt = now
        )

        return try {
            settlementDao.insert(entity)
            entity.toDomain()
        } catch (e: SQLiteConstraintException) {
            // Lost a race against a concurrent initiate() with the same key — reuse that row.
            settlementDao.getByIdempotencyKey(idempotencyKey)?.toDomain()
                ?: throw e
        }
    }

    override suspend fun updateStatus(
        settlementId: String,
        status: SettlementStatus,
        providerTxnId: String?,
        verificationNotes: String?,
        razorpayOrderId: String?,
        paymentId: String?
    ): Settlement? {
        val existing = settlementDao.getById(settlementId) ?: return null

        // A terminal settlement is never re-opened. This is what makes a duplicated callback
        // (the same success delivered twice) a no-op instead of a second balance change.
        if (SettlementStatus.valueOf(existing.status).isTerminal) {
            return existing.toDomain()
        }

        val updated = existing.copy(
            status = status.name,
            providerTxnId = providerTxnId ?: existing.providerTxnId,
            razorpayOrderId = razorpayOrderId ?: existing.razorpayOrderId,
            paymentId = paymentId ?: existing.paymentId,
            verificationNotes = verificationNotes ?: existing.verificationNotes,
            updatedAt = Date()
        )
        settlementDao.update(updated)



        return updated.toDomain()
    }

    override suspend fun getInFlight(
        groupId: String,
        fromUserId: String,
        toUserId: String
    ): List<Settlement> =
        settlementDao.getInFlight(groupId, fromUserId, toUserId).map { it.toDomain() }

    override suspend fun recordManualSettlement(
        settlementId: String,
        groupId: String,
        toUserId: String,
        amount: Double,
        method: PaymentMethod,
        providerTxnId: String?,
        note: String?
    ): Result<Unit> {
        return try {
            val requestData = hashMapOf(
                "settlementId" to settlementId,
                "groupId" to groupId,
                "receiverId" to toUserId,
                "amountPaise" to Money.toPaise(amount),
                "method" to method.name,
                "providerTxnId" to providerTxnId,
                "note" to note
            )

            val result = functions
                .getHttpsCallable("recordManualSettlement")
                .call(requestData)
                .await()

            val responseData = result.getData() as? Map<*, *>
            if (responseData != null && responseData["success"] == true) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Backend rejected manual settlement."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllInFlight(): List<Settlement> =
        settlementDao.getAllInFlight().map { it.toDomain() }

    override suspend fun deleteForGroup(groupId: String) {
        settlementDao.deleteForGroup(groupId)
    }

    private fun FirestoreSettlement.toEntity(docId: String) = SettlementEntity(
        id = settlementId.ifEmpty { docId },
        groupId = groupId,
        fromUserId = fromUserId,
        toUserId = toUserId,
        amount = amountPaise / 100.0,
        date = date,
        method = method,
        status = status,
        idempotencyKey = idempotencyKey,
        providerTxnId = providerTxnId,
        providerRefId = providerRefId,
        razorpayOrderId = razorpayOrderId,
        paymentId = paymentId,
        expenseId = expenseId,
        note = note,
        verificationNotes = verificationNotes,
        updatedAt = updatedAt ?: Date()
    )

    private fun SettlementEntity.toDomain() = Settlement(
        id = id,
        groupId = groupId,
        fromUserId = fromUserId,
        toUserId = toUserId,
        amount = amount,
        date = date,
        method = runCatching { PaymentMethod.valueOf(method) }.getOrDefault(PaymentMethod.CASH),
        status = runCatching { SettlementStatus.valueOf(status) }
            .getOrDefault(SettlementStatus.PENDING_VERIFICATION),
        idempotencyKey = idempotencyKey,
        providerTxnId = providerTxnId,
        providerRefId = providerRefId,
        razorpayOrderId = razorpayOrderId,
        paymentId = paymentId,
        expenseId = expenseId,
        note = note,
        verificationNotes = verificationNotes,
        updatedAt = updatedAt
    )
}
