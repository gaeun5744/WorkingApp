package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegionsResponse(
    val regions: List<RegionDto>,
)
