package com.example.workingapp.domain.model

import java.time.LocalDate

data class TodayRewards(
    val date: LocalDate,
    /** 서버에 저장된 오늘 걸음 — 기기 걸음보다 동기화 주기만큼 늦을 수 있다 */
    val serverSteps: Int,
    val tiers: List<RewardTier>,
)
