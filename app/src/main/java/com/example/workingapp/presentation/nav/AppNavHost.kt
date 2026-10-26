package com.example.workingapp.presentation.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.rememberResultEventBusNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.workingapp.R
import com.example.workingapp.presentation.history.HistoryRoute
import com.example.workingapp.presentation.home.HomeRoute
import com.example.workingapp.presentation.onboarding.OnboardingRoute
import com.example.workingapp.presentation.placeholder.PlaceholderScreen
import com.example.workingapp.presentation.region.RegionSelectRoute
import com.example.workingapp.presentation.walkmap.WalkMapRoute
import com.example.workingapp.presentation.wallet.WalletRoute

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    viewModel: AppNavViewModel = hiltViewModel(),
) {
    val backStack = rememberNavBackStack(viewModel.startKey)
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
            entry<OnboardingKey> {
                OnboardingRoute(
                    onSelectRegion = { navigate(RegionSelectKey) },
                    onCompleted = {
                        // 온보딩으로 돌아갈 수 없도록 백스택을 홈 하나로 바꾼다
                        backStack.clear()
                        backStack.add(HomeKey)
                    },
                )
            }
            entry<RegionSelectKey> {
                val resultBus = LocalResultEventBus.current
                RegionSelectRoute(
                    onRegionSelected = { region ->
                        resultBus.sendResult(RegionResult(region.id, region.name))
                        backStack.removeLastOrNull()
                    },
                )
            }
            entry<HomeKey> { HomeRoute(onNavigate = navigate) }
            entry<HistoryKey> { HistoryRoute() }
            entry<RankingKey> { PlaceholderScreen(title = stringResource(R.string.screen_ranking)) }
            entry<WalletKey> { WalletRoute() }
            entry<WalkMapKey> { WalkMapRoute() }
        },
    )
}
