package com.example.workingapp.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workingapp.domain.model.Region
import com.example.workingapp.domain.repository.AccountRepository
import com.example.workingapp.presentation.text.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    // 가입은 했지만 동네를 정하기 전에 앱을 껐다면 동네 단계부터 이어서 한다
    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            step = if (accountRepository.hasSignedUp()) OnboardingStep.REGION else OnboardingStep.NICKNAME,
            nickname = accountRepository.savedNickname().orEmpty(),
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _completed = Channel<Unit>(Channel.BUFFERED)
    /** 온보딩 완료 (일회성 이벤트) */
    val completed: Flow<Unit> = _completed.receiveAsFlow()

    fun onNicknameChange(nickname: String) {
        _uiState.update { it.copy(nickname = nickname.take(OnboardingUiState.MAX_NICKNAME_LENGTH), error = null) }
    }

    fun onSubmitNickname() {
        val state = _uiState.value
        if (!state.canSubmitNickname) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            accountRepository.signUp(state.nickname.trim())
                .onSuccess { _uiState.update { it.copy(isSubmitting = false, step = OnboardingStep.REGION) } }
                .onFailure { e -> _uiState.update { it.copy(isSubmitting = false, error = e.toUiText()) } }
        }
    }

    fun onRegionSelected(region: Region) {
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            accountRepository.updateRegion(region)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _completed.send(Unit)
                }
                .onFailure { e -> _uiState.update { it.copy(isSubmitting = false, error = e.toUiText()) } }
        }
    }
}
