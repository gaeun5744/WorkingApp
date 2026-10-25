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

    /** 업로드 대상: [fromDate] 이후(포함)의 미동기화 날짜 */
    @Query("SELECT * FROM daily_steps WHERE synced = 0 AND date >= :fromDate ORDER BY date")
    suspend fun getUnsyncedSince(fromDate: String): List<DailyStepsEntity>

    /**
     * 업로드 직전에 읽은 [updatedAtMillis] 가 그대로일 때만 동기화 완료로 표시한다.
     * 업로드하는 동안 걸음이 더 쌓였다면 표시하지 않아 다음 동기화에서 다시 올라간다.
     */
    @Query("UPDATE daily_steps SET synced = 1 WHERE date = :date AND updated_at = :updatedAtMillis")
    suspend fun markSynced(date: String, updatedAtMillis: Long): Int

    /** 자동 재가입 시 새 계정으로 다시 올리기 위해 모두 미동기화로 되돌린다. OkHttp 스레드에서 동기 호출한다. */
    @Query("UPDATE daily_steps SET synced = 0")
    fun markAllUnsynced(): Int

    @Upsert
    suspend fun upsertDaily(entity: DailyStepsEntity)

    @Upsert
    suspend fun upsertHourly(entity: HourlyStepsEntity)

    @Upsert
    suspend fun upsertCheckpoint(entity: CounterCheckpointEntity)
}
