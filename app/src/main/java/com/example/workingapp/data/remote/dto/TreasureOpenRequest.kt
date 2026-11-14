package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TreasureOpenRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val isMock: Boolean,
    val measuredAt: String,
    // Phase A 에서는 생략, M9 에서 Play Integrity 토큰을 붙인다
    val integrityToken: String? = null,
)
