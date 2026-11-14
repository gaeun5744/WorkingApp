package com.example.workingapp.presentation.home

import app.cash.turbine.test
import com.example.workingapp.R
import com.example.workingapp.domain.model.AppError
import com.example.workingapp.domain.model.ClaimResult
import com.example.workingapp.domain.model.DailySteps
import com.example.workingapp.presentation.text.UiText
import com.example.workingapp.testing.FakeGoalRepository
import com.example.workingapp.testing.FakeRewardRepository
import com.example.workingapp.testing.FakeStepRepository
import com.example.workingapp.testing.MainDispatcherRule
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeRewardClaimTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 25)
    private val stepRepository = FakeStepRepository()
    private val rewardRepository = FakeRewardRepository()
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        // 기기 걸음 5,200 — 서버는 아직 동기화 전이라 모든 구간이 LOCKED
        stepRepository.today.value = DailySteps(today, steps = 5_200, goal = 10_000)
        viewModel = HomeViewModel(stepRepository, FakeGoalRepository(), rewardRepository)
        viewModel.onPermissionChecked(true)
    }

    /** uiState 는 구독자가 있을 때만 갱신되므로(WhileSubscribed) 테스트 동안 구독을 유지한다 */
    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun HomeViewModel.tiers(): Map<Int, RewardTierState> {
        val tracking = uiState.value as HomeUiState.Tracking
        val rewards = tracking.rewards as RewardsSection.Loaded
        return rewards.tiers.associate { it.tier to it.state }
    }

    @Test
    fun `서버가 아직 잠김이어도 기기 걸음이 넘은 구간은 받기 가능으로 보인다`() = runTest {
        subscribe()
        viewModel.uiState.test {
            awaitItem()
            assertEquals(
                mapOf(
                    1 to RewardTierState.CLAIMABLE,
                    2 to RewardTierState.CLAIMABLE,
                    3 to RewardTierState.CLAIMABLE,
                    4 to RewardTierState.LOCKED,
                    5 to RewardTierState.LOCKED,
                ),
                viewModel.tiers(),
            )
        }
    }

    @Test
    fun `수령 중에는 바로 받음으로 보이고, 최신 걸음을 먼저 동기화한 뒤 오늘 날짜로 요청한다`() = runTest {
        subscribe()
        val response = CompletableDeferred<Result<ClaimResult>>()
        rewardRepository.claimHandler = { _, _ ->
            assertEquals("수령 전에 동기화해야 한다", 1, stepRepository.syncCount)
            response.await()
        }
        viewModel.uiState.test {
            awaitItem()
            viewModel.onClaim(3)
            assertEquals(RewardTierState.CLAIMED, viewModel.tiers()[3])

            response.complete(Result.success(ClaimResult(tier = 3, points = 5, balance = 5, alreadyClaimed = false)))
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(today to 3), rewardRepository.claimCalls)
        assertEquals(RewardTierState.CLAIMED, viewModel.tiers()[3])
    }

    @Test
    fun `적립되면 포인트와 잔액을 알려준다`() = runTest {
        subscribe()
        viewModel.effects.test {
            viewModel.onClaim(1)
            assertEquals(
                HomeEffect.ShowMessage(UiText.Resource(R.string.reward_claimed, persistentListOf(1, 1))),
                awaitItem(),
            )
        }
    }

    @Test
    fun `서버가 구간 미달로 거절하면 받기 가능으로 되돌리고 이유를 알려준다`() = runTest {
        subscribe()
        rewardRepository.claimHandler = { _, _ -> Result.failure(AppError.InvalidRequest("REWARD_NOT_REACHED")) }

        viewModel.effects.test {
            viewModel.onClaim(3)
            assertEquals(HomeEffect.ShowMessage(UiText.Resource(R.string.reward_error_not_reached)), awaitItem())
        }
        assertEquals(RewardTierState.CLAIMABLE, viewModel.tiers()[3])
    }

    @Test
    fun `동기화가 실패하면 수령을 요청하지 않고 되돌린다`() = runTest {
        subscribe()
        stepRepository.syncResult = Result.failure(AppError.Network)

        viewModel.effects.test {
            viewModel.onClaim(2)
            assertEquals(HomeEffect.ShowMessage(UiText.Resource(R.string.error_network)), awaitItem())
        }
        assertEquals(emptyList<Pair<LocalDate, Int>>(), rewardRepository.claimCalls)
        assertEquals(RewardTierState.CLAIMABLE, viewModel.tiers()[2])
    }

    @Test
    fun `응답을 기다리는 동안 다시 눌러도 요청은 한 번만 보낸다`() = runTest {
        subscribe()
        val response = CompletableDeferred<Result<ClaimResult>>()
        rewardRepository.claimHandler = { _, _ -> response.await() }

        viewModel.onClaim(1)
        viewModel.onClaim(1)
        viewModel.onClaim(1)
        response.complete(Result.success(ClaimResult(tier = 1, points = 1, balance = 1, alreadyClaimed = false)))
        viewModel.onClaim(1)

        assertEquals(1, rewardRepository.claimCalls.size)
    }

    @Test
    fun `이미 받은 구간이면 포인트 없이 이미 받았다고 알려준다`() = runTest {
        subscribe()
        rewardRepository.claimHandler = { _, tier -> Result.success(ClaimResult(tier, points = 1, balance = 7, alreadyClaimed = true)) }

        viewModel.effects.test {
            viewModel.onClaim(1)
            assertEquals(HomeEffect.ShowMessage(UiText.Resource(R.string.reward_already_claimed)), awaitItem())
        }
    }

    @Test
    fun `서버에서 이미 받은 구간은 받음으로 보이고 누를 수 없다`() = runTest {
        subscribe()
        rewardRepository.todayResult = Result.success(FakeRewardRepository.todayRewards(claimed = setOf(1)))
        viewModel = HomeViewModel(stepRepository, FakeGoalRepository(), rewardRepository)
        viewModel.onPermissionChecked(true)
        subscribe()

        viewModel.uiState.test {
            awaitItem()
            assertEquals(RewardTierState.CLAIMED, viewModel.tiers()[1])
            viewModel.onClaim(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, rewardRepository.claimCalls.size)
    }
}
