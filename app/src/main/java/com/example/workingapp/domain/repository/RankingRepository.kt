package com.example.workingapp.domain.repository

import com.example.workingapp.domain.model.Ranking
import com.example.workingapp.domain.model.RankingPeriod

interface RankingRepository {

    /**
     * 기간별로 5분 동안 캐시한다. [forceRefresh] 면 캐시를 무시하고 새로 받는다.
     * 새로 받을 때는 내 미동기화 걸음을 먼저 올려 내 순위가 늦게 반영되지 않게 한다.
     */
    suspend fun getRanking(period: RankingPeriod, forceRefresh: Boolean = false): Result<Ranking>

    /** 동네가 바뀌면 이전 동네의 캐시를 버린다 */
    fun invalidate()
}
