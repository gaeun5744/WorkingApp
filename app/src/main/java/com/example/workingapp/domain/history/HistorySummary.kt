package com.example.workingapp.domain.history

import com.example.workingapp.domain.model.DailySteps
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class HistorySummary(
    val totalSteps: Int,
    val averageSteps: Int,
    val achievedDays: Int,
) {
    companion object {
        /**
         * 일 평균은 기록이 없는 날(0걸음)까지 포함해, 기간 시작부터 오늘까지 지난 날수로 나눈다.
         * 기록이 있는 날만으로 나누면 걷지 않은 날이 평균에서 빠져 실제보다 높게 보인다.
         */
        fun from(days: List<DailySteps>, range: HistoryRange, today: LocalDate): HistorySummary {
            val inRange = days.filter { !it.date.isBefore(range.start) && !it.date.isAfter(range.end) }
            val total = inRange.sumOf { it.steps }
            val lastCountedDay = minOf(range.end, today)
            val elapsedDays = if (lastCountedDay.isBefore(range.start)) 0L else ChronoUnit.DAYS.between(range.start, lastCountedDay) + 1
            return HistorySummary(
                totalSteps = total,
                averageSteps = if (elapsedDays == 0L) 0 else (total / elapsedDays).toInt(),
                achievedDays = inRange.count { it.isGoalAchieved },
            )
        }
    }
}
