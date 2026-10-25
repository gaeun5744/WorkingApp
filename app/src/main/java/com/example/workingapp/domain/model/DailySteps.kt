package com.example.workingapp.domain.model

import java.time.LocalDate

data class DailySteps(
    val date: LocalDate,
    val steps: Int,
)
