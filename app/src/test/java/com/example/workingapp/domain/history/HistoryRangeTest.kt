package com.example.workingapp.domain.history

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HistoryRangeTest {

    // 2026-10-25 은 일요일
    private val sunday = LocalDate.of(2026, 10, 25)

    @Test
    fun `일간은 그날 하루다`() {
        assertEquals(HistoryRange(sunday, sunday), HistoryRange.of(HistoryPeriod.DAY, sunday))
    }

    @Test
    fun `주간은 월요일부터 일요일까지다`() {
        val range = HistoryRange.of(HistoryPeriod.WEEK, sunday)

        assertEquals(LocalDate.of(2026, 10, 19), range.start)
        assertEquals(LocalDate.of(2026, 10, 25), range.end)
    }

    @Test
    fun `월간은 1일부터 말일까지다`() {
        val range = HistoryRange.of(HistoryPeriod.MONTH, LocalDate.of(2028, 2, 10))

        assertEquals(LocalDate.of(2028, 2, 1), range.start)
        assertEquals(LocalDate.of(2028, 2, 29), range.end)
    }

    @Test
    fun `기간 단위로 앞뒤로 이동한다`() {
        assertEquals(LocalDate.of(2026, 10, 24), HistoryPeriod.DAY.shift(sunday, -1))
        assertEquals(LocalDate.of(2026, 10, 18), HistoryPeriod.WEEK.shift(sunday, -1))
        assertEquals(LocalDate.of(2026, 9, 25), HistoryPeriod.MONTH.shift(sunday, -1))
    }

    @Test
    fun `오늘이 포함된 기간이면 다음 기간이 없다`() {
        val today = sunday
        assertEquals(false, HistoryRange.of(HistoryPeriod.WEEK, today).hasNext(today))
        assertEquals(true, HistoryRange.of(HistoryPeriod.WEEK, today.minusWeeks(1)).hasNext(today))
    }
}
