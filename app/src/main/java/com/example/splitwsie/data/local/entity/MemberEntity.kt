package com.example.splitwsie.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Join table linking users to groups (many-to-many).
 */
@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "userId"],
    indices = [Index(value = ["userId"])]
)
data class MemberEntity(
    val groupId: String,
    val userId: String
)
