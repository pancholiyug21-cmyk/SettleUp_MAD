package com.example.splitwsie.domain.model

data class Group(
    val id: String,
    val name: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val currency: String = "INR",
    val members: List<User> = emptyList(),
    val totalExpenses: Double = 0.0,
    /** Short shareable code used to join the group. */
    val inviteCode: String? = null,
    /** Who created the group. Only they may delete it or remove other members. */
    val createdByUserId: String? = null,
    val category: String = "Other",
    val simplifyDebts: Boolean = false
)
