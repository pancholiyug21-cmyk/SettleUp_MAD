package com.example.splitwsie.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "comments",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("expenseId")
    ]
)
data class CommentEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val userId: String,
    val text: String,
    val timestamp: Date
)
