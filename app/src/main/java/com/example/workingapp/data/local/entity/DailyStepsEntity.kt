package com.example.workingapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 날짜별 누적 걸음. [synced] 는 서버 업로드 후 true (M3). */
@Entity(tableName = "daily_steps")
data class DailyStepsEntity(
    @PrimaryKey val date: String,
    val steps: Int,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
    val synced: Boolean,
)
