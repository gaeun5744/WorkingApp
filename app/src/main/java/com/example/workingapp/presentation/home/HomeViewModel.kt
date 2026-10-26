package com.example.workingapp.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workingapp.R
import com.example.workingapp.domain.model.AppError
import com.example.workingapp.domain.model.ClaimResult
import com.example.workingapp.domain.model.RewardStatus
import com.example.workingapp.domain.model.TodayRewards
import com.example.workingapp.domain.repository.GoalRepository
import com.example.workingapp.domain.repository.RewardRepository
import com.example.workingapp.domain.repository.StepRepository
import com.example.workingapp.domain.step.StepMetrics
import com.example.workingapp.presentation.text.UiText
import com.example.workingapp.presentation.text.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val stepRepository: StepRepository,
    private val goalRepository: GoalRepository,
    private val rewardRepository: RewardRepository,
) : ViewModel() {

    // 권한 확인에는 Context 가 필요해 Route 가 확인 결과를 전달한다. null = 아직 확인 전
    private val permissionGranted = MutableStateFlow<Boolean?>(null)
    private val serverRewards = MutableStateFlow<RewardsLoad>(RewardsLoad.Loading)
    private val claims = MutableStateFlow(ClaimState())
    private var today: LocalDate? = null

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> =
        combine(permissionGranted, stepRepository.observeTodaySteps(), serverRewards, claims) { granted, todaySteps, rewards, claims ->
            when {
                !stepRepository.isSensorAvailable -> HomeUiState.SensorUnavailable
                granted == null -> HomeUiState.Loading
                !granted -> HomeUiState.PermissionRequired
                else -> HomeUiState.Tracking(
                    todaySteps = todaySteps.steps,
                    goal = todaySteps.goal,
                    metrics = StepMetrics.from(todaySteps.steps),
                    rewards = rewardsSection(rewards, claims, localSteps = todaySteps.steps),
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    init {
        // 날짜가 바뀌면 그날의 리워드를 새로 받고, 전날 수령 기록은 버린다
        stepRepository.observeTodaySteps()
            .map { it.date }
            .distinctUntilChanged()
            .onEach { date ->
                today = date
                claims.value = ClaimState()
                refreshRewards()
            }
            .launchIn(viewModelScope)
    }

    fun onPermissionChecked(granted: Boolean) {
        permissionGranted.value = granted
    }

    fun onGoalChange(goal: Int) {
        viewModelScope.launch { goalRepository.setGoal(goal) }
    }

    /** 화면에 다시 들어올 때 다른 기기·워커가 바꾼 서버 상태를 반영한다 */
    fun onResume() {
        viewModelScope.launch { refreshRewards() }
    }

    fun onRetryRewards() = onResume()

    /**
     * 1) 응답을 기다리는 중이거나 이미 받은 구간이면 무시 (연타 방지)
     * 2) 낙관적 반영: 바로 "받음"으로 보여준다
     * 3) 서버는 자기가 받은 걸음으로만 판정하므로(PRD Q5) 최신 걸음을 먼저 올린다
     * 4) 실패하면 되돌리고 이유를 알려준다. 서버 수령은 멱등이라 재시도해도 중복 적립되지 않는다
     */
    fun onClaim(tier: Int) {
        val date = today ?: return
        val current = claims.value
        if (tier in current.inFlight || tier in current.claimed || isServerClaimed(tier)) return
        claims.update { it.copy(inFlight = it.inFlight + tier) }

        viewModelScope.launch {
            val result = stepRepository.syncPendingSteps().fold(
                onSuccess = { rewardRepository.claim(date, tier) },
                onFailure = { Result.failure(it) },
            )
            claims.update { state ->
                val claimed = if (result.isSuccess) state.claimed + tier else state.claimed
                state.copy(inFlight = state.inFlight - tier, claimed = claimed)
            }
            result
                .onSuccess { claim ->
                    _effects.send(HomeEffect.ShowMessage(claim.message()))
                    refreshRewards()
                }
                .onFailure { error -> _effects.send(HomeEffect.ShowMessage(error.toClaimErrorText())) }
        }
    }

    private suspend fun refreshRewards() {
        rewardRepository.getTodayRewards()
            .onSuccess { serverRewards.value = RewardsLoad.Loaded(it) }
            // 이미 받아 둔 상태가 있으면 잠깐의 실패로 화면을 오류로 바꾸지 않는다
            .onFailure { if (serverRewards.value !is RewardsLoad.Loaded) serverRewards.value = RewardsLoad.Error }
    }

    private fun isServerClaimed(tier: Int): Boolean {
        val loaded = serverRewards.value as? RewardsLoad.Loaded ?: return false
        return loaded.rewards.tiers.any { it.tier == tier && it.status == RewardStatus.CLAIMED }
    }

    /**
     * "받음" 은 서버 기준, "받기 가능" 은 기기 걸음 기준.
     * 서버 걸음은 동기화 주기만큼 늦으므로 서버 상태만 보면 이미 걸은 구간이 한동안 잠겨 보인다.
     */
    private fun rewardsSection(load: RewardsLoad, claims: ClaimState, localSteps: Int): RewardsSection = when (load) {
        RewardsLoad.Loading -> RewardsSection.Loading
        RewardsLoad.Error -> RewardsSection.Error
        is RewardsLoad.Loaded -> RewardsSection.Loaded(
            load.rewards.tiers.map { tier ->
                val state = when {
                    tier.status == RewardStatus.CLAIMED || tier.tier in claims.claimed || tier.tier in claims.inFlight ->
                        RewardTierState.CLAIMED
                    localSteps >= tier.requiredSteps -> RewardTierState.CLAIMABLE
                    else -> RewardTierState.LOCKED
                }
                RewardTierUi(tier.tier, tier.requiredSteps, tier.points, state)
            }.toImmutableList(),
        )
    }

    private fun ClaimResult.message(): UiText =
        if (alreadyClaimed) {
            UiText.Resource(R.string.reward_already_claimed)
        } else {
            UiText.Resource(R.string.reward_claimed, persistentListOf(points, balance))
        }

    private fun Throwable.toClaimErrorText(): UiText = when ((this as? AppError.InvalidRequest)?.code) {
        "REWARD_NOT_REACHED" -> UiText.Resource(R.string.reward_error_not_reached)
        "REWARD_NOT_TODAY" -> UiText.Resource(R.string.reward_error_not_today)
        else -> toUiText()
    }

    private sealed interface RewardsLoad {
        data object Loading : RewardsLoad
        data object Error : RewardsLoad
        data class Loaded(val rewards: TodayRewards) : RewardsLoad
    }

    /** [inFlight]: 응답을 기다리는 구간(낙관적으로 받음 표시), [claimed]: 오늘 이 화면에서 받은 구간 */
    private data class ClaimState(val inFlight: Set<Int> = emptySet(), val claimed: Set<Int> = emptySet())

    companion object {
        val GOAL_OPTIONS: List<Int> = listOf(3_000, 5_000, 6_000, 8_000, 10_000, 12_000, 15_000, 20_000)
    }
}
