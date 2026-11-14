package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresInSeconds: Long,
)
