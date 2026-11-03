package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

/** 서버에 저장된(상한·max 적용 후) 날짜별 걸음. 업로드용 [DailyStepsDto] 와 달리 시간대별 값이 없다. */
@Serializable
data class StoredDailyStepsDto(
    val date: String,
    val steps: Int,
)
