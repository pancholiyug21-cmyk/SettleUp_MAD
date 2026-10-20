package com.example.splitwsie.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val phoneNumber: String? = null,
    val upiId: String? = null,
    val bankAccount: String? = null,
    val defaultCurrency: String = "INR"
)
