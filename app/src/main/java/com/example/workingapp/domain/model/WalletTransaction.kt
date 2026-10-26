package com.example.workingapp.domain.model

import java.time.OffsetDateTime

data class WalletTransaction(
    val id: String,
    val amount: Int,
    val reason: WalletReason,
    val createdAt: OffsetDateTime,
)
