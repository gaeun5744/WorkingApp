package com.example.workingapp.presentation.ranking

import com.example.workingapp.domain.model.RankingPeriod

sealed interface RankingEvent {
    data class SelectPeriod(val period: RankingPeriod) : RankingEvent
    data object Refresh : RankingEvent
    data object Retry : RankingEvent
    data object ChangeRegion : RankingEvent
}
