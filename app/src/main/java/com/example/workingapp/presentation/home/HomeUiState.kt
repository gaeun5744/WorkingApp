package com.example.workingapp.presentation.home

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object SensorUnavailable : HomeUiState
    data object PermissionRequired : HomeUiState
    data class Tracking(val todaySteps: Int) : HomeUiState
}
