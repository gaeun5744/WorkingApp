package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RankingResponse(
    val period: String,
    val periodStart: String,
    val region: RegionDto,
    val entries: List<RankingEntryDto>,
    val me: RankingEntryDto,
)
