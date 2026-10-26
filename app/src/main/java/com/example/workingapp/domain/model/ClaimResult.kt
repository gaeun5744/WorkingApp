package com.example.workingapp.domain.model

data class ClaimResult(
    val tier: Int,
    val points: Int,
    val balance: Int,
    /** 이미 받은 구간을 다시 요청한 경우 (포인트가 더 적립되지 않음) */
    val alreadyClaimed: Boolean,
)
