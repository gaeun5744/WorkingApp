package com.example.workingapp.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workingapp.domain.repository.GoalRepository
import com.example.workingapp.domain.repository.StepRepository
import com.example.workingapp.domain.step.StepMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    stepRepository: StepRepository,
    private val goalRepository: GoalRepository,
) : ViewModel() {

    // 권한 확인에는 Context 가 필요해 Route 가 확인 결과를 전달한다. null = 아직 확인 전
    private val permissionGranted = MutableStateFlow<Boolean?>(null)

    val uiState: StateFlow<HomeUiState> =
        combine(permissionGranted, stepRepository.observeTodaySteps()) { granted, today ->
            when {
                !stepRepository.isSensorAvailable -> HomeUiState.SensorUnavailable
                granted == null -> HomeUiState.Loading
                !granted -> HomeUiState.PermissionRequired
                else -> HomeUiState.Tracking(
                    todaySteps = today.steps,
                    goal = today.goal,
                    metrics = StepMetrics.from(today.steps),
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    fun onPermissionChecked(granted: Boolean) {
        permissionGranted.value = granted
    }

    fun onGoalChange(goal: Int) {
        viewModelScope.launch { goalRepository.setGoal(goal) }
    }

    companion object {
        val GOAL_OPTIONS: List<Int> = listOf(3_000, 5_000, 6_000, 8_000, 10_000, 12_000, 15_000, 20_000)
    }
}
