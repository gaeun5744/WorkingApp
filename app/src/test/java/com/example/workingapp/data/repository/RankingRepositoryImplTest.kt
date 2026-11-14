package com.example.workingapp.data.repository

import com.example.workingapp.data.remote.api.PedometerApi
import com.example.workingapp.data.remote.dto.AnonymousSignUpRequest
import com.example.workingapp.data.remote.dto.UpdateRegionRequest
import com.example.workingapp.data.remote.mock.MockApiConfig
import com.example.workingapp.data.remote.mock.MockApiInterceptor
import com.example.workingapp.data.remote.mock.MockNeighborSteps
import com.example.workingapp.data.remote.mock.MockServer
import com.example.workingapp.data.remote.mock.MockTreasureField
import com.example.workingapp.data.remote.mock.MutableClock
import com.example.workingapp.domain.model.AppError
import com.example.workingapp.domain.model.RankingPeriod
import com.example.workingapp.testing.FakeStepRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Duration
import java.time.OffsetDateTime

/** Mock 경로로 실제 `/ranking` 요청 횟수를 세어 5분 캐시 규칙을 검증한다. */
class RankingRepositoryImplTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val clock = MutableClock(OffsetDateTime.parse("2026-10-26T14:00:00+09:00").toInstant())
    private val stepRepository = FakeStepRepository()
    private var token: String? = null
    private val rankingRequests = mutableListOf<String>()
    private val api: PedometerApi = run {
        val config = MockApiConfig(LongRange.EMPTY, 0.0, Duration.ofDays(1), Duration.ofDays(30))
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.encodedPath == "/ranking") rankingRequests += request.url.queryParameter("period").orEmpty()
                chain.proceed(token?.let { request.newBuilder().header("Authorization", "Bearer $it").build() } ?: request)
            }
            .addInterceptor(MockApiInterceptor(MockServer(clock, config, MockNeighborSteps(), MockTreasureField()), config, json))
            .build()
        Retrofit.Builder()
            .baseUrl("https://mock.workingapp.local/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PedometerApi::class.java)
    }
    private val repository = RankingRepositoryImpl(api, stepRepository, json, clock)

    private suspend fun signUp(regionId: Long? = 1) {
        token = api.signUpAnonymous(AnonymousSignUpRequest("걷는토끼")).accessToken
        regionId?.let { api.updateRegion(UpdateRegionRequest(it)) }
    }

    @Test
    fun `5분 안에 다시 요청하면 캐시를 쓰고, 걸음 동기화도 다시 하지 않는다`() = runTest {
        signUp()
        val first = repository.getRanking(RankingPeriod.DAILY).getOrThrow()
        clock.advance(Duration.ofMinutes(4))
        val second = repository.getRanking(RankingPeriod.DAILY).getOrThrow()

        assertEquals(listOf("daily"), rankingRequests)
        assertEquals(1, stepRepository.syncCount)
        assertEquals(first, second)
        assertEquals("역삼동", first.regionName)
        assertTrue(first.entries.size <= 50)
    }

    @Test
    fun `5분이 지나면 새로 받는다`() = runTest {
        signUp()
        repository.getRanking(RankingPeriod.DAILY)
        clock.advance(Duration.ofMinutes(5))
        repository.getRanking(RankingPeriod.DAILY)

        assertEquals(listOf("daily", "daily"), rankingRequests)
        assertEquals(2, stepRepository.syncCount)
    }

    @Test
    fun `강제 새로고침은 캐시를 무시한다`() = runTest {
        signUp()
        repository.getRanking(RankingPeriod.DAILY)
        repository.getRanking(RankingPeriod.DAILY, forceRefresh = true)

        assertEquals(2, rankingRequests.size)
    }

    @Test
    fun `일간과 주간은 따로 캐시한다`() = runTest {
        signUp()
        repository.getRanking(RankingPeriod.DAILY)
        repository.getRanking(RankingPeriod.WEEKLY)
        repository.getRanking(RankingPeriod.DAILY)
        repository.getRanking(RankingPeriod.WEEKLY)

        assertEquals(listOf("daily", "weekly"), rankingRequests)
    }

    @Test
    fun `동네를 바꾸면 캐시를 비워 새 동네 랭킹을 받는다`() = runTest {
        signUp()
        repository.getRanking(RankingPeriod.DAILY)
        api.updateRegion(UpdateRegionRequest(8))
        repository.invalidate()

        assertEquals("성수동", repository.getRanking(RankingPeriod.DAILY).getOrThrow().regionName)
        assertEquals(2, rankingRequests.size)
    }

    @Test
    fun `실패한 응답은 캐시하지 않는다`() = runTest {
        signUp(regionId = null)
        val error = repository.getRanking(RankingPeriod.DAILY).exceptionOrNull()
        assertEquals(AppError.InvalidRequest("REGION_NOT_SET"), error)

        api.updateRegion(UpdateRegionRequest(1))
        assertTrue(repository.getRanking(RankingPeriod.DAILY).isSuccess)
        assertEquals(2, rankingRequests.size)
    }
}
