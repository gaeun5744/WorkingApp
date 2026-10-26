package com.example.workingapp.domain.model

/** [status] 는 서버 판정 (서버에 저장된 걸음 기준). */
data class RewardTier(
    val tier: Int,
    val requiredSteps: Int,
    val points: Int,
    val status: RewardStatus,
)
