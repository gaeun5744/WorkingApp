package com.example.workingapp.domain.repository

import com.example.workingapp.domain.model.DeviceLocation
import com.example.workingapp.domain.model.GeoPoint
import com.example.workingapp.domain.model.NearbyTreasures
import com.example.workingapp.domain.model.TreasureOpenResult

interface TreasureRepository {

    /** [around] 가 속한 칸과 주변 8칸의 오늘 상자 */
    suspend fun getNearbyTreasures(around: GeoPoint): Result<NearbyTreasures>

    /** 서버가 위치를 다시 검증한다 (하루 상한·거리·정확도·가짜 GPS·이동 속도). 이미 연 상자는 멱등하게 성공 응답. */
    suspend fun open(treasureId: String, location: DeviceLocation): Result<TreasureOpenResult>
}
