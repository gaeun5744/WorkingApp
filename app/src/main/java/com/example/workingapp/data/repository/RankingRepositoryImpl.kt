package com.example.workingapp.data.repository

import com.example.workingapp.data.remote.api.PedometerApi
import com.example.workingapp.data.remote.apiCall
import com.example.workingapp.data.remote.dto.toDomain
import com.example.workingapp.domain.model.Ranking
import com.example.workingapp.domain.model.RankingPeriod
import com.example.workingapp.domain.repository.RankingRepository
import com.example.workingapp.domain.repository.StepRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 랭킹은 금방 바뀌고 앱을 다시 열면 새로 받는 게 맞아 메모리에만 5분 캐시한다 (PRD §6).
 * 같은 기간을 동시에 요청해도 한 번만 받도록 Mutex 로 묶는다.
 */
@Singleton
internal class RankingRepositoryImpl @Inject constructor(
    private val api: PedometerApi,
    private val stepRepository: StepRepository,
    private val json: Json,
    private val clock: Clock,
) : RankingRepository {

    private val mutex = Mutex()
    private val cache = mutableMapOf<RankingPeriod, Ranking>()

    override suspend fun getRanking(period: RankingPeriod, forceRefresh: Boolean): Result<Ranking> = mutex.withLock {
        val cached = cache[period]
        if (!forceRefresh && cached != null && isFresh(cached)) return@withLock Result.success(cached)

        // 서버 걸음은 동기화 주기만큼 늦으므로 내 걸음을 먼저 올린다. 실패해도 랭킹은 보여준다
        stepRepository.syncPendingSteps()
        apiCall(json) { api.getRanking(period.apiValue) }
            .map { it.toDomain(period, fetchedAt = clock.instant()) }
            .onSuccess { cache[period] = it }
    }

    override fun invalidate() {
        cache.clear()
    }

    private fun isFresh(ranking: Ranking): Boolean =
        Duration.between(ranking.fetchedAt, clock.instant()) < CACHE_TTL

    private companion object {
        val CACHE_TTL: Duration = Duration.ofMinutes(5)
    }
}
