package com.example.workingapp.presentation.walkmap

enum class LocationPermission {
    /** 아직 확인 전 */
    UNKNOWN,
    PRECISE,
    /** 대략적인 위치만 허용 — 지도는 보이지만 상자는 열 수 없다 (정확도 기준 미달) */
    APPROXIMATE,
    DENIED,
}
