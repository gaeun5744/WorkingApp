package com.example.workingapp.presentation.walkmap

import com.example.workingapp.domain.model.GeoPoint

data class TreasureUi(
    val id: String,
    val location: GeoPoint,
    val points: Int,
    val opened: Boolean,
    /** 내 위치를 모르면 null */
    val distanceMeters: Int?,
)
