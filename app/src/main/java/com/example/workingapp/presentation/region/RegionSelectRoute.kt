package com.example.workingapp.presentation.region

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workingapp.domain.model.Region

@Composable
fun RegionSelectRoute(
    onRegionSelected: (Region) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegionSelectViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RegionSelectScreen(
        query = query,
        state = uiState,
        onQueryChange = viewModel::onQueryChange,
        onRegionClick = onRegionSelected,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}
