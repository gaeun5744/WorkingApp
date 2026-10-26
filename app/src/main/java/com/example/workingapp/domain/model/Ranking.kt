package com.example.workingapp.domain.model

import java.time.Instant
import java.time.LocalDate

data class Ranking(
    val period: RankingPeriod,
    /** daily = 오늘, weekly = 이번 주 월요일 (KST) */
    val periodStart: LocalDate,
    val regionName: String,
    /** 상위 50명 */
    val entries: List<RankingEntry>,
    val me: RankingEntry,
    /** 서버에서 받아 온 시각 (캐시 기준 시각 표시용) */
    val fetchedAt: Instant,
)
