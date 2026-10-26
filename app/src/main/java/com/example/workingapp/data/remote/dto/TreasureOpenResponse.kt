package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TreasureOpenResponse(
    val treasureId: String,
    val points: Int,
    val balance: Int,
    val alreadyOpened: Boolean,
)
