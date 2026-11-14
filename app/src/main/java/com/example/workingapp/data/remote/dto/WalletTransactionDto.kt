package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class WalletTransactionDto(
    val id: String,
    val amount: Int,
    val reason: String,
    val createdAt: String,
)
