package com.example.splitwsie.data.remote.model

data class FirestoreSplit(
    val userId: String = "",
    val amountOwedPaise: Long = 0L, // Minor currency units
    val splitType: String = "EQUAL",
    val percentage: Double? = null,
    val shares: Double? = null
)
