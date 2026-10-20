package com.example.splitwsie.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Persisted payment record. The unique index on [idempotencyKey] is what makes
 * duplicate-payment prevention enforceable at the storage layer rather than in UI code.
 */
@Entity(
    tableName = "settlements",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["groupId"]),
        Index(value = ["fromUserId"]),
        Index(value = ["toUserId"])
    ]
)
data class SettlementEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val fromUserId: String,
    val toUserId: String,
    val amount: Double,
    val date: Date,
    val method: String,
    val status: String,
    val idempotencyKey: String,
    val providerTxnId: String? = null,
    val providerRefId: String? = null,
    val razorpayOrderId: String? = null,
    val expenseId: String? = null,
    val note: String? = null,
    val verificationNotes: String? = null,
    val paymentId: String? = null,
    val updatedAt: Date
)
