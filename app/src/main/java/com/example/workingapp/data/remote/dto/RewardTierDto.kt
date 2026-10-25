package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RewardTierDto(
    val tier: Int,
    val requiredSteps: Int,
    val points: Int,
    val status: RewardStatusDto,
)
