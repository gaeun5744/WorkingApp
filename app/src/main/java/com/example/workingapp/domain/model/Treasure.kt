package com.example.workingapp.domain.model

data class Treasure(
    val id: String,
    val location: GeoPoint,
    val points: Int,
    val opened: Boolean,
)
