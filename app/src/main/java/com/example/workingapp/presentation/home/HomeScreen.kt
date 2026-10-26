package com.example.workingapp.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.workingapp.R
import com.example.workingapp.domain.step.StepMetrics
import com.example.workingapp.presentation.home.components.GoalDialog
import com.example.workingapp.presentation.home.components.RewardTiersCard
import com.example.workingapp.presentation.home.components.StepProgressRing
import com.example.workingapp.presentation.nav.HistoryKey
import com.example.workingapp.presentation.nav.RankingKey
import com.example.workingapp.presentation.nav.WalletKey
import com.example.workingapp.ui.theme.WorkingAppTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** 홈: 오늘 걸음·목표 진행률·환산값·리워드 구간 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier = modifier.fillMaxSize()) {
        HomeContent(state = state, onEvent = onEvent)
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }
}

@Composable
private fun HomeContent(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                HomeUiState.Loading -> CircularProgressIndicator()
                is HomeUiState.Tracking -> Tracking(state = state, onEvent = onEvent)
                HomeUiState.PermissionRequired -> PermissionRequired(onEvent = onEvent)
                HomeUiState.SensorUnavailable -> Message(
                    title = stringResource(R.string.home_sensor_unavailable_title),
                    description = stringResource(R.string.home_sensor_unavailable_description),
                )
            }
        }
        // 하단 내비게이션이 생기기 전까지의 임시 이동 버튼
        TemporaryNavigation(onEvent = onEvent)
    }
}

@Composable
private fun Tracking(state: HomeUiState.Tracking, onEvent: (HomeEvent) -> Unit) {
    // 다이얼로그 표시 여부는 이 화면 안에서만 쓰는 UI 상태라 ViewModel 에 두지 않는다
    var showGoalDialog by rememberSaveable { mutableStateOf(false) }

    StepProgressRing(progress = state.progress) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.home_today_steps, state.todaySteps),
                style = MaterialTheme.typography.displayMedium,
            )
            TextButton(onClick = { showGoalDialog = true }) {
                Text(stringResource(R.string.home_goal, state.goal))
            }
        }
    }
    MetricsRow(metrics = state.metrics)
    RewardTiersCard(
        section = state.rewards,
        onClaim = { tier -> onEvent(HomeEvent.ClaimReward(tier)) },
        onRetry = { onEvent(HomeEvent.RetryRewards) },
    )

    if (showGoalDialog) {
        GoalDialog(
            options = HomeViewModel.GOAL_OPTIONS.toImmutableList(),
            currentGoal = state.goal,
            onSelect = { goal ->
                onEvent(HomeEvent.ChangeGoal(goal))
                showGoalDialog = false
            },
            onDismiss = { showGoalDialog = false },
        )
    }
}

@Composable
private fun MetricsRow(metrics: StepMetrics) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Metric(value = stringResource(R.string.home_metric_distance, metrics.distanceKm), label = stringResource(R.string.home_metric_distance_label))
        Metric(value = stringResource(R.string.home_metric_kcal, metrics.kcal), label = stringResource(R.string.home_metric_kcal_label))
        Metric(value = stringResource(R.string.home_metric_minutes, metrics.minutes), label = stringResource(R.string.home_metric_minutes_label))
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermissionRequired(onEvent: (HomeEvent) -> Unit) {
    Message(
        title = stringResource(R.string.home_permission_title),
        description = stringResource(R.string.home_permission_description),
    )
    Button(onClick = { onEvent(HomeEvent.RequestPermission) }) {
        Text(stringResource(R.string.home_permission_request))
    }
    TextButton(onClick = { onEvent(HomeEvent.OpenSettings) }) {
        Text(stringResource(R.string.home_permission_open_settings))
    }
}

@Composable
private fun Message(title: String, description: String) {
    Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Text(text = description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
}

@Composable
private fun TemporaryNavigation(onEvent: (HomeEvent) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            R.string.screen_history to HistoryKey,
            R.string.screen_ranking to RankingKey,
            R.string.screen_wallet to WalletKey,
        ).forEach { (label, key) ->
            OutlinedButton(onClick = { onEvent(HomeEvent.Navigate(key)) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(label))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenTrackingPreview() {
    WorkingAppTheme {
        HomeScreen(
            state = HomeUiState.Tracking(
                todaySteps = 5_230,
                goal = 10_000,
                metrics = StepMetrics.from(5_230),
                rewards = RewardsSection.Loaded(
                    persistentListOf(
                        RewardTierUi(1, 1_000, 1, RewardTierState.CLAIMED),
                        RewardTierUi(2, 3_000, 3, RewardTierState.CLAIMABLE),
                        RewardTierUi(3, 5_000, 5, RewardTierState.CLAIMABLE),
                        RewardTierUi(4, 7_000, 7, RewardTierState.LOCKED),
                        RewardTierUi(5, 10_000, 10, RewardTierState.LOCKED),
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenGoalExceededPreview() {
    WorkingAppTheme {
        HomeScreen(
            state = HomeUiState.Tracking(
                todaySteps = 12_480,
                goal = 10_000,
                metrics = StepMetrics.from(12_480),
                rewards = RewardsSection.Loading,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPermissionPreview() {
    WorkingAppTheme { HomeScreen(state = HomeUiState.PermissionRequired, onEvent = {}) }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenSensorUnavailablePreview() {
    WorkingAppTheme { HomeScreen(state = HomeUiState.SensorUnavailable, onEvent = {}) }
}
