package com.example.workingapp.presentation.ranking

import com.example.workingapp.presentation.text.UiText

sealed interface RankingEffect {
    data class ShowMessage(val message: UiText) : RankingEffect
}
