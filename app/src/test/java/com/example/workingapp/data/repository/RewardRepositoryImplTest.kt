package com.example.workingapp.data.repository

import com.example.workingapp.data.remote.api.PedometerApi
import com.example.workingapp.data.remote.dto.AnonymousSignUpRequest
import com.example.workingapp.data.remote.dto.DailyStepsDto
import com.example.workingapp.data.remote.dto.StepsSyncRequest
import com.example.workingapp.data.remote.mock.MockApiConfig
import com.example.workingapp.data.remote.mock.MockApiInterceptor
import com.example.workingapp.data.remote.mock.MockNeighborSteps
import com.example.workingapp.data.remote.mock.MockServer
import com.example.workingapp.data.remote.mock.MutableClock
import com.example.workingapp.domain.model.AppError
import com.example.workingapp.domain.model.RewardStatus
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
import java.time.LocalDate
import java.time.OffsetDateTime

/** Retrofit → Mock 경로로 DTO → 도메인 매핑과 오류 정규화를 확인한다. */
class RewardRepositoryImplTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val clock = MutableClock(OffsetDateTime.parse("2026-10-25T14:00:00+09:00").toInstant())
    private val today = LocalDate.of(2026, 10, 25)
    private var token: String? = null
    private val api: PedometerApi = run {
        val config = MockApiConfig(LongRange.EMPTY, 0.0, Duration.ofMinutes(10), Duration.ofDays(30))
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(token?.let { request.newBuilder().header("Authorization", "Bearer $it").build() } ?: request)
            }
            .addInterceptor(MockApiInterceptor(MockServer(clock, config, MockNeighborSteps()), config, json))
            .build()
        Retrofit.Builder()
            .baseUrl("https://mock.workingapp.local/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PedometerApi::class.java)
    }
    private val repository = RewardRepositoryImpl(api, json)

    private suspend fun signUpWithSteps(steps: Int) {
        token = api.signUpAnonymous(AnonymousSignUpRequest("걷는토끼")).accessToken
        api.syncSteps(StepsSyncRequest(listOf(DailyStepsDto(today.toString(), steps, emptyList()))))
    }

    @Test
    fun `오늘 리워드와 수령 결과, 지갑 내역을 도메인 모델로 바꾼다`() = runTest {
        signUpWithSteps(5_200)

        val rewards = repository.getTodayRewards().getOrThrow()
        assertEquals(5_200, rewards.serverSteps)
        assertEquals(RewardStatus.CLAIMABLE, rewards.tiers.first { it.tier == 3 }.status)

        val claim = repository.claim(today, 3).getOrThrow()
        assertEquals(5, claim.balance)

        val wallet = repository.getWallet().getOrThrow()
        assertEquals(3, wallet.transactions.single().rewardTier)
        assertEquals(OffsetDateTime.parse("2026-10-25T14:00:00+09:00"), wallet.transactions.single().createdAt)
    }

    @Test
    fun `구간 미달 거절은 에러 코드가 담긴 InvalidRequest 로 바꾼다`() = runTest {
        signUpWithSteps(500)

        val error = repository.claim(today, 1).exceptionOrNull()

        assertTrue(error is AppError.InvalidRequest)
        assertEquals("REWARD_NOT_REACHED", (error as AppError.InvalidRequest).code)
    }
}
