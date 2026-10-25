package com.example.workingapp.data.local.entity

import androidx.room.Entity

/** 시간대별 걸음 (누적 아님). 서버 타당성 검사용 업로드 데이터 (PRD Q9). */
@Entity(tableName = "hourly_steps", primaryKeys = ["date", "hour"])
data class HourlyStepsEntity(
    val date: String,
    val hour: Int,
    val steps: Int,
)
