package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AnonymousSignUpRequest(
    val nickname: String,
)
