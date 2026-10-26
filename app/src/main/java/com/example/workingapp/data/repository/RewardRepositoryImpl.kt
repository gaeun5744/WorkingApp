package com.example.workingapp.data.repository

import com.example.workingapp.data.remote.api.PedometerApi
import com.example.workingapp.data.remote.apiCall
import com.example.workingapp.data.remote.dto.RewardClaimRequest
import com.example.workingapp.data.remote.dto.toDomain
import com.example.workingapp.domain.model.ClaimResult
import com.example.workingapp.domain.model.TodayRewards
import com.example.workingapp.domain.model.Wallet
import com.example.workingapp.domain.repository.RewardRepository
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

internal class RewardRepositoryImpl @Inject constructor(
    private val api: PedometerApi,
    private val json: Json,
) : RewardRepository {

    override suspend fun getTodayRewards(): Result<TodayRewards> =
        apiCall(json) { api.getTodayRewards() }.map { it.toDomain() }

    // integrityToken 은 Phase A 에서 생략, M8 에서 Play Integrity 토큰을 붙인다
    override suspend fun claim(date: LocalDate, tier: Int): Result<ClaimResult> =
        apiCall(json) { api.claimReward(RewardClaimRequest(date = date.toString(), tier = tier)) }.map { it.toDomain() }

    override suspend fun getWallet(): Result<Wallet> =
        apiCall(json) { api.getWallet() }.map { it.toDomain() }
}
