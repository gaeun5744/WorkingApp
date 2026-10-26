package com.example.workingapp.presentation.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HistoryRoute(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        state = uiState,
        onPeriodSelected = viewModel::onPeriodSelected,
        onPrevious = viewModel::onPrevious,
        onNext = viewModel::onNext,
        modifier = modifier,
    )
}
