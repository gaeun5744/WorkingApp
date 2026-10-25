package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class WalletResponse(
    val balance: Int,
    val transactions: List<WalletTransactionDto>,
)
