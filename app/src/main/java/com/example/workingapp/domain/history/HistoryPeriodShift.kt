package com.example.workingapp.domain.history

import java.time.LocalDate

/** [anchor] 를 이 기간 단위로 [delta] 만큼 이동한다. */
fun HistoryPeriod.shift(anchor: LocalDate, delta: Long): LocalDate = when (this) {
    HistoryPeriod.DAY -> anchor.plusDays(delta)
    HistoryPeriod.WEEK -> anchor.plusWeeks(delta)
    HistoryPeriod.MONTH -> anchor.plusMonths(delta)
}
