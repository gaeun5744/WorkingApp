package com.example.workingapp.presentation.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.rememberResultEventBusNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.workingapp.presentation.history.HistoryRoute
import com.example.workingapp.presentation.home.HomeRoute
import com.example.workingapp.presentation.onboarding.OnboardingRoute
import com.example.workingapp.presentation.ranking.RankingRoute
import com.example.workingapp.presentation.region.RegionSelectRoute
import com.example.workingapp.presentation.walkmap.WalkMapRoute
import com.example.workingapp.presentation.wallet.WalletRoute
import timber.log.Timber

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    viewModel: AppNavViewModel = hiltViewModel(),
) {
    val backStack = rememberNavBackStack(viewModel.startKey)
    val navigate: (NavKey) -> Unit = { key -> backStack.add(key) }

    // 불변식: 백스택은 비면 안 된다 (NavDisplay 가 IllegalArgumentException 으로 종료).
    // M7 에뮬레이터 확인 중 동네 선택 결과를 돌려준 직후 한 번 "backstack cannot be empty" 크래시가 났지만
    // 재현하지 못해 원인을 찾지 못했다 (docs/architecture.md §8). 마지막 화면은 빼지 않고, 그런 시도가 있으면 기록한다.
    val pop: (from: String) -> Unit = { from ->
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else {
            Timber.w("마지막 화면을 빼려는 시도를 막음 (from=%s, backStack=%s)", from, backStack.toList())
        }
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { pop("onBack") },
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
                        pop("regionSelected")
                    },
                )
            }
            entry<HomeKey> { HomeRoute(onNavigate = navigate) }
            entry<HistoryKey> { HistoryRoute() }
            entry<RankingKey> { RankingRoute(onChangeRegion = { navigate(RegionSelectKey) }) }
            entry<WalletKey> { WalletRoute() }
            entry<WalkMapKey> { WalkMapRoute() }
        },
    )
}
