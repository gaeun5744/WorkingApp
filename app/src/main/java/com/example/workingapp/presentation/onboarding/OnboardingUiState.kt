package com.example.workingapp.presentation.onboarding

import com.example.workingapp.presentation.text.UiText

data class OnboardingUiState(
    val step: OnboardingStep,
    val nickname: String,
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
) {
    val canSubmitNickname: Boolean get() = nickname.trim().length in 1..MAX_NICKNAME_LENGTH && !isSubmitting

    companion object {
        const val MAX_NICKNAME_LENGTH = 12
    }
}
