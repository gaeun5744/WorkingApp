package com.example.workingapp.data.repository

import com.example.workingapp.data.remote.api.PedometerApi
import com.example.workingapp.data.remote.apiCall
import com.example.workingapp.data.remote.dto.TreasureOpenRequest
import com.example.workingapp.data.remote.dto.toDomain
import com.example.workingapp.domain.model.DeviceLocation
import com.example.workingapp.domain.model.GeoPoint
import com.example.workingapp.domain.model.NearbyTreasures
import com.example.workingapp.domain.model.TreasureOpenResult
import com.example.workingapp.domain.repository.TreasureRepository
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

internal class TreasureRepositoryImpl @Inject constructor(
    private val api: PedometerApi,
    private val json: Json,
    private val clock: Clock,
) : TreasureRepository {

    override suspend fun getNearbyTreasures(around: GeoPoint): Result<NearbyTreasures> =
        apiCall(json) { api.getNearbyTreasures(around.latitude, around.longitude) }.map { it.toDomain() }

    override suspend fun open(treasureId: String, location: DeviceLocation): Result<TreasureOpenResult> {
        val request = TreasureOpenRequest(
            latitude = location.point.latitude,
            longitude = location.point.longitude,
            accuracyMeters = location.accuracyMeters.toDouble(),
            isMock = location.isMock,
            measuredAt = OffsetDateTime.ofInstant(location.measuredAt, clock.zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
        return apiCall(json) { api.openTreasure(treasureId, request) }.map { it.toDomain() }
    }
}
