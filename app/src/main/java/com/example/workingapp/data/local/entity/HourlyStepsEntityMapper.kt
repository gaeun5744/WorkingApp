package com.example.workingapp.data.local.entity

import com.example.workingapp.domain.model.HourlySteps

fun HourlyStepsEntity.toDomain(): HourlySteps = HourlySteps(hour = hour, steps = steps)
