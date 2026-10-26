package com.example.workingapp.presentation.home

import app.cash.turbine.test
import com.example.workingapp.domain.model.DailySteps
import com.example.workingapp.testing.FakeGoalRepository
import com.example.workingapp.testing.FakeRewardRepository
import com.example.workingapp.testing.FakeStepRepository
import com.example.workingapp.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val stepRepository = FakeStepRepository()
    private val goalRepository = FakeGoalRepository()

    @Test
    fun `권한이 있으면 걸음·목표·진행률·환산값을 보여준다`() = runTest {
        stepRepository.today.value = DailySteps(LocalDate.of(2026, 10, 25), steps = 5_000, goal = 10_000)
        val viewModel = HomeViewModel(stepRepository, goalRepository, FakeRewardRepository())

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            viewModel.onPermissionChecked(true)
            val tracking = awaitItem() as HomeUiState.Tracking
            assertEquals(0.5f, tracking.progress)
            assertEquals(3.5, tracking.metrics.distanceKm, 1e-9)
        }
    }

    @Test
    fun `권한이 없으면 권한 요청 상태다`() = runTest {
        val viewModel = HomeViewModel(stepRepository, goalRepository, FakeRewardRepository())

        viewModel.uiState.test {
            skipItems(1)
            viewModel.onPermissionChecked(false)
            assertEquals(HomeUiState.PermissionRequired, awaitItem())
        }
    }

    @Test
    fun `센서가 없으면 권한과 관계없이 미지원 상태다`() = runTest {
        val viewModel = HomeViewModel(FakeStepRepository(isSensorAvailable = false), goalRepository, FakeRewardRepository())

        viewModel.uiState.test {
            assertEquals(HomeUiState.SensorUnavailable, awaitItem())
        }
    }

    @Test
    fun `목표를 넘으면 진행률은 1이고, 목표 변경은 저장소에 반영된다`() = runTest {
        stepRepository.today.value = DailySteps(LocalDate.of(2026, 10, 25), steps = 12_000, goal = 10_000)
        val viewModel = HomeViewModel(stepRepository, goalRepository, FakeRewardRepository())

        viewModel.uiState.test {
            skipItems(1)
            viewModel.onPermissionChecked(true)
            assertEquals(1f, (awaitItem() as HomeUiState.Tracking).progress)
        }
        viewModel.onGoalChange(8_000)
        assertEquals(8_000, goalRepository.goal.value)
    }
}
