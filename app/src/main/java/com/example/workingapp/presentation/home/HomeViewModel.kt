package com.example.workingapp.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workingapp.domain.repository.StepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: StepRepository,
) : ViewModel() {

    // 권한 확인에는 Context 가 필요해 Route 가 확인 결과를 전달한다. null = 아직 확인 전
    private val permissionGranted = MutableStateFlow<Boolean?>(null)

    val uiState: StateFlow<HomeUiState> =
        combine(permissionGranted, repository.observeTodaySteps()) { granted, today ->
            when {
                !repository.isSensorAvailable -> HomeUiState.SensorUnavailable
                granted == null -> HomeUiState.Loading
                !granted -> HomeUiState.PermissionRequired
                else -> HomeUiState.Tracking(todaySteps = today.steps)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    fun onPermissionChecked(granted: Boolean) {
        permissionGranted.value = granted
    }
}
