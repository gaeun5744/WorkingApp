package com.example.workingapp.testing

import com.example.workingapp.domain.model.DailySteps
import com.example.workingapp.domain.model.HourlySteps
import com.example.workingapp.domain.model.StepReading
import com.example.workingapp.domain.repository.StepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeStepRepository(
    override val isSensorAvailable: Boolean = true,
) : StepRepository {

    val days = MutableStateFlow<List<DailySteps>>(emptyList())
    val hourly = MutableStateFlow<Map<LocalDate, List<HourlySteps>>>(emptyMap())
    val today = MutableStateFlow(DailySteps(LocalDate.of(2026, 10, 25), steps = 0, goal = 10_000))

    override fun readings(): Flow<StepReading> = emptyFlow()
    override suspend fun record(reading: StepReading) = Unit
    override fun observeTodaySteps(): Flow<DailySteps> = today
    var syncResult: Result<Unit> = Result.success(Unit)
    var syncCount = 0
        private set

    override suspend fun syncPendingSteps(): Result<Unit> {
        syncCount++
        return syncResult
    }

    override fun observeDailySteps(from: LocalDate, to: LocalDate): Flow<List<DailySteps>> =
        days.map { list -> list.filter { !it.date.isBefore(from) && !it.date.isAfter(to) } }

    override fun observeHourlySteps(date: LocalDate): Flow<List<HourlySteps>> =
        hourly.map { it[date].orEmpty() }
}
