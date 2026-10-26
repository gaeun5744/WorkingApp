package com.example.workingapp.presentation.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workingapp.R
import com.example.workingapp.domain.model.AppError
import com.example.workingapp.domain.model.Ranking
import com.example.workingapp.domain.model.RankingEntry
import com.example.workingapp.domain.model.RankingPeriod
import com.example.workingapp.domain.model.Region
import com.example.workingapp.domain.repository.AccountRepository
import com.example.workingapp.domain.repository.RankingRepository
import com.example.workingapp.presentation.text.UiText
import com.example.workingapp.presentation.text.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class RankingViewModel @Inject constructor(
    private val rankingRepository: RankingRepository,
    private val accountRepository: AccountRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RankingUiState())
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    private val _effects = Channel<RankingEffect>(Channel.BUFFERED)
    val effects: Flow<RankingEffect> = _effects.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load(forceRefresh = false)
    }

    fun onPeriodSelected(period: RankingPeriod) {
        if (period == _uiState.value.period) return
        _uiState.update { it.copy(period = period, content = RankingContent.Loading) }
        load(forceRefresh = false)
    }

    fun onRefresh() = load(forceRefresh = true)

    fun onRetry() = load(forceRefresh = true)

    fun onRegionSelected(region: Region) {
        viewModelScope.launch {
            accountRepository.updateRegion(region)
                .onSuccess {
                    rankingRepository.invalidate()
                    _uiState.update { it.copy(content = RankingContent.Loading) }
                    load(forceRefresh = true)
                }
                .onFailure { _effects.send(RankingEffect.ShowMessage(it.toUiText())) }
        }
    }

    // 탭을 빠르게 바꾸면 이전 기간 응답이 늦게 도착해 덮어쓰지 않도록 이전 요청을 취소한다
    private fun load(forceRefresh: Boolean) {
        loadJob?.cancel()
        val period = _uiState.value.period
        val hasContent = _uiState.value.content is RankingContent.Loaded
        _uiState.update { it.copy(isRefreshing = forceRefresh && hasContent) }
        loadJob = viewModelScope.launch {
            rankingRepository.getRanking(period, forceRefresh)
                .onSuccess { ranking -> _uiState.update { it.copy(content = ranking.toContent(), isRefreshing = false) } }
                .onFailure { error -> onLoadFailed(error) }
        }
    }

    private suspend fun onLoadFailed(error: Throwable) {
        // 이미 보이는 목록이 있으면 오류 화면으로 바꾸지 않고 안내만 한다
        if (_uiState.value.content is RankingContent.Loaded) {
            _uiState.update { it.copy(isRefreshing = false) }
            _effects.send(RankingEffect.ShowMessage(error.toUiText()))
            return
        }
        val regionNotSet = (error as? AppError.InvalidRequest)?.code == "REGION_NOT_SET"
        val message = if (regionNotSet) UiText.Resource(R.string.ranking_region_not_set) else error.toUiText()
        _uiState.update { it.copy(content = RankingContent.Error(message, regionNotSet), isRefreshing = false) }
    }

    private fun Ranking.toContent(): RankingContent.Loaded = RankingContent.Loaded(
        regionName = regionName,
        entries = entries.map { it.toUi(isMe = it.userId == me.userId) }.toImmutableList(),
        me = me.toUi(isMe = true),
        // LocalTime.ofInstant 는 API 31+ 라 minSdk 30 에서 쓸 수 있는 atZone 으로 변환한다
        fetchedAt = fetchedAt.atZone(clock.zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES),
    )

    private fun RankingEntry.toUi(isMe: Boolean) = RankingEntryUi(rank, userId, nickname, steps, isMe)
}
