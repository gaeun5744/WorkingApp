package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateRegionRequest(
    val regionId: Long,
)
