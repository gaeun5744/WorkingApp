package com.example.workingapp.domain.model

import java.time.OffsetDateTime

data class WalletTransaction(
    val id: String,
    val amount: Int,
    /** 리워드 구간 적립이면 구간 번호, 그 외 사유면 null */
    val rewardTier: Int?,
    val createdAt: OffsetDateTime,
)
