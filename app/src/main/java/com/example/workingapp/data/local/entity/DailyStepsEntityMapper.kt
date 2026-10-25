package com.example.workingapp.data.local.entity

import com.example.workingapp.domain.model.DailySteps
import java.time.LocalDate

fun DailyStepsEntity.toDomain(): DailySteps = DailySteps(date = LocalDate.parse(date), steps = steps)
