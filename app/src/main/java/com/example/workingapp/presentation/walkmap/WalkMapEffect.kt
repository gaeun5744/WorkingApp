package com.example.workingapp.presentation.walkmap

import com.example.workingapp.presentation.text.UiText

sealed interface WalkMapEffect {
    data class ShowMessage(val message: UiText) : WalkMapEffect
}
