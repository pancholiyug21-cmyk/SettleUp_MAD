package com.example.splitwsie.domain.model

import java.util.Date

data class Comment(
    val id: String,
    val expenseId: String,
    val userId: String,
    val text: String,
    val timestamp: Date
)
