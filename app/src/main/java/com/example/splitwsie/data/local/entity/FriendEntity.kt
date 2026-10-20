package com.example.splitwsie.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "friends",
    primaryKeys = ["requesterId", "receiverId"],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["requesterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiverId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("requesterId"),
        Index("receiverId")
    ]
)
data class FriendEntity(
    val requesterId: String,
    val receiverId: String,
    val status: String, // PENDING, ACCEPTED, REJECTED
    val timestamp: Long
)
