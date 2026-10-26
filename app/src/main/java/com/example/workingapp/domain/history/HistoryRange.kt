package com.example.workingapp.domain.history

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 기록 화면이 보여주는 기간 (양 끝 포함). 주는 월요일 시작 (PRD Q11 과 같은 기준). */
data class HistoryRange(
    val start: LocalDate,
    val end: LocalDate,
) {
    val days: List<LocalDate> get() = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()

    /** 오늘이 이 기간 안에 있으면 미래 기간으로는 이동할 수 없다. */
    fun hasNext(today: LocalDate): Boolean = end.isBefore(today)

    companion object {
        fun of(period: HistoryPeriod, anchor: LocalDate): HistoryRange = when (period) {
            HistoryPeriod.DAY -> HistoryRange(anchor, anchor)
            HistoryPeriod.WEEK -> {
                val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                HistoryRange(monday, monday.plusDays(6))
            }
            HistoryPeriod.MONTH -> HistoryRange(
                anchor.with(TemporalAdjusters.firstDayOfMonth()),
                anchor.with(TemporalAdjusters.lastDayOfMonth()),
            )
        }
    }
}
