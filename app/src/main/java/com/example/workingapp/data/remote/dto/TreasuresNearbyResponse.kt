package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TreasuresNearbyResponse(
    val date: String,
    val openRadiusMeters: Int,
    val dailyOpenLimit: Int,
    val openedToday: Int,
    val treasures: List<TreasureDto>,
)
