package com.example.splitwsie.domain.model

import java.util.Date

data class CloudSettlement(
    val id: String,
    val payerId: String,
    val receiverId: String,
    val groupId: String?,
    val amount: Long, // in minor units
    val paymentId: String?,
    val status: PaymentStatus,
    val createdAt: Date,
    val completedAt: Date?
)
