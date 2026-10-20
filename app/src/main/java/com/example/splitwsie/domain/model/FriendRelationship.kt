package com.example.splitwsie.domain.model

data class FriendRelationship(
    val requesterId: String,
    val receiverId: String,
    val status: FriendStatus,
    val timestamp: Long
)
