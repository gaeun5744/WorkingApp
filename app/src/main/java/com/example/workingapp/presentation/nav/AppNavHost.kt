package com.example.workingapp.presentation.nav

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.result.rememberResultEventBusNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.workingapp.R
import com.example.workingapp.presentation.placeholder.PlaceholderScreen

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    // 온보딩 분기(가입 여부에 따른 시작 화면)는 M3 에서 추가한다
    val backStack = rememberNavBackStack(HomeKey)
    val navigate: (NavKey) -> Unit = { key -> backStack.add(key) }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberResultEventBusNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeKey> {
                PlaceholderScreen(title = stringResource(R.string.screen_home)) {
                    Button(onClick = { navigate(HistoryKey) }) { Text(stringResource(R.string.screen_history)) }
                    Button(onClick = { navigate(RankingKey) }) { Text(stringResource(R.string.screen_ranking)) }
                    Button(onClick = { navigate(WalletKey) }) { Text(stringResource(R.string.screen_wallet)) }
                    Button(onClick = { navigate(RegionSelectKey) }) { Text(stringResource(R.string.screen_region_select)) }
                    Button(onClick = { navigate(OnboardingKey) }) { Text(stringResource(R.string.screen_onboarding)) }
                }
            }
            entry<HistoryKey> { PlaceholderScreen(title = stringResource(R.string.screen_history)) }
            entry<RankingKey> { PlaceholderScreen(title = stringResource(R.string.screen_ranking)) }
            entry<WalletKey> { PlaceholderScreen(title = stringResource(R.string.screen_wallet)) }
            entry<RegionSelectKey> { PlaceholderScreen(title = stringResource(R.string.screen_region_select)) }
            entry<OnboardingKey> { PlaceholderScreen(title = stringResource(R.string.screen_onboarding)) }
        },
    )
}
