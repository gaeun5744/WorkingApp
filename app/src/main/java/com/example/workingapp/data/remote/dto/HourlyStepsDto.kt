package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class HourlyStepsDto(
    val hour: Int,
    val steps: Int,
)
