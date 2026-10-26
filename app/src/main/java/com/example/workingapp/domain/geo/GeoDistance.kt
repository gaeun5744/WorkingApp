package com.example.workingapp.domain.geo

import com.example.workingapp.domain.model.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** 하버사인 공식으로 구한 두 지점 사이의 대원 거리(m). 동네 규모(수 km)에서는 오차가 무시할 만하다. */
fun GeoPoint.distanceMetersTo(other: GeoPoint): Double {
    val dLat = Math.toRadians(other.latitude - latitude)
    val dLng = Math.toRadians(other.longitude - longitude)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
}

/** 이 지점에서 북쪽·동쪽으로 미터만큼 옮긴 지점 (짧은 거리 근사) */
fun GeoPoint.offsetMeters(northMeters: Double, eastMeters: Double): GeoPoint {
    val metersPerDegreeLat = Math.PI * EARTH_RADIUS_METERS / 180
    return GeoPoint(
        latitude = latitude + northMeters / metersPerDegreeLat,
        longitude = longitude + eastMeters / (metersPerDegreeLat * cos(Math.toRadians(latitude))),
    )
}
