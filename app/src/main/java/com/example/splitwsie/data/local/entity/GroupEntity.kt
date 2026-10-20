package com.example.splitwsie.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val currency: String = "INR",
    val inviteCode: String? = null,
    val createdByUserId: String,
    val category: String = "Other",
    val iconUrl: String? = null,
    val simplifyDebts: Boolean = false
)
