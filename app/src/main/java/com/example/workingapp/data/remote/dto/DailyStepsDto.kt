package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DailyStepsDto(
    val date: String,
    val steps: Int,
    val hourly: List<HourlyStepsDto>,
)
