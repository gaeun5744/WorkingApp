package com.example.workingapp.data.remote.dto

import com.example.workingapp.domain.model.GeoPoint
import com.example.workingapp.domain.model.NearbyTreasures
import com.example.workingapp.domain.model.Treasure
import com.example.workingapp.domain.model.TreasureOpenResult
import java.time.LocalDate

fun TreasuresNearbyResponse.toDomain(): NearbyTreasures = NearbyTreasures(
    date = LocalDate.parse(date),
    openRadiusMeters = openRadiusMeters,
    dailyOpenLimit = dailyOpenLimit,
    openedToday = openedToday,
    treasures = treasures.map { it.toDomain() },
)

fun TreasureDto.toDomain(): Treasure =
    Treasure(id = id, location = GeoPoint(latitude, longitude), points = points, opened = opened)

fun TreasureOpenResponse.toDomain(): TreasureOpenResult =
    TreasureOpenResult(treasureId = treasureId, points = points, balance = balance, alreadyOpened = alreadyOpened)
