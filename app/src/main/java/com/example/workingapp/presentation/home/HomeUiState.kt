package com.example.workingapp.presentation.home

import com.example.workingapp.domain.step.StepMetrics

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object SensorUnavailable : HomeUiState
    data object PermissionRequired : HomeUiState
    data class Tracking(
        val todaySteps: Int,
        val goal: Int,
        val metrics: StepMetrics,
        val rewards: RewardsSection,
    ) : HomeUiState {
        /** 0.0~1.0 (목표를 넘어도 1.0) */
        val progress: Float get() = if (goal <= 0) 0f else (todaySteps.toFloat() / goal).coerceAtMost(1f)
    }
}
