package com.example.workingapp.testing

import com.example.workingapp.domain.model.ClaimResult
import com.example.workingapp.domain.model.RewardStatus
import com.example.workingapp.domain.model.RewardTier
import com.example.workingapp.domain.model.TodayRewards
import com.example.workingapp.domain.model.Wallet
import com.example.workingapp.domain.repository.RewardRepository
import java.time.LocalDate

class FakeRewardRepository : RewardRepository {

    var todayResult: Result<TodayRewards> = Result.success(todayRewards())
    var walletResult: Result<Wallet> = Result.success(Wallet(balance = 0, transactions = emptyList()))

    /** 테스트가 수령 응답 시점을 조절할 수 있도록 함수로 둔다 */
    var claimHandler: suspend (LocalDate, Int) -> Result<ClaimResult> = { _, tier ->
        Result.success(ClaimResult(tier = tier, points = tier, balance = tier, alreadyClaimed = false))
    }
    val claimCalls = mutableListOf<Pair<LocalDate, Int>>()
    var walletCalls = 0
        private set

    override suspend fun getTodayRewards(): Result<TodayRewards> = todayResult

    override suspend fun claim(date: LocalDate, tier: Int): Result<ClaimResult> {
        claimCalls += date to tier
        return claimHandler(date, tier)
    }

    override suspend fun getWallet(): Result<Wallet> {
        walletCalls++
        return walletResult
    }

    companion object {
        fun todayRewards(
            date: LocalDate = LocalDate.of(2026, 10, 25),
            claimed: Set<Int> = emptySet(),
        ) = TodayRewards(
            date = date,
            serverSteps = 0,
            tiers = listOf(1 to 1_000, 2 to 3_000, 3 to 5_000, 4 to 7_000, 5 to 10_000).map { (tier, required) ->
                RewardTier(tier, required, points = tier, status = if (tier in claimed) RewardStatus.CLAIMED else RewardStatus.LOCKED)
            },
        )
    }
}
