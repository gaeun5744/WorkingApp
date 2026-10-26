package com.example.workingapp.data.remote.dto

import com.example.workingapp.domain.model.ClaimResult
import com.example.workingapp.domain.model.RewardStatus
import com.example.workingapp.domain.model.RewardTier
import com.example.workingapp.domain.model.TodayRewards
import java.time.LocalDate

fun RewardsTodayResponse.toDomain(): TodayRewards = TodayRewards(
    date = LocalDate.parse(date),
    serverSteps = steps,
    tiers = tiers.map { it.toDomain() },
)

fun RewardTierDto.toDomain(): RewardTier = RewardTier(
    tier = tier,
    requiredSteps = requiredSteps,
    points = points,
    status = when (status) {
        RewardStatusDto.LOCKED -> RewardStatus.LOCKED
        RewardStatusDto.CLAIMABLE -> RewardStatus.CLAIMABLE
        RewardStatusDto.CLAIMED -> RewardStatus.CLAIMED
    },
)

fun RewardClaimResponse.toDomain(): ClaimResult =
    ClaimResult(tier = tier, points = points, balance = balance, alreadyClaimed = alreadyClaimed)
