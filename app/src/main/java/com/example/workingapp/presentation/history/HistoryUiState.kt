package com.example.workingapp.presentation.history

import com.example.workingapp.domain.history.HistoryPeriod
import com.example.workingapp.domain.history.HistoryRange
import com.example.workingapp.domain.history.HistorySummary
import kotlinx.collections.immutable.ImmutableList

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    data class Content(
        val period: HistoryPeriod,
        val range: HistoryRange,
        /** DAY: 0~23시, WEEK: 월~일, MONTH: 1일~말일 */
        val bars: ImmutableList<ChartBar>,
        val summary: HistorySummary,
        val canGoNext: Boolean,
    ) : HistoryUiState
}
