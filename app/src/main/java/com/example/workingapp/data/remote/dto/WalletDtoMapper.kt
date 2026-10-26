package com.example.workingapp.data.remote.dto

import com.example.workingapp.domain.model.Wallet
import com.example.workingapp.domain.model.WalletReason
import com.example.workingapp.domain.model.WalletTransaction
import java.time.OffsetDateTime

fun WalletResponse.toDomain(): Wallet = Wallet(balance = balance, transactions = transactions.map { it.toDomain() })

fun WalletTransactionDto.toDomain(): WalletTransaction = WalletTransaction(
    id = id,
    amount = amount,
    reason = reason.toWalletReason(),
    createdAt = OffsetDateTime.parse(createdAt),
)

private fun String.toWalletReason(): WalletReason = when {
    this == TREASURE -> WalletReason.Treasure
    startsWith(REWARD_TIER_PREFIX) -> removePrefix(REWARD_TIER_PREFIX).toIntOrNull()
        ?.let { WalletReason.RewardTier(it) } ?: WalletReason.Other
    else -> WalletReason.Other
}

private const val REWARD_TIER_PREFIX = "REWARD_TIER_"
private const val TREASURE = "TREASURE"
