package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RankingEntryDto(
    val rank: Int,
    val userId: String,
    val nickname: String,
    val steps: Int,
)
