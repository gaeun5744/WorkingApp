package com.example.workingapp.presentation.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.result.ResultEffect
import com.example.workingapp.domain.model.Region
import com.example.workingapp.presentation.nav.RegionResult
import com.example.workingapp.worker.StepSyncWorker

@Composable
fun OnboardingRoute(
    onSelectRegion: () -> Unit,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 동네 선택 화면이 돌려준 결과 (Nav3 ResultEventBus)
    ResultEffect<RegionResult> { result -> viewModel.onRegionSelected(Region(result.id, result.name)) }

    LaunchedEffect(viewModel) {
        viewModel.completed.collect {
            // 가입 전에 쌓인 걸음을 바로 올린다
            StepSyncWorker.syncNow(context)
            onCompleted()
        }
    }

    OnboardingScreen(
        state = uiState,
        onNicknameChange = viewModel::onNicknameChange,
        onSubmitNickname = viewModel::onSubmitNickname,
        onSelectRegion = onSelectRegion,
        modifier = modifier,
    )
}
