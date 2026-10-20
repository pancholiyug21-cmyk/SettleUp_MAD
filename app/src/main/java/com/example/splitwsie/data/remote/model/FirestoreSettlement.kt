package com.example.splitwsie.data.remote.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class FirestoreSettlement(
    val settlementId: String = "",
    val groupId: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val amountPaise: Long = 0L,
    val date: Date = Date(),
    val method: String = "RAZORPAY",
    val status: String = "VERIFIED",
    val idempotencyKey: String = "",
    val providerTxnId: String? = null,
    val providerRefId: String? = null,
    val razorpayOrderId: String? = null,
    val paymentId: String? = null,
    val expenseId: String? = null,
    val note: String? = null,
    val verificationNotes: String? = null,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)
