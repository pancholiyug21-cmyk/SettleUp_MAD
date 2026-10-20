package com.example.splitwsie.domain.model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val phoneNumber: String? = null,
    val profilePhotoUrl: String? = null,
    val upiId: String? = null,
    val bankAccount: String? = null,
    val defaultCurrency: String = "INR"
)
