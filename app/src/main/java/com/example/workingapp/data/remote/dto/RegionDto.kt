package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegionDto(
    val id: Long,
    val name: String,
)
