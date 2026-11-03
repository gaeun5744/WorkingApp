package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

/** `GET /steps` 응답. 기록이 있는 날짜만 날짜 오름차순으로 온다. */
@Serializable
data class StepsResponse(
    val days: List<StoredDailyStepsDto>,
)
