package com.example.splitwsie.data.remote.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class FirestoreGroupMember(
    val uid: String = "",
    val role: String = "MEMBER",
    val displayName: String = "",
    @ServerTimestamp val joinedAt: Date? = null
)
