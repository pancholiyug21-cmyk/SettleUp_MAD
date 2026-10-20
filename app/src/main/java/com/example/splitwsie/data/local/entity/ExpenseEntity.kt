package com.example.splitwsie.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["groupId"])]
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val groupId: String?,
    val title: String,
    val amount: Double,
    val category: String,
    val date: Date,
    val paidById: String,
    val notes: String? = null,
    val receiptImageUrl: String? = null,
    val status: String = "ACTIVE",
    val repeatInterval: String = "NONE",
    val nextRepeatDate: Date? = null
)
