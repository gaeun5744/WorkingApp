package com.example.workingapp.presentation.ranking

import app.cash.turbine.test
import com.example.workingapp.R
import com.example.workingapp.domain.model.AppError
import com.example.workingapp.domain.model.RankingPeriod
import com.example.workingapp.domain.model.Region
import com.example.workingapp.presentation.text.UiText
import com.example.workingapp.testing.FakeAccountRepository
import com.example.workingapp.testing.FakeRankingRepository
import com.example.workingapp.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId

class RankingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val rankings = FakeRankingRepository()
    private val accounts = FakeAccountRepository()
    private val clock = Clock.systemUTC().withZone(ZoneId.of("Asia/Seoul"))
    // MainDispatcherRule 이 Main 을 바꾼 뒤에 만들어야 init 의 로드가 테스트 디스패처에서 돈다
    private val viewModel by lazy { RankingViewModel(rankings, accounts, clock) }

    private fun loaded() = viewModel.uiState.value.content as RankingContent.Loaded

    @Test
    fun `처음에는 일간 랭킹을 캐시 허용으로 부르고, 내 행을 표시한다`() {
        val content = loaded()

        assertEquals(listOf(RankingPeriod.DAILY to false), rankings.calls)
        assertEquals("역삼동", content.regionName)
        assertEquals(listOf(false, true, false), content.entries.map { it.isMe })
        assertEquals(2, content.me.rank)
        assertEquals(LocalTime.of(21, 30), content.fetchedAt)
    }

    @Test
    fun `주간 탭을 고르면 주간을 부른다`() {
        viewModel.onPeriodSelected(RankingPeriod.WEEKLY)

        assertEquals(RankingPeriod.WEEKLY, viewModel.uiState.value.period)
        assertEquals(RankingPeriod.WEEKLY, rankings.calls.last().first)
    }

    @Test
    fun `당겨서 새로고침은 캐시를 무시한다`() {
        viewModel.uiState.value
        viewModel.onRefresh()

        assertEquals(RankingPeriod.DAILY to true, rankings.calls.last())
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `동네를 바꾸면 저장하고 캐시를 비운 뒤 새 동네 랭킹을 받는다`() {
        viewModel.uiState.value
        rankings.result = { Result.success(FakeRankingRepository.ranking(it, regionName = "성수동")) }

        viewModel.onRegionSelected(Region(8, "성수동"))

        assertEquals(listOf(Region(8, "성수동")), accounts.updatedRegions)
        assertEquals(1, rankings.invalidations)
        assertEquals("성수동", loaded().regionName)
    }

    @Test
    fun `동네가 설정되지 않았으면 동네 선택을 안내한다`() {
        rankings.result = { Result.failure(AppError.InvalidRequest("REGION_NOT_SET")) }

        val error = viewModel.uiState.value.content as RankingContent.Error
        assertTrue(error.regionNotSet)
    }

    @Test
    fun `이미 목록이 있을 때 새로고침이 실패하면 목록은 두고 안내만 한다`() = runTest {
        viewModel.uiState.value
        rankings.result = { Result.failure(AppError.Network) }

        viewModel.effects.test {
            viewModel.onRefresh()
            assertEquals(RankingEffect.ShowMessage(UiText.Resource(R.string.error_network)), awaitItem())
        }
        assertEquals("역삼동", loaded().regionName)
    }
}
