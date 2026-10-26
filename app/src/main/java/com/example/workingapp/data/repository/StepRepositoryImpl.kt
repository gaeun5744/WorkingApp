package com.example.workingapp.data.repository

import androidx.room.withTransaction
import com.example.workingapp.data.local.GoalStore
import com.example.workingapp.data.local.WorkingAppDatabase
import com.example.workingapp.data.local.entity.DailyStepsEntity
import com.example.workingapp.data.local.entity.HourlyStepsEntity
import com.example.workingapp.data.local.entity.toDomain
import com.example.workingapp.data.local.entity.toEntity
import com.example.workingapp.data.remote.api.PedometerApi
import com.example.workingapp.data.remote.apiCall
import com.example.workingapp.data.remote.dto.DailyStepsDto
import com.example.workingapp.data.remote.dto.HourlyStepsDto
import com.example.workingapp.data.remote.dto.StepsSyncRequest
import com.example.workingapp.data.sensor.StepDataSource
import com.example.workingapp.domain.model.DailySteps
import com.example.workingapp.domain.model.HourlySteps
import com.example.workingapp.domain.model.StepReading
import com.example.workingapp.domain.repository.StepRepository
import com.example.workingapp.domain.step.StepDelta
import com.example.workingapp.domain.step.StepDeltaCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

internal class StepRepositoryImpl @Inject constructor(
    private val database: WorkingAppDatabase,
    private val dataSource: StepDataSource,
    private val clock: Clock,
    private val api: PedometerApi,
    private val json: Json,
    private val goalStore: GoalStore,
) : StepRepository {

    private val dao = database.stepDao()
    private val calculator = StepDeltaCalculator(clock.zone)

    override val isSensorAvailable: Boolean get() = dataSource.isAvailable

    override fun readings(): Flow<StepReading> = dataSource.readings()

    override suspend fun record(reading: StepReading) {
        // 서비스와 워커가 동시에 기록해도 체크포인트를 읽고-계산하고-쓰는 과정이 섞이지 않도록 한 트랜잭션으로 묶는다
        database.withTransaction {
            val calculation = calculator.calculate(dao.getCheckpoint()?.toDomain(), reading)
            calculation.delta?.let { addSteps(it) }
            dao.upsertCheckpoint(calculation.checkpoint.toEntity())
        }
    }

    private suspend fun addSteps(delta: StepDelta) {
        val date = delta.date.toString()
        val daily = dao.getDaily(date)
        dao.upsertDaily(
            DailyStepsEntity(
                date = date,
                steps = (daily?.steps ?: 0) + delta.steps,
                updatedAtMillis = clock.millis(),
                synced = false,
                // 그날 첫 기록일 때의 목표를 남긴다. 이후 목표 변경은 GoalRepository 가 오늘 행에 반영한다
                goal = daily?.goal ?: goalStore.current(),
            ),
        )
        val hourly = dao.getHourly(date, delta.hour)
        dao.upsertHourly(HourlyStepsEntity(date, delta.hour, (hourly?.steps ?: 0) + delta.steps))
    }

    // 오늘은 아직 기록이 없을 수 있어 목표는 항상 현재 목표를 쓴다
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTodaySteps(): Flow<DailySteps> =
        todayFlow().flatMapLatest { today ->
            combine(dao.observeDaily(today.toString()), goalStore.goal) { entity, goal ->
                DailySteps(today, steps = entity?.steps ?: 0, goal = goal)
            }
        }

    override fun observeDailySteps(from: LocalDate, to: LocalDate): Flow<List<DailySteps>> =
        dao.observeRange(from.toString(), to.toString()).map { rows -> rows.map { it.toDomain() } }

    override fun observeHourlySteps(date: LocalDate): Flow<List<HourlySteps>> =
        dao.observeHourlyOf(date.toString()).map { rows -> rows.map { it.toDomain() } }

    // 다음 자정까지 기다렸다가 새 날짜를 내보낸다
    private fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            val now = LocalDateTime.now(clock)
            emit(now.toLocalDate())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, nextMidnight).toMillis() + MIDNIGHT_MARGIN_MILLIS)
        }
    }.distinctUntilChanged()

    override suspend fun syncPendingSteps(): Result<Unit> {
        val fromDate = LocalDate.now(clock).minusDays(SYNC_WINDOW_DAYS - 1).toString()
        val pending = dao.getUnsyncedSince(fromDate)
        if (pending.isEmpty()) return Result.success(Unit)

        val request = StepsSyncRequest(
            days = pending.map { day ->
                DailyStepsDto(
                    date = day.date,
                    steps = day.steps,
                    hourly = dao.getHourlyOf(day.date).map { HourlyStepsDto(it.hour, it.steps) },
                )
            },
        )
        return apiCall(json) { api.syncSteps(request) }.map {
            // ACCEPTED·CAPPED·REJECTED 모두 서버가 처리를 끝낸 것이므로 다시 보낼 필요가 없다
            pending.forEach { day -> dao.markSynced(day.date, day.updatedAtMillis) }
        }
    }

    private companion object {
        const val MIDNIGHT_MARGIN_MILLIS = 1_000L
        const val SYNC_WINDOW_DAYS = 7L
    }
}
