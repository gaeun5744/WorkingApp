package com.example.workingapp.presentation.ranking

import com.example.workingapp.domain.model.RankingPeriod

data class RankingUiState(
    val period: RankingPeriod = RankingPeriod.DAILY,
    val content: RankingContent = RankingContent.Loading,
    val isRefreshing: Boolean = false,
)
