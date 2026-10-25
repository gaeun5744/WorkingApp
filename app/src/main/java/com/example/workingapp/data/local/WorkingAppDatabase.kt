package com.example.workingapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.workingapp.data.local.dao.StepDao
import com.example.workingapp.data.local.entity.CounterCheckpointEntity
import com.example.workingapp.data.local.entity.DailyStepsEntity
import com.example.workingapp.data.local.entity.HourlyStepsEntity

@Database(
    entities = [DailyStepsEntity::class, HourlyStepsEntity::class, CounterCheckpointEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WorkingAppDatabase : RoomDatabase() {
    abstract fun stepDao(): StepDao
}
