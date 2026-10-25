package com.example.workingapp.data.remote.mock

import com.example.workingapp.data.remote.dto.MeResponse
import com.example.workingapp.data.remote.dto.RankingEntryDto
import com.example.workingapp.data.remote.dto.RankingResponse
import com.example.workingapp.data.remote.dto.RegionDto
import com.example.workingapp.data.remote.dto.RegionsResponse
import com.example.workingapp.data.remote.dto.RewardClaimRequest
import com.example.workingapp.data.remote.dto.RewardClaimResponse
import com.example.workingapp.data.remote.dto.RewardStatusDto
import com.example.workingapp.data.remote.dto.RewardTierDto
import com.example.workingapp.data.remote.dto.RewardsTodayResponse
import com.example.workingapp.data.remote.dto.StepsSyncRequest
import com.example.workingapp.data.remote.dto.StepsSyncResponse
import com.example.workingapp.data.remote.dto.StepsSyncResultDto
import com.example.workingapp.data.remote.dto.SyncStatusDto
import com.example.workingapp.data.remote.dto.TokenResponse
import com.example.workingapp.data.remote.dto.WalletResponse
import com.example.workingapp.data.remote.dto.WalletTransactionDto
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `docs/api.md` 의 서버 규칙을 메모리 상태로 흉내 내는 Mock 서버.
 * 상태는 프로세스가 살아 있는 동안만 유지된다 (PRD Q18 알려진 한계).
 * OkHttp 는 여러 스레드에서 요청을 보내므로 모든 상태 접근은 [lock] 안에서 한다.
 */
@Singleton
internal class MockServer @Inject constructor(
    private val clock: Clock,
    private val config: MockApiConfig,
    private val neighborSteps: MockNeighborSteps,
) {
    private val lock = Any()

    private val users = mutableMapOf<String, MockUser>()
    private val accessSessions = mutableMapOf<String, Session>()
    private val refreshSessions = mutableMapOf<String, Session>()
    private val dailySteps = mutableMapOf<UserDate, Int>()
    private val claimedTiers = mutableSetOf<Claim>()
    private val balances = mutableMapOf<String, Int>()
    private val transactions = mutableMapOf<String, MutableList<WalletTransactionDto>>()
    private var nextId = 1L

    // region 인증

    fun signUpAnonymous(nickname: String): MockResult<TokenResponse> = synchronized(lock) {
        val trimmed = nickname.trim()
        if (trimmed.length !in 1..MAX_NICKNAME_LENGTH) {
            return validationError("닉네임은 1~${MAX_NICKNAME_LENGTH}자여야 합니다.")
        }
        val userId = "u_${UUID.randomUUID().toString().take(8)}"
        users[userId] = MockUser(id = userId, nickname = trimmed, regionId = null)
        MockResult.Success(issueTokens(userId), httpStatus = 201)
    }

    fun refreshToken(refreshToken: String): MockResult<TokenResponse> = synchronized(lock) {
        // rotation: 사용한 리프레시 토큰은 성공 여부와 관계없이 즉시 폐기
        val session = refreshSessions.remove(refreshToken)
        if (session == null || !session.expiresAt.isAfter(clock.instant()) || session.userId !in users) {
            return MockResult.Failure(401, "INVALID_REFRESH_TOKEN", "리프레시 토큰이 유효하지 않습니다.")
        }
        MockResult.Success(issueTokens(session.userId))
    }

    /** `Authorization` 헤더 값으로 사용자 ID 를 찾는다. */
    fun authenticate(authorization: String?): MockResult<String> = synchronized(lock) {
        val token = authorization?.removePrefix(BEARER_PREFIX)
        val session = token?.let { accessSessions[it] }
        when {
            session == null || session.userId !in users ->
                MockResult.Failure(401, "UNAUTHORIZED", "인증이 필요합니다.")
            !session.expiresAt.isAfter(clock.instant()) ->
                MockResult.Failure(401, "TOKEN_EXPIRED", "액세스 토큰이 만료되었습니다.")
            else -> MockResult.Success(session.userId)
        }
    }

    private fun issueTokens(userId: String): TokenResponse {
        val now = clock.instant()
        val accessToken = "mock-access-${UUID.randomUUID()}"
        val refreshToken = "mock-refresh-${UUID.randomUUID()}"
        accessSessions[accessToken] = Session(userId, now.plus(config.accessTokenTtl))
        refreshSessions[refreshToken] = Session(userId, now.plus(config.refreshTokenTtl))
        return TokenResponse(
            userId = userId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresInSeconds = config.accessTokenTtl.seconds,
        )
    }

    // endregion

    // region 사용자 / 동네

    fun getMe(userId: String): MockResult<MeResponse> = synchronized(lock) {
        MockResult.Success(meOf(userId))
    }

    fun searchRegions(query: String): MockResult<RegionsResponse> {
        val keyword = query.trim()
        return MockResult.Success(RegionsResponse(REGIONS.filter { it.name.contains(keyword) }))
    }

    fun updateRegion(userId: String, regionId: Long): MockResult<MeResponse> = synchronized(lock) {
        if (REGIONS.none { it.id == regionId }) {
            return MockResult.Failure(404, "NOT_FOUND", "존재하지 않는 동네입니다.")
        }
        users.computeIfPresent(userId) { _, user -> user.copy(regionId = regionId) }
        MockResult.Success(meOf(userId))
    }

    private fun meOf(userId: String): MeResponse {
        val user = users.getValue(userId)
        return MeResponse(
            userId = user.id,
            nickname = user.nickname,
            region = REGIONS.find { it.id == user.regionId },
        )
    }

    // endregion

    // region 걸음

    fun syncSteps(userId: String, request: StepsSyncRequest): MockResult<StepsSyncResponse> = synchronized(lock) {
        val invalid = request.days.any { day ->
            day.steps < 0 || day.hourly.any { it.hour !in 0..23 || it.steps < 0 }
        }
        if (invalid) return validationError("걸음 수는 0 이상, hour 는 0~23 이어야 합니다.")
        // 일부 날짜만 반영된 채로 실패하지 않도록 날짜 형식을 먼저 모두 검사한다
        val dates = request.days.map { day ->
            parseDate(day.date) ?: return validationError("date 형식은 yyyy-MM-dd 입니다: ${day.date}")
        }

        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()
        val results = request.days.zip(dates) { day, date ->
            if (date.isAfter(today) || date.isBefore(today.minusDays(SYNC_WINDOW_DAYS - 1))) {
                return@zip StepsSyncResultDto(day.date, SyncStatusDto.REJECTED, acceptedSteps = 0)
            }
            val cappedHourlySum = day.hourly.sumOf { hourly ->
                if (date == today && hourly.hour > now.hour) 0 else minOf(hourly.steps, HOURLY_CAP)
            }
            var accepted = minOf(day.steps, DAILY_CAP)
            if (day.hourly.isNotEmpty()) accepted = minOf(accepted, cappedHourlySum)

            val key = UserDate(userId, date)
            dailySteps[key] = maxOf(dailySteps[key] ?: 0, accepted)
            val status = if (accepted < day.steps) SyncStatusDto.CAPPED else SyncStatusDto.ACCEPTED
            StepsSyncResultDto(day.date, status, accepted)
        }
        MockResult.Success(StepsSyncResponse(results))
    }

    // endregion

    // region 리워드 / 지갑

    fun getTodayRewards(userId: String): MockResult<RewardsTodayResponse> = synchronized(lock) {
        val today = LocalDate.now(clock)
        val steps = dailySteps[UserDate(userId, today)] ?: 0
        val tiers = REWARD_TIERS.map { tier ->
            val status = when {
                Claim(userId, today, tier.tier) in claimedTiers -> RewardStatusDto.CLAIMED
                steps >= tier.requiredSteps -> RewardStatusDto.CLAIMABLE
                else -> RewardStatusDto.LOCKED
            }
            RewardTierDto(tier.tier, tier.requiredSteps, tier.points, status)
        }
        MockResult.Success(RewardsTodayResponse(today.toString(), steps, tiers))
    }

    fun claimReward(userId: String, request: RewardClaimRequest): MockResult<RewardClaimResponse> =
        synchronized(lock) {
            val tier = REWARD_TIERS.find { it.tier == request.tier }
                ?: return validationError("존재하지 않는 리워드 구간입니다: ${request.tier}")
            val today = LocalDate.now(clock)
            if (parseDate(request.date) != today) {
                return MockResult.Failure(400, "REWARD_NOT_TODAY", "오늘 리워드만 받을 수 있습니다.")
            }
            val claim = Claim(userId, today, tier.tier)
            if (claim in claimedTiers) {
                return MockResult.Success(
                    RewardClaimResponse(request.date, tier.tier, tier.points, balanceOf(userId), alreadyClaimed = true),
                )
            }
            if ((dailySteps[UserDate(userId, today)] ?: 0) < tier.requiredSteps) {
                return MockResult.Failure(400, "REWARD_NOT_REACHED", "아직 구간 걸음 수에 도달하지 않았습니다.")
            }
            claimedTiers += claim
            balances[userId] = balanceOf(userId) + tier.points
            transactions.getOrPut(userId) { mutableListOf() }.add(
                0,
                WalletTransactionDto(
                    id = "t_${nextId++}",
                    amount = tier.points,
                    reason = "REWARD_TIER_${tier.tier}",
                    createdAt = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                ),
            )
            MockResult.Success(
                RewardClaimResponse(request.date, tier.tier, tier.points, balanceOf(userId), alreadyClaimed = false),
            )
        }

    fun getWallet(userId: String): MockResult<WalletResponse> = synchronized(lock) {
        MockResult.Success(WalletResponse(balanceOf(userId), transactions[userId].orEmpty().toList()))
    }

    private fun balanceOf(userId: String): Int = balances[userId] ?: 0

    // endregion

    // region 랭킹

    fun getRanking(userId: String, period: String): MockResult<RankingResponse> = synchronized(lock) {
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()
        val periodStart = when (period) {
            PERIOD_DAILY -> today
            PERIOD_WEEKLY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            else -> return validationError("period 는 daily 또는 weekly 입니다.")
        }
        val me = users.getValue(userId)
        val region = REGIONS.find { it.id == me.regionId }
            ?: return MockResult.Failure(409, "REGION_NOT_SET", "동네를 먼저 설정해 주세요.")

        val days = generateSequence(periodStart) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        val realUsers = users.values.filter { it.regionId == region.id }
            .map { user -> Triple(user.id, user.nickname, days.sumOf { dailySteps[UserDate(user.id, it)] ?: 0 }) }
        val neighbors = neighborSteps.neighborsOf(region.id)
            .map { n -> Triple(n.userId, n.nickname, days.sumOf { neighborSteps.stepsOn(n.userId, it, now) }) }

        val ranked = (realUsers + neighbors)
            .sortedWith(compareByDescending<Triple<String, String, Int>> { it.third }.thenBy { it.first })
            .mapIndexed { index, (id, nickname, steps) -> RankingEntryDto(index + 1, id, nickname, steps) }

        MockResult.Success(
            RankingResponse(
                period = period,
                periodStart = periodStart.toString(),
                region = region,
                entries = ranked.take(RANKING_SIZE),
                me = ranked.first { it.userId == userId },
            ),
        )
    }

    // endregion

    private fun parseDate(value: String): LocalDate? =
        try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }

    private fun validationError(message: String) = MockResult.Failure(400, "VALIDATION_ERROR", message)

    private data class MockUser(val id: String, val nickname: String, val regionId: Long?)
    private data class Session(val userId: String, val expiresAt: Instant)
    private data class UserDate(val userId: String, val date: LocalDate)
    private data class Claim(val userId: String, val date: LocalDate, val tier: Int)
    private data class RewardTier(val tier: Int, val requiredSteps: Int, val points: Int)

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val MAX_NICKNAME_LENGTH = 12
        const val SYNC_WINDOW_DAYS = 7L
        const val HOURLY_CAP = 12_000
        const val DAILY_CAP = 100_000
        const val RANKING_SIZE = 50
        const val PERIOD_DAILY = "daily"
        const val PERIOD_WEEKLY = "weekly"

        val REWARD_TIERS = listOf(
            RewardTier(1, 1_000, 1),
            RewardTier(2, 3_000, 3),
            RewardTier(3, 5_000, 5),
            RewardTier(4, 7_000, 7),
            RewardTier(5, 10_000, 10),
        )

        val REGIONS = listOf(
            RegionDto(1, "역삼동"), RegionDto(2, "삼성동"), RegionDto(3, "대치동"),
            RegionDto(4, "서초동"), RegionDto(5, "잠실동"), RegionDto(6, "합정동"),
            RegionDto(7, "연남동"), RegionDto(8, "성수동"), RegionDto(9, "망원동"),
            RegionDto(10, "판교동"),
        )
    }
}
