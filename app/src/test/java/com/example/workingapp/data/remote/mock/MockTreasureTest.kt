package com.example.workingapp.data.remote.mock

import com.example.workingapp.data.remote.api.PedometerApi
import com.example.workingapp.data.remote.dto.AnonymousSignUpRequest
import com.example.workingapp.data.remote.dto.ErrorResponse
import com.example.workingapp.data.remote.dto.TreasureDto
import com.example.workingapp.data.remote.dto.TreasureOpenRequest
import com.example.workingapp.domain.geo.GeoCell
import com.example.workingapp.domain.geo.distanceMetersTo
import com.example.workingapp.domain.geo.offsetMeters
import com.example.workingapp.domain.model.GeoPoint
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Duration
import java.time.OffsetDateTime

/** 격자 칸 기반 보물상자 생성·열기 검증 규칙 (`docs/api.md` 보물상자 절). */
class MockTreasureTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val clock = MutableClock(OffsetDateTime.parse("2026-10-26T14:00:00+09:00").toInstant())
    private var token: String? = null
    private val api: PedometerApi = run {
        // 날짜를 넘기는 테스트가 있어 토큰 만료가 끼어들지 않게 길게 둔다
        val config = MockApiConfig(LongRange.EMPTY, 0.0, Duration.ofDays(3), Duration.ofDays(30))
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
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

    // 동네를 정하지 않아도 보물상자는 쓸 수 있다 (동네는 랭킹 전용)
    private suspend fun signUp() {
        token = api.signUpAnonymous(AnonymousSignUpRequest("걷는토끼")).accessToken
    }

    private suspend fun nearby(point: GeoPoint = YEOKSAM) = api.getNearbyTreasures(point.latitude, point.longitude)

    private fun TreasureDto.point() = GeoPoint(latitude, longitude)

    private fun at(point: GeoPoint, accuracy: Double = 10.0, isMock: Boolean = false) = TreasureOpenRequest(
        latitude = point.latitude,
        longitude = point.longitude,
        accuracyMeters = accuracy,
        isMock = isMock,
        measuredAt = "2026-10-26T14:00:00+09:00",
    )

    private suspend fun assertError(httpStatus: Int, code: String, block: suspend () -> Unit) {
        try {
            block()
            fail("$httpStatus $code 에러가 발생해야 합니다")
        } catch (e: HttpException) {
            assertEquals(httpStatus, e.code())
            assertEquals(code, json.decodeFromString(ErrorResponse.serializer(), e.response()?.errorBody()?.string().orEmpty()).code)
        }
    }

    @Test
    fun `동네 설정 없이도 어디서나 주변 3x3칸에 칸마다 2개씩 상자가 있다`() = runTest {
        signUp()
        for (place in listOf(YEOKSAM, BUSAN, JEJU)) {
            val treasures = nearby(place).treasures
            val cells = treasures.map { GeoCell.of(it.point()) }.toSet()

            assertEquals(18, treasures.size)
            assertEquals(GeoCell.of(place).withNeighbors().toSet(), cells)
        }
    }

    @Test
    fun `같은 장소·같은 날이면 누구에게나 같은 상자이고, 같은 칸 안 어디서 불러도 같다`() = runTest {
        signUp()
        val mine = nearby(YEOKSAM).treasures
        signUp()
        val neighbor = nearby(YEOKSAM.offsetMeters(northMeters = 20.0, eastMeters = 20.0)).treasures

        assertEquals(mine, neighbor)
    }

    @Test
    fun `날짜가 바뀌면 상자 위치도 바뀐다`() = runTest {
        signUp()
        val today = nearby().treasures.map { it.point() }
        clock.advance(Duration.ofDays(1))

        assertTrue(today.intersect(nearby().treasures.map { it.point() }.toSet()).isEmpty())
    }

    @Test
    fun `50m 안에서 열면 2P 적립, 다시 열면 위치와 관계없이 alreadyOpened`() = runTest {
        signUp()
        val treasure = nearby().treasures.first()

        val first = api.openTreasure(treasure.id, at(treasure.point().offsetMeters(northMeters = 30.0, eastMeters = 0.0)))
        val again = api.openTreasure(treasure.id, at(treasure.point().offsetMeters(northMeters = 5_000.0, eastMeters = 0.0)))

        assertEquals(2, first.balance)
        assertFalse(first.alreadyOpened)
        assertTrue(again.alreadyOpened)
        assertEquals(2, again.balance)
        val after = nearby()
        assertTrue(after.treasures.first { it.id == treasure.id }.opened)
        assertEquals(1, after.openedToday)
        assertEquals("TREASURE", api.getWallet().transactions.single().reason)
    }

    @Test
    fun `50m 밖, 가짜 GPS, 정확도 50m 초과는 거부한다`() = runTest {
        signUp()
        val treasure = nearby().treasures.first()
        val near = treasure.point().offsetMeters(northMeters = 10.0, eastMeters = 0.0)

        assertError(400, "TREASURE_TOO_FAR") { api.openTreasure(treasure.id, at(treasure.point().offsetMeters(60.0, 0.0))) }
        assertError(400, "TREASURE_MOCK_LOCATION") { api.openTreasure(treasure.id, at(near, isMock = true)) }
        assertError(400, "TREASURE_LOW_ACCURACY") { api.openTreasure(treasure.id, at(near, accuracy = 80.0)) }
    }

    @Test
    fun `하루 5개를 열면 6번째는 거부하지만, 이미 연 상자는 계속 멱등 응답한다`() = runTest {
        signUp()
        val treasures = nearby().treasures
        val opened = treasures.take(5)
        opened.forEach { treasure ->
            api.openTreasure(treasure.id, at(treasure.point()))
            clock.advance(Duration.ofHours(1)) // 순간이동 검사에 걸리지 않게 충분히 시간을 둔다
        }
        val sixth = treasures[5]

        assertError(400, "TREASURE_DAILY_LIMIT") { api.openTreasure(sixth.id, at(sixth.point())) }
        assertTrue(api.openTreasure(opened.first().id, at(opened.first().point())).alreadyOpened)
        assertEquals(5, nearby().openedToday)
    }

    @Test
    fun `직전 상자에서 시속 20km 보다 빠르게 이동했으면 거부하고, 충분히 시간이 지나면 열린다`() = runTest {
        signUp()
        val treasures = nearby().treasures
        val first = treasures.first()
        val second = treasures.maxBy { it.point().distanceMetersTo(first.point()) }
        val gap = first.point().distanceMetersTo(second.point())
        api.openTreasure(first.id, at(first.point()))

        clock.advance(Duration.ofSeconds(10))
        assertError(400, "TREASURE_TOO_FAST") { api.openTreasure(second.id, at(second.point())) }

        // 시속 20km 로 걸리는 시간보다 조금 더 지나면 정상 이동
        clock.advance(Duration.ofSeconds((gap / (20_000.0 / 3_600)).toLong() + 10))
        assertFalse(api.openTreasure(second.id, at(second.point())).alreadyOpened)
    }

    @Test
    fun `어제 상자는 NOT_TODAY, 형식이 틀리거나 없는 번호는 NOT_FOUND`() = runTest {
        signUp()
        val yesterday = nearby().treasures.first()
        clock.advance(Duration.ofDays(1))

        assertError(400, "TREASURE_NOT_TODAY") { api.openTreasure(yesterday.id, at(yesterday.point())) }
        assertError(404, "NOT_FOUND") { api.openTreasure("not-a-treasure", at(yesterday.point())) }
        assertError(404, "NOT_FOUND") { api.openTreasure("7500_25407-20261027-9", at(yesterday.point())) }
    }

    @Test
    fun `좌표가 없으면 400 VALIDATION_ERROR`() = runTest {
        signUp()
        assertError(400, "VALIDATION_ERROR") { api.getNearbyTreasures(latitude = 200.0, longitude = 0.0) }
    }

    private companion object {
        val YEOKSAM = GeoPoint(37.5006, 127.0364)
        val BUSAN = GeoPoint(35.1587, 129.1604)
        val JEJU = GeoPoint(33.4996, 126.5312)
    }
}
