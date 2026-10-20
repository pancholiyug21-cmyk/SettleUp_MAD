package com.example.splitwsie.domain.model

data class Debt(
    val fromUserId: String,
    val toUserId: String,
    val amount: Double
)
