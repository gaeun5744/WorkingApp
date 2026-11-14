package com.example.workingapp.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.workingapp.data.local.dao.StepDao
import com.example.workingapp.data.local.entity.CounterCheckpointEntity
import com.example.workingapp.data.local.entity.DailyStepsEntity
import com.example.workingapp.data.local.entity.HourlyStepsEntity

/**
 * 스키마 이력은 `app/schemas/` 에 내보낸다.
 * - v2: `daily_steps.goal` 추가 (AutoMigration, 기존 행은 기본 목표로 채움)
 */
@Database(
    entities = [DailyStepsEntity::class, HourlyStepsEntity::class, CounterCheckpointEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class WorkingAppDatabase : RoomDatabase() {
    abstract fun stepDao(): StepDao
}
