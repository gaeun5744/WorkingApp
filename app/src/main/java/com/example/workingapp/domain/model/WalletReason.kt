package com.example.workingapp.domain.model

sealed interface WalletReason {
    data class RewardTier(val tier: Int) : WalletReason
    data object Treasure : WalletReason
    data object Other : WalletReason
}
