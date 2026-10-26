package com.example.workingapp.presentation.walkmap

import com.example.workingapp.presentation.text.UiText
import kotlinx.collections.immutable.ImmutableList

sealed interface TreasureContent {
    /** 주변 상자를 찾으려면 위치가 먼저 필요하다 */
    data object WaitingForLocation : TreasureContent
    data object Loading : TreasureContent
    data class Error(val message: UiText) : TreasureContent
    data class Loaded(
        val openRadiusMeters: Int,
        val dailyOpenLimit: Int,
        val openedToday: Int,
        val treasures: ImmutableList<TreasureUi>,
    ) : TreasureContent {
        val reachedDailyLimit: Boolean get() = openedToday >= dailyOpenLimit
    }
}
