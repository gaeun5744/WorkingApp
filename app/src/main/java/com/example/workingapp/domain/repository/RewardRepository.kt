package com.example.workingapp.domain.repository

import com.example.workingapp.domain.model.ClaimResult
import com.example.workingapp.domain.model.TodayRewards
import com.example.workingapp.domain.model.Wallet
import java.time.LocalDate

interface RewardRepository {

    suspend fun getTodayRewards(): Result<TodayRewards>

    /** 서버가 멱등하게 처리한다: 같은 구간을 다시 요청해도 한 번만 적립된다. */
    suspend fun claim(date: LocalDate, tier: Int): Result<ClaimResult>

    suspend fun getWallet(): Result<Wallet>
}
