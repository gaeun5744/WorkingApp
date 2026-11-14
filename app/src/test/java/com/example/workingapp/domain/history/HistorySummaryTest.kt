package com.example.workingapp.domain.history

import com.example.workingapp.domain.model.DailySteps
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HistorySummaryTest {

    private val monday = LocalDate.of(2026, 10, 19)
    private val week = HistoryRange.of(HistoryPeriod.WEEK, monday)

    @Test
    fun `합계와 달성 일수는 그날의 목표로 판정한다`() {
        val days = listOf(
            DailySteps(monday, steps = 9_000, goal = 8_000), // 달성
            DailySteps(monday.plusDays(1), steps = 9_000, goal = 10_000), // 미달 (목표를 올린 날)
            DailySteps(monday.plusDays(2), steps = 12_000, goal = 10_000), // 달성
        )
        val summary = HistorySummary.from(days, week, today = monday.plusDays(6))

        assertEquals(30_000, summary.totalSteps)
        assertEquals(2, summary.achievedDays)
    }

    @Test
    fun `일 평균은 기록이 없는 날까지 포함해 오늘까지 지난 날수로 나눈다`() {
        val days = listOf(DailySteps(monday, steps = 7_000, goal = 10_000))
        // 수요일 기준: 월·화·수 3일
        val summary = HistorySummary.from(days, week, today = monday.plusDays(2))

        assertEquals(2_333, summary.averageSteps)
    }

    @Test
    fun `아직 시작하지 않은 기간은 평균이 0이다`() {
        val summary = HistorySummary.from(emptyList(), week, today = monday.minusDays(1))

        assertEquals(0, summary.averageSteps)
    }
}
