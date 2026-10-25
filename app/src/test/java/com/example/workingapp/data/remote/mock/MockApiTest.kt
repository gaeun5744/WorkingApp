package com.example.workingapp.data.remote.mock

import com.example.workingapp.data.remote.api.AuthApi
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
import retrofit2.Call
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
    private lateinit var authApi: AuthApi
    private var token: String? = null

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

    private fun <T> Call<T>.executeOrThrow(): T {
        val response = execute()
        return response.body() ?: throw HttpException(response)
    }

    private fun createApi(config: MockApiConfig): PedometerApi {
        val retrofit = createRetrofit(config)
        authApi = retrofit.create(AuthApi::class.java)
        return retrofit.create(PedometerApi::class.java)
    }

    private fun createRetrofit(config: MockApiConfig): Retrofit {
        val server = MockServer(clock, config, MockNeighborSteps())
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val authorized = token?.let { request.newBuilder().header("Authorization", "Bearer $it").build() }
                chain.proceed(authorized ?: request)
            }
            .addInterceptor(MockApiInterceptor(server, config, json))
            .build()
        return Retrofit.Builder()
            .baseUrl("https://mock.workingapp.local/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private suspend fun signUp() {
        token = api.signUpAnonymous(AnonymousSignUpRequest("걷는토끼")).accessToken
    }

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
        token = "unknown"
        assertError(401, "UNAUTHORIZED") { api.getMe() }
    }

    @Test
    fun `액세스 토큰이 만료되면 401 TOKEN_EXPIRED`() = runTest {
        signUp()
        clock.advance(Duration.ofMinutes(11))
        assertError(401, "TOKEN_EXPIRED") { api.getMe() }
    }

    @Test
    fun `리프레시는 새 토큰을 발급하고 사용한 리프레시 토큰은 폐기한다`() = runTest {
        val first = api.signUpAnonymous(AnonymousSignUpRequest("걷는토끼"))
        val second = authApi.refreshToken(RefreshTokenRequest(first.refreshToken)).executeOrThrow()

        assertEquals(first.userId, second.userId)
        assertNotEquals(first.refreshToken, second.refreshToken)
        token = second.accessToken
        assertEquals("걷는토끼", api.getMe().nickname)
        assertError(401, "INVALID_REFRESH_TOKEN") { authApi.refreshToken(RefreshTokenRequest(first.refreshToken)).executeOrThrow() }
    }

    @Test
    fun `닉네임이 비어 있으면 400 VALIDATION_ERROR`() = runTest {
        assertError(400, "VALIDATION_ERROR") { api.signUpAnonymous(AnonymousSignUpRequest("  ")) }
    }

    // endregion

    // region 걸음

    @Test
    fun `같은 날짜는 더 큰 값만 반영되어 재전송해도 결과가 같다`() = runTest {
        signUp()
        api.syncSteps(day("2026-10-25", 5_000))
        api.syncSteps(day("2026-10-25", 3_000))
        api.syncSteps(day("2026-10-25", 5_000))

        assertEquals(5_000, api.getTodayRewards().steps)
    }

    @Test
    fun `시간당 상한과 미래 시간대는 잘라내고 CAPPED 로 응답한다`() = runTest {
        signUp()
        val hourly = listOf(
            HourlyStepsDto(hour = 9, steps = 15_000), // 12,000 으로 잘림
            HourlyStepsDto(hour = 13, steps = 1_000),
            HourlyStepsDto(hour = 15, steps = 2_000), // 현재 14시 → 미래 시간대라 0
        )
        val result = api.syncSteps(day("2026-10-25", 18_000, hourly)).results.single()

        assertEquals(SyncStatusDto.CAPPED, result.status)
        assertEquals(13_000, result.acceptedSteps)
    }

    @Test
    fun `미래 날짜와 7일보다 오래된 날짜는 REJECTED`() = runTest {
        signUp()
        val request = StepsSyncRequest(
            listOf(
                DailyStepsDto("2026-10-26", 1_000, emptyList()),
                DailyStepsDto("2026-10-18", 1_000, emptyList()),
                DailyStepsDto("2026-10-19", 1_000, emptyList()),
            ),
        )
        val statuses = api.syncSteps(request).results.map { it.status }

        assertEquals(listOf(SyncStatusDto.REJECTED, SyncStatusDto.REJECTED, SyncStatusDto.ACCEPTED), statuses)
    }

    @Test
    fun `날짜 형식이 하나라도 틀리면 아무것도 반영하지 않는다`() = runTest {
        signUp()
        val request = StepsSyncRequest(
            listOf(
                DailyStepsDto("2026-10-25", 5_000, emptyList()),
                DailyStepsDto("10/25", 1_000, emptyList()),
            ),
        )
        assertError(400, "VALIDATION_ERROR") { api.syncSteps(request) }
        assertEquals(0, api.getTodayRewards().steps)
    }

    // endregion

    // region 리워드

    @Test
    fun `같은 구간을 다시 받으면 포인트를 더 주지 않고 alreadyClaimed 로 응답한다`() = runTest {
        signUp()
        api.syncSteps(day("2026-10-25", 5_200))

        val first = api.claimReward(RewardClaimRequest("2026-10-25", tier = 3))
        val second = api.claimReward(RewardClaimRequest("2026-10-25", tier = 3))

        assertFalse(first.alreadyClaimed)
        assertTrue(second.alreadyClaimed)
        assertEquals(5, first.balance)
        assertEquals(5, second.balance)
        assertEquals(1, api.getWallet().transactions.size)
        assertEquals(RewardStatusDto.CLAIMED, api.getTodayRewards().tiers.first { it.tier == 3 }.status)
    }

    @Test
    fun `구간 미달이면 REWARD_NOT_REACHED, 오늘이 아니면 REWARD_NOT_TODAY`() = runTest {
        signUp()
        api.syncSteps(day("2026-10-25", 2_000))

        assertError(400, "REWARD_NOT_REACHED") { api.claimReward(RewardClaimRequest("2026-10-25", tier = 2)) }
        assertError(400, "REWARD_NOT_TODAY") { api.claimReward(RewardClaimRequest("2026-10-24", tier = 1)) }
    }

    // endregion

    // region 랭킹

    @Test
    fun `동네를 설정하지 않으면 랭킹은 409 REGION_NOT_SET`() = runTest {
        signUp()
        assertError(409, "REGION_NOT_SET") { api.getRanking("daily") }
    }

    @Test
    fun `랭킹은 걸음 내림차순이고 내 순위를 포함한다`() = runTest {
        signUp()
        api.updateRegion(UpdateRegionRequest(regionId = 1))
        api.syncSteps(day("2026-10-25", 99_000))

        val daily = api.getRanking("daily")
        val weekly = api.getRanking("weekly")

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
