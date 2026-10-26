package com.example.workingapp.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workingapp.domain.history.HistoryPeriod
import com.example.workingapp.domain.history.HistoryRange
import com.example.workingapp.domain.history.HistorySummary
import com.example.workingapp.domain.history.shift
import com.example.workingapp.domain.model.DailySteps
import com.example.workingapp.domain.model.HourlySteps
import com.example.workingapp.domain.repository.StepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val stepRepository: StepRepository,
    private val clock: Clock,
) : ViewModel() {

    private val period = MutableStateFlow(HistoryPeriod.WEEK)
    private val anchor = MutableStateFlow(LocalDate.now(clock))

    // 기간·기준일이 바뀌면 이전 기간 구독은 버리고 새 기간만 구독한다
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> =
        combine(period, anchor) { period, anchor -> period to HistoryRange.of(period, anchor) }
            .flatMapLatest { (period, range) -> contentOf(period, range) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    fun onPeriodSelected(selected: HistoryPeriod) {
        period.value = selected
        // 기간을 바꾸면 오늘이 포함된 기간부터 보여준다
        anchor.value = LocalDate.now(clock)
    }

    fun onPrevious() = anchor.update { period.value.shift(it, -1) }

    fun onNext() {
        val today = LocalDate.now(clock)
        if (HistoryRange.of(period.value, anchor.value).hasNext(today)) anchor.update { period.value.shift(it, 1) }
    }

    private fun contentOf(period: HistoryPeriod, range: HistoryRange): Flow<HistoryUiState> {
        val days = stepRepository.observeDailySteps(range.start, range.end)
        return if (period == HistoryPeriod.DAY) {
            combine(days, stepRepository.observeHourlySteps(range.start)) { dailies, hourly ->
                content(period, range, dailies, bars = hourlyBars(hourly, goalReached = dailies.firstOrNull()?.isGoalAchieved == true))
            }
        } else {
            days.map { dailies -> content(period, range, dailies, bars = dailyBars(range, dailies)) }
        }
    }

    private fun content(period: HistoryPeriod, range: HistoryRange, dailies: List<DailySteps>, bars: List<ChartBar>) =
        HistoryUiState.Content(
            period = period,
            range = range,
            bars = bars.toImmutableList(),
            summary = HistorySummary.from(dailies, range, LocalDate.now(clock)),
            canGoNext = range.hasNext(LocalDate.now(clock)),
        )

    // 기록이 없는 날도 0 막대로 채워 날짜 칸이 비지 않게 한다
    private fun dailyBars(range: HistoryRange, dailies: List<DailySteps>): List<ChartBar> {
        val byDate = dailies.associateBy { it.date }
        return range.days.map { date ->
            val day = byDate[date]
            ChartBar(steps = day?.steps ?: 0, achieved = day?.isGoalAchieved == true)
        }
    }

    private fun hourlyBars(hourly: List<HourlySteps>, goalReached: Boolean): List<ChartBar> {
        val byHour = hourly.associate { it.hour to it.steps }
        return (0 until HOURS_PER_DAY).map { hour -> ChartBar(steps = byHour[hour] ?: 0, achieved = goalReached) }
    }

    private companion object {
        const val HOURS_PER_DAY = 24
    }
}
