package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class StepsSyncResultDto(
    val date: String,
    val status: SyncStatusDto,
    val acceptedSteps: Int,
)
