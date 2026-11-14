package com.example.workingapp.testing

import com.example.workingapp.domain.model.Ranking
import com.example.workingapp.domain.model.RankingEntry
import com.example.workingapp.domain.model.RankingPeriod
import com.example.workingapp.domain.repository.RankingRepository
import java.time.Instant
import java.time.LocalDate

class FakeRankingRepository : RankingRepository {

    var result: (RankingPeriod) -> Result<Ranking> = { Result.success(ranking(it)) }
    val calls = mutableListOf<Pair<RankingPeriod, Boolean>>()
    var invalidations = 0
        private set

    override suspend fun getRanking(period: RankingPeriod, forceRefresh: Boolean): Result<Ranking> {
        calls += period to forceRefresh
        return result(period)
    }

    override fun invalidate() {
        invalidations++
    }

    companion object {
        const val MY_ID = "u_me"

        fun ranking(period: RankingPeriod = RankingPeriod.DAILY, regionName: String = "역삼동") = Ranking(
            period = period,
            periodStart = LocalDate.of(2026, 10, 26),
            regionName = regionName,
            entries = listOf(
                RankingEntry(1, "n_1", "산책왕", 15_200),
                RankingEntry(2, MY_ID, "걷는토끼", 9_000),
                RankingEntry(3, "n_2", "아침러너", 8_100),
            ),
            me = RankingEntry(2, MY_ID, "걷는토끼", 9_000),
            fetchedAt = Instant.parse("2026-10-26T12:30:00Z"),
        )
    }
}
