package com.example.splitwsie.data.remote.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class FirestoreGroup(
    val groupId: String = "",
    val name: String = "",
    val description: String? = null,
    val currency: String = "INR",
    val inviteCode: String? = null,
    val createdByUserId: String = "",
    val category: String = "Other",
    val iconUrl: String? = null,
    val simplifyDebts: Boolean = false,
    val memberIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)
