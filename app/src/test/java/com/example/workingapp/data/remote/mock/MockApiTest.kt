package com.example.workingapp.data.remote.mock

import com.example.workingapp.data.remote.api.PedometerApi
import com.example.workingapp.data.remote.dto.AnonymousSignUpRequest
import com.example.workingapp.data.remote.dto.DailyStepsDto
import com.example.workingapp.data.remote.dto.ErrorResponse
import com.example.workingapp.data.remote.dto.HourlyStepsDto
import com.example.workingapp.data.remote.dto.RefreshTokenRequest
import com.example.workingapp.data.remote.dto.RewardClaimRequest
import com.example.workingapp.data.remote.dto.RewardStatusDto
import com.example.workingapp.data.remote.dto.StepsSyncRequest
import com.example.workingapp.data.remote.dto.SyncStatusDto
import com.example.workingapp.data.remote.dto.UpdateRegionRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Duration
import java.time.OffsetDateTime

/** Retrofit → OkHttp → MockApiInterceptor 전체 경로로 `docs/api.md` 규칙을 검증한다. */
class MockApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    // 2026-10-25(일) 14:00 KST
    private val clock = MutableClock(OffsetDateTime.parse("2026-10-25T14:00:00+09:00").toInstant())
    private lateinit var api: PedometerApi

    @Before
    fun setUp() {
        val config = MockApiConfig(
            latencyMillis = LongRange.EMPTY,
            failureRate = 0.0,
            accessTokenTtl = Duration.ofMinutes(10),
            refreshTokenTtl = Duration.ofDays(30),
        )
        api = createApi(config)
    }

    private fun createApi(config: MockApiConfig): PedometerApi {
        val server = MockServer(clock, config, MockNeighborSteps())
        val client = OkHttpClient.Builder().addInterceptor(MockApiInterceptor(server, config, json)).build()
        return Retrofit.Builder()
            .baseUrl("https://mock.workingapp.local/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PedometerApi::class.java)
    }

    private suspend fun signUp(): String =
        "Bearer " + api.signUpAnonymous(AnonymousSignUpRequest("걷는토끼")).accessToken

    private suspend fun assertError(httpStatus: Int, code: String, block: suspend () -> Unit) {
        try {
            block()
            fail("$httpStatus $code 에러가 발생해야 합니다")
        } catch (e: HttpException) {
            assertEquals(httpStatus, e.code())
            val body = json.decodeFromString(ErrorResponse.serializer(), e.response()?.errorBody()?.string().orEmpty())
            assertEquals(code, body.code)
        }
    }

    private fun day(date: String, steps: Int, hourly: List<HourlyStepsDto> = emptyList()) =
        StepsSyncRequest(listOf(DailyStepsDto(date, steps, hourly)))

    // region 인증

    @Test
    fun `토큰 없이 호출하면 401 UNAUTHORIZED`() = runTest {
        assertError(401, "UNAUTHORIZED") { api.getMe("Bearer unknown") }
    }

    @Test
    fun `액세스 토큰이 만료되면 401 TOKEN_EXPIRED`() = runTest {
        val auth = signUp()
        clock.advance(Duration.ofMinutes(11))
        assertError(401, "TOKEN_EXPIRED") { api.getMe(auth) }
    }

    @Test
    fun `리프레시는 새 토큰을 발급하고 사용한 리프레시 토큰은 폐기한다`() = runTest {
        val first = api.signUpAnonymous(AnonymousSignUpRequest("걷는토끼"))
        val second = api.refreshToken(RefreshTokenRequest(first.refreshToken))

        assertEquals(first.userId, second.userId)
        assertNotEquals(first.refreshToken, second.refreshToken)
        assertEquals("걷는토끼", api.getMe("Bearer " + second.accessToken).nickname)
        assertError(401, "INVALID_REFRESH_TOKEN") { api.refreshToken(RefreshTokenRequest(first.refreshToken)) }
    }

    @Test
    fun `닉네임이 비어 있으면 400 VALIDATION_ERROR`() = runTest {
        assertError(400, "VALIDATION_ERROR") { api.signUpAnonymous(AnonymousSignUpRequest("  ")) }
    }

    // endregion

    // region 걸음

    @Test
    fun `같은 날짜는 더 큰 값만 반영되어 재전송해도 결과가 같다`() = runTest {
        val auth = signUp()
        api.syncSteps(auth, day("2026-10-25", 5_000))
        api.syncSteps(auth, day("2026-10-25", 3_000))
        api.syncSteps(auth, day("2026-10-25", 5_000))

        assertEquals(5_000, api.getTodayRewards(auth).steps)
    }

    @Test
    fun `시간당 상한과 미래 시간대는 잘라내고 CAPPED 로 응답한다`() = runTest {
        val auth = signUp()
        val hourly = listOf(
            HourlyStepsDto(hour = 9, steps = 15_000), // 12,000 으로 잘림
            HourlyStepsDto(hour = 13, steps = 1_000),
            HourlyStepsDto(hour = 15, steps = 2_000), // 현재 14시 → 미래 시간대라 0
        )
        val result = api.syncSteps(auth, day("2026-10-25", 18_000, hourly)).results.single()

        assertEquals(SyncStatusDto.CAPPED, result.status)
        assertEquals(13_000, result.acceptedSteps)
    }

    @Test
    fun `미래 날짜와 7일보다 오래된 날짜는 REJECTED`() = runTest {
        val auth = signUp()
        val request = StepsSyncRequest(
            listOf(
                DailyStepsDto("2026-10-26", 1_000, emptyList()),
                DailyStepsDto("2026-10-18", 1_000, emptyList()),
                DailyStepsDto("2026-10-19", 1_000, emptyList()),
            ),
        )
        val statuses = api.syncSteps(auth, request).results.map { it.status }

        assertEquals(listOf(SyncStatusDto.REJECTED, SyncStatusDto.REJECTED, SyncStatusDto.ACCEPTED), statuses)
    }

    @Test
    fun `날짜 형식이 하나라도 틀리면 아무것도 반영하지 않는다`() = runTest {
        val auth = signUp()
        val request = StepsSyncRequest(
            listOf(
                DailyStepsDto("2026-10-25", 5_000, emptyList()),
                DailyStepsDto("10/25", 1_000, emptyList()),
            ),
        )
        assertError(400, "VALIDATION_ERROR") { api.syncSteps(auth, request) }
        assertEquals(0, api.getTodayRewards(auth).steps)
    }

    // endregion

    // region 리워드

    @Test
    fun `같은 구간을 다시 받으면 포인트를 더 주지 않고 alreadyClaimed 로 응답한다`() = runTest {
        val auth = signUp()
        api.syncSteps(auth, day("2026-10-25", 5_200))

        val first = api.claimReward(auth, RewardClaimRequest("2026-10-25", tier = 3))
        val second = api.claimReward(auth, RewardClaimRequest("2026-10-25", tier = 3))

        assertFalse(first.alreadyClaimed)
        assertTrue(second.alreadyClaimed)
        assertEquals(5, first.balance)
        assertEquals(5, second.balance)
        assertEquals(1, api.getWallet(auth).transactions.size)
        assertEquals(RewardStatusDto.CLAIMED, api.getTodayRewards(auth).tiers.first { it.tier == 3 }.status)
    }

    @Test
    fun `구간 미달이면 REWARD_NOT_REACHED, 오늘이 아니면 REWARD_NOT_TODAY`() = runTest {
        val auth = signUp()
        api.syncSteps(auth, day("2026-10-25", 2_000))

        assertError(400, "REWARD_NOT_REACHED") { api.claimReward(auth, RewardClaimRequest("2026-10-25", tier = 2)) }
        assertError(400, "REWARD_NOT_TODAY") { api.claimReward(auth, RewardClaimRequest("2026-10-24", tier = 1)) }
    }

    // endregion

    // region 랭킹

    @Test
    fun `동네를 설정하지 않으면 랭킹은 409 REGION_NOT_SET`() = runTest {
        val auth = signUp()
        assertError(409, "REGION_NOT_SET") { api.getRanking(auth, "daily") }
    }

    @Test
    fun `랭킹은 걸음 내림차순이고 내 순위를 포함한다`() = runTest {
        val auth = signUp()
        api.updateRegion(auth, UpdateRegionRequest(regionId = 1))
        api.syncSteps(auth, day("2026-10-25", 99_000))

        val daily = api.getRanking(auth, "daily")
        val weekly = api.getRanking(auth, "weekly")

        assertEquals(1, daily.me.rank)
        assertEquals(daily.entries.sortedByDescending { it.steps }, daily.entries)
        assertEquals("2026-10-19", weekly.periodStart) // 이번 주 월요일
        assertTrue(weekly.entries.size <= 50)
    }

    // endregion

    @Test
    fun `failureRate 가 1 이면 모든 요청이 500 INTERNAL_ERROR`() = runTest {
        val failingApi = createApi(
            MockApiConfig(LongRange.EMPTY, failureRate = 1.0, Duration.ofMinutes(10), Duration.ofDays(30)),
        )
        assertError(500, "INTERNAL_ERROR") { failingApi.signUpAnonymous(AnonymousSignUpRequest("걷는토끼")) }
    }
}
