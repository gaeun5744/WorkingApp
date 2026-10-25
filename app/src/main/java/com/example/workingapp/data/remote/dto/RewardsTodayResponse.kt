package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RewardsTodayResponse(
    val date: String,
    val steps: Int,
    val tiers: List<RewardTierDto>,
)
