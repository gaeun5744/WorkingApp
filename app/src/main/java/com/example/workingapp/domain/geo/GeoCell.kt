package com.example.workingapp.domain.geo

import com.example.workingapp.domain.model.GeoPoint
import kotlin.math.floor

/**
 * 위경도 [SIZE_DEGREES] 도 격자 칸 (PRD Q25). 보물상자는 칸 단위로 만들고 조회한다.
 * 서울 기준 한 칸은 남북 약 550m × 동서 약 440m.
 */
data class GeoCell(
    val latIndex: Int,
    val lngIndex: Int,
) {
    val southWest: GeoPoint get() = GeoPoint(latIndex * SIZE_DEGREES, lngIndex * SIZE_DEGREES)

    /** 이 칸과 주변 8칸 */
    fun withNeighbors(): List<GeoCell> =
        (-1..1).flatMap { dLat -> (-1..1).map { dLng -> GeoCell(latIndex + dLat, lngIndex + dLng) } }

    companion object {
        const val SIZE_DEGREES = 0.005

        fun of(point: GeoPoint): GeoCell = GeoCell(
            latIndex = floor(point.latitude / SIZE_DEGREES).toInt(),
            lngIndex = floor(point.longitude / SIZE_DEGREES).toInt(),
        )
    }
}
