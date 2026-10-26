package com.example.workingapp.data.remote.dto

import com.example.workingapp.domain.model.Wallet
import com.example.workingapp.domain.model.WalletTransaction
import java.time.OffsetDateTime

fun WalletResponse.toDomain(): Wallet = Wallet(balance = balance, transactions = transactions.map { it.toDomain() })

fun WalletTransactionDto.toDomain(): WalletTransaction = WalletTransaction(
    id = id,
    amount = amount,
    rewardTier = reason.removePrefix(REWARD_TIER_PREFIX).takeIf { reason.startsWith(REWARD_TIER_PREFIX) }?.toIntOrNull(),
    createdAt = OffsetDateTime.parse(createdAt),
)

private const val REWARD_TIER_PREFIX = "REWARD_TIER_"
