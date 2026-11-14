package com.example.workingapp.domain.model

data class Wallet(
    val balance: Int,
    /** 최신순 */
    val transactions: List<WalletTransaction>,
)
