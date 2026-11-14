package com.example.workingapp.presentation.walkmap

import com.example.workingapp.domain.model.GeoPoint

data class WalkMapUiState(
    val permission: LocationPermission = LocationPermission.UNKNOWN,
    val content: TreasureContent = TreasureContent.WaitingForLocation,
    val myLocation: GeoPoint? = null,
    val accuracyMeters: Float? = null,
    /** 아직 열지 않은 상자 중 가장 가까운 것 */
    val nearest: TreasureUi? = null,
    val canOpenNearest: Boolean = false,
    val isOpening: Boolean = false,
)
