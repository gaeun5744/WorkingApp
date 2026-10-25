package com.example.workingapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.workingapp.data.local.entity.CounterCheckpointEntity
import com.example.workingapp.data.local.entity.DailyStepsEntity
import com.example.workingapp.data.local.entity.HourlyStepsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {

    @Query("SELECT * FROM daily_steps WHERE date = :date")
    fun observeDaily(date: String): Flow<DailyStepsEntity?>

    @Query("SELECT * FROM daily_steps WHERE date = :date")
    suspend fun getDaily(date: String): DailyStepsEntity?

    @Query("SELECT * FROM hourly_steps WHERE date = :date AND hour = :hour")
    suspend fun getHourly(date: String, hour: Int): HourlyStepsEntity?

    @Query("SELECT * FROM hourly_steps WHERE date = :date ORDER BY hour")
    suspend fun getHourlyOf(date: String): List<HourlyStepsEntity>

    @Query("SELECT * FROM counter_checkpoint WHERE id = ${CounterCheckpointEntity.SINGLE_ROW_ID}")
    suspend fun getCheckpoint(): CounterCheckpointEntity?

    @Upsert
    suspend fun upsertDaily(entity: DailyStepsEntity)

    @Upsert
    suspend fun upsertHourly(entity: HourlyStepsEntity)

    @Upsert
    suspend fun upsertCheckpoint(entity: CounterCheckpointEntity)
}
