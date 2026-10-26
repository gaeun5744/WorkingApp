package com.example.workingapp.domain.model

import java.time.Instant

/**
 * 기기가 측정한 위치.
 * - [accuracyMeters]: 68% 확률로 실제 위치가 이 반경 안에 있다 (Android Location.accuracy)
 * - [isMock]: 가짜 GPS 앱 등 모의 위치 제공자가 만든 위치인지
 */
data class DeviceLocation(
    val point: GeoPoint,
    val accuracyMeters: Float,
    val isMock: Boolean,
    val measuredAt: Instant,
)
