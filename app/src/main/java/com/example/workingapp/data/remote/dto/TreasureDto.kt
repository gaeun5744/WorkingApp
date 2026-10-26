package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TreasureDto(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val points: Int,
    val opened: Boolean,
)
