package com.example.workingapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
enum class SyncStatusDto {
    ACCEPTED,
    CAPPED,
    REJECTED,
}
