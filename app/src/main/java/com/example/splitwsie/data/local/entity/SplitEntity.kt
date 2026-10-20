package com.example.splitwsie.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.splitwsie.domain.model.SplitType

@Entity(
    tableName = "splits",
    indices = [Index(value = ["expenseId"])]
)
data class SplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: String,
    val userId: String,
    val amountOwed: Double,
    val splitType: SplitType,
    val percentage: Double? = null,
    val shares: Double? = null
)
