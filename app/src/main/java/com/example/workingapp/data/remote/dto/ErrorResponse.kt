package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
)
