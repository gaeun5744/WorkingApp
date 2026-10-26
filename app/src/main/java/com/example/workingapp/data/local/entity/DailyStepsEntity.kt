package com.example.workingapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 날짜별 누적 걸음. [synced] 는 서버 업로드 후 true.
 * [goal] 은 그날의 목표 (v2 에서 추가). 목표를 바꿔도 지난날의 달성 여부가 바뀌지 않도록 날짜별로 남긴다.
 */
@Entity(tableName = "daily_steps")
data class DailyStepsEntity(
    @PrimaryKey val date: String,
    val steps: Int,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
    val synced: Boolean,
    @ColumnInfo(defaultValue = DEFAULT_GOAL_SQL) val goal: Int,
) {
    companion object {
        // v1 → v2 마이그레이션 때 기존 행에 채워지는 값. GoalStore.DEFAULT_GOAL 과 같게 유지한다
        const val DEFAULT_GOAL_SQL = "10000"
    }
}
