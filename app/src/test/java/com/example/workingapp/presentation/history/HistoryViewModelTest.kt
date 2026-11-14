package com.example.workingapp.presentation.history

import app.cash.turbine.test
import com.example.workingapp.domain.history.HistoryPeriod
import com.example.workingapp.domain.model.DailySteps
import com.example.workingapp.domain.model.HourlySteps
import com.example.workingapp.testing.FakeStepRepository
import com.example.workingapp.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // 2026-10-25(일) 14:00 KST
    private val clock = Clock.fixed(OffsetDateTime.parse("2026-10-25T14:00:00+09:00").toInstant(), ZoneId.of("Asia/Seoul"))
    private val today = LocalDate.of(2026, 10, 25)
    private val repository = FakeStepRepository()

    private fun content(state: HistoryUiState) = state as HistoryUiState.Content

    @Test
    fun `처음에는 이번 주를 월요일부터 7칸으로 보여주고 다음 주로는 갈 수 없다`() = runTest {
        repository.days.value = listOf(
            DailySteps(LocalDate.of(2026, 10, 19), 12_000, goal = 10_000),
            DailySteps(LocalDate.of(2026, 10, 21), 4_000, goal = 10_000),
        )
        val viewModel = HistoryViewModel(repository, clock)

        // UnconfinedTestDispatcher 에서는 구독 즉시 Content 가 계산되어 StateFlow 가 Loading 을 건너뛴다
        viewModel.uiState.test {
            val week = content(awaitItem())
            assertEquals(HistoryPeriod.WEEK, week.period)
            assertEquals(listOf(12_000, 0, 4_000, 0, 0, 0, 0), week.bars.map { it.steps })
            assertEquals(listOf(true, false, false, false, false, false, false), week.bars.map { it.achieved })
            assertEquals(1, week.summary.achievedDays)
            assertFalse(week.canGoNext)
        }
    }

    @Test
    fun `이전 주로 이동하면 다음 주로 다시 갈 수 있고, 이번 주에서 다음은 무시한다`() = runTest {
        val viewModel = HistoryViewModel(repository, clock)

        viewModel.uiState.test {
            skipItems(1)
            viewModel.onPrevious()
            val lastWeek = content(awaitItem())
            assertEquals(LocalDate.of(2026, 10, 12), lastWeek.range.start)
            assertTrue(lastWeek.canGoNext)

            viewModel.onNext()
            assertEquals(LocalDate.of(2026, 10, 19), content(awaitItem()).range.start)

            viewModel.onNext()
            expectNoEvents()
        }
    }

    @Test
    fun `일간은 24시간 막대이고 데이터가 바뀌면 다시 내보낸다`() = runTest {
        repository.hourly.value = mapOf(today to listOf(HourlySteps(hour = 9, steps = 1_200)))
        val viewModel = HistoryViewModel(repository, clock)

        viewModel.uiState.test {
            skipItems(1)
            viewModel.onPeriodSelected(HistoryPeriod.DAY)
            val day = content(awaitItem())
            assertEquals(24, day.bars.size)
            assertEquals(1_200, day.bars[9].steps)

            repository.hourly.value = mapOf(today to listOf(HourlySteps(hour = 9, steps = 1_500)))
            assertEquals(1_500, content(awaitItem()).bars[9].steps)
        }
    }

    @Test
    fun `월간은 그 달의 날수만큼 막대를 만든다`() = runTest {
        val viewModel = HistoryViewModel(repository, clock)

        viewModel.uiState.test {
            skipItems(1)
            viewModel.onPeriodSelected(HistoryPeriod.MONTH)
            assertEquals(31, content(awaitItem()).bars.size)
        }
    }
}
