package com.example.workingapp.data.repository

import androidx.room.withTransaction
import com.example.workingapp.data.local.WorkingAppDatabase
import com.example.workingapp.data.local.entity.DailyStepsEntity
import com.example.workingapp.data.local.entity.HourlyStepsEntity
import com.example.workingapp.data.local.entity.toDomain
import com.example.workingapp.data.local.entity.toEntity
import com.example.workingapp.data.sensor.StepDataSource
import com.example.workingapp.domain.model.DailySteps
import com.example.workingapp.domain.model.StepReading
import com.example.workingapp.domain.repository.StepRepository
import com.example.workingapp.domain.step.StepDelta
import com.example.workingapp.domain.step.StepDeltaCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

internal class StepRepositoryImpl @Inject constructor(
    private val database: WorkingAppDatabase,
    private val dataSource: StepDataSource,
    private val clock: Clock,
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
            ),
        )
        val hourly = dao.getHourly(date, delta.hour)
        dao.upsertHourly(HourlyStepsEntity(date, delta.hour, (hourly?.steps ?: 0) + delta.steps))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTodaySteps(): Flow<DailySteps> =
        todayFlow().flatMapLatest { today ->
            dao.observeDaily(today.toString()).map { it?.toDomain() ?: DailySteps(today, steps = 0) }
        }

    // 다음 자정까지 기다렸다가 새 날짜를 내보낸다
    private fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            val now = LocalDateTime.now(clock)
            emit(now.toLocalDate())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, nextMidnight).toMillis() + MIDNIGHT_MARGIN_MILLIS)
        }
    }.distinctUntilChanged()

    private companion object {
        const val MIDNIGHT_MARGIN_MILLIS = 1_000L
    }
}
