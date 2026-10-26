package com.example.workingapp.presentation.ranking

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.result.ResultEffect
import com.example.workingapp.domain.model.Region
import com.example.workingapp.presentation.nav.RegionResult
import com.example.workingapp.presentation.text.resolve

@Composable
fun RankingRoute(
    onChangeRegion: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RankingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 동네 선택 화면이 돌려준 결과 (Nav3 ResultEventBus)
    ResultEffect<RegionResult> { result -> viewModel.onRegionSelected(Region(result.id, result.name)) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RankingEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message.resolve(context))
            }
        }
    }

    RankingScreen(
        state = uiState,
        onEvent = { event ->
            when (event) {
                is RankingEvent.SelectPeriod -> viewModel.onPeriodSelected(event.period)
                RankingEvent.Refresh -> viewModel.onRefresh()
                RankingEvent.Retry -> viewModel.onRetry()
                RankingEvent.ChangeRegion -> onChangeRegion()
            }
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}
