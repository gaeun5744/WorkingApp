package com.example.workingapp.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.workingapp.R
import com.example.workingapp.presentation.nav.HistoryKey
import com.example.workingapp.presentation.nav.OnboardingKey
import com.example.workingapp.presentation.nav.RankingKey
import com.example.workingapp.presentation.nav.RegionSelectKey
import com.example.workingapp.presentation.nav.WalletKey
import com.example.workingapp.ui.theme.WorkingAppTheme

/** M2 1차 홈: 오늘 걸음과 측정 상태. 진행 바·리워드 구간은 M4 에서 추가한다. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                HomeUiState.Loading -> CircularProgressIndicator()
                is HomeUiState.Tracking -> TodaySteps(steps = state.todaySteps)
                HomeUiState.PermissionRequired -> PermissionRequired(onEvent = onEvent)
                HomeUiState.SensorUnavailable -> Message(
                    title = stringResource(R.string.home_sensor_unavailable_title),
                    description = stringResource(R.string.home_sensor_unavailable_description),
                )
            }
        }
        // 다른 화면이 만들어지기 전까지의 임시 이동 버튼
        TemporaryNavigation(onEvent = onEvent)
    }
}

@Composable
private fun TodaySteps(steps: Int) {
    Text(
        text = stringResource(R.string.home_today_steps, steps),
        style = MaterialTheme.typography.displayLarge,
    )
    Text(text = stringResource(R.string.home_steps_unit), style = MaterialTheme.typography.titleMedium)
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            R.string.screen_history to HistoryKey,
            R.string.screen_ranking to RankingKey,
            R.string.screen_wallet to WalletKey,
            R.string.screen_region_select to RegionSelectKey,
            R.string.screen_onboarding to OnboardingKey,
        ).forEach { (label, key) ->
            OutlinedButton(onClick = { onEvent(HomeEvent.Navigate(key)) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(label))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenTrackingPreview() {
    WorkingAppTheme { HomeScreen(state = HomeUiState.Tracking(todaySteps = 5_230), onEvent = {}) }
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

@Preview(showBackground = true)
@Composable
private fun HomeScreenLoadingPreview() {
    WorkingAppTheme { HomeScreen(state = HomeUiState.Loading, onEvent = {}) }
}
