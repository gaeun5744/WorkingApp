package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MeResponse(
    val userId: String,
    val nickname: String,
    val region: RegionDto?,
)
