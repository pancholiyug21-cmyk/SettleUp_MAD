package com.example.splitwsie.data.remote.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class FirestoreUser(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String? = null,
    val upiId: String? = null,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)
