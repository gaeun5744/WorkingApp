package com.example.workingapp.domain.model

import java.time.LocalDate

/** 요청한 위치 주변 3×3 칸의 오늘 보물상자 */
data class NearbyTreasures(
    val date: LocalDate,
    /** 이 거리 안에서만 열 수 있다 (서버가 다시 검증) */
    val openRadiusMeters: Int,
    val dailyOpenLimit: Int,
    val openedToday: Int,
    val treasures: List<Treasure>,
)
