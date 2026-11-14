package com.example.workingapp.domain.step

import java.time.LocalDate

/** [date]의 [hour]시(KST)에 더할 걸음 수. */
data class StepDelta(
    val date: LocalDate,
    val hour: Int,
    val steps: Int,
)
