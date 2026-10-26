package com.example.workingapp.presentation.home

import com.example.workingapp.presentation.text.UiText

/** 한 번만 소비되는 이벤트 (UiState 에 섞지 않는다) */
sealed interface HomeEffect {
    data class ShowMessage(val message: UiText) : HomeEffect
}
