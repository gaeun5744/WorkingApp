package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RewardClaimRequest(
    val date: String,
    val tier: Int,
    // Phase A 에서는 선택(무시됨), M9 에서 필수화
    val integrityToken: String? = null,
)
