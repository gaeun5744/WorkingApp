package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RewardClaimResponse(
    val date: String,
    val tier: Int,
    val points: Int,
    val balance: Int,
    val alreadyClaimed: Boolean,
)
