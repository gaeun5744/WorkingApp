package com.example.workingapp.data.remote.mock

import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.abs

/**
 * 랭킹을 채우기 위한 가짜 이웃.
 * 같은 (이웃, 날짜)에는 항상 같은 하루 목표치를 만들고, 오늘은 시각에 따라 점점 늘어나게 해서
 * 프로세스를 재시작해도 랭킹이 자연스럽게 보이도록 한다.
 */
internal class MockNeighborSteps @Inject constructor() {

    fun neighborsOf(regionId: Long): List<Neighbor> =
        NICKNAMES.mapIndexed { index, nickname ->
            Neighbor(userId = "n_${regionId}_$index", nickname = nickname, regionId = regionId)
        }

    fun stepsOn(userId: String, date: LocalDate, now: LocalDateTime): Int {
        val today = now.toLocalDate()
        if (date.isAfter(today)) return 0
        val dailyTotal = MIN_DAILY + abs("$userId:$date".hashCode()) % (MAX_DAILY - MIN_DAILY)
        if (date.isBefore(today)) return dailyTotal
        return (dailyTotal * dayProgress(now)).toInt()
    }

    // 06시~22시 사이에 걸음이 고르게 쌓인다고 가정
    private fun dayProgress(now: LocalDateTime): Double {
        val minutes = now.hour * 60 + now.minute
        return ((minutes - WALK_START_MINUTES).toDouble() / WALK_DURATION_MINUTES).coerceIn(0.0, 1.0)
    }

    data class Neighbor(val userId: String, val nickname: String, val regionId: Long)

    private companion object {
        const val MIN_DAILY = 2_000
        const val MAX_DAILY = 18_000
        const val WALK_START_MINUTES = 6 * 60
        const val WALK_DURATION_MINUTES = 16 * 60
        val NICKNAMES = listOf(
            "산책왕", "아침러너", "퇴근길워커", "강아지와함께", "만보도전",
            "느긋한거북", "계단매니아", "공원지킴이", "출근은걸어서", "주말등산러",
            "밤산책", "커피한잔걸음", "동네한바퀴", "건강제일", "걷는게좋아",
            "오늘도걷자", "발바닥튼튼", "하루만보", "골목탐험가", "바람쐬기",
        )
    }
}
