package com.example.splitwsie.data.remote.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class FirestoreExpense(
    val expenseId: String = "",
    val groupId: String = "",
    val title: String = "",
    val amountPaise: Long = 0L, // Minor currency units to avoid precision loss
    val category: String = "Other",
    val date: Date = Date(),
    val paidById: String = "",
    val notes: String? = null,
    val receiptImageUrl: String? = null,
    val status: String = "ACTIVE",
    val repeatInterval: String = "NONE",
    val nextRepeatDate: Date? = null,
    val splits: List<FirestoreSplit> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)
