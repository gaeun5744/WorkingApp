package com.example.workingapp.testing

import com.example.workingapp.domain.geo.offsetMeters
import com.example.workingapp.domain.model.DeviceLocation
import com.example.workingapp.domain.model.GeoPoint
import com.example.workingapp.domain.model.NearbyTreasures
import com.example.workingapp.domain.model.Treasure
import com.example.workingapp.domain.model.TreasureOpenResult
import com.example.workingapp.domain.repository.TreasureRepository
import java.time.LocalDate

class FakeTreasureRepository : TreasureRepository {

    var nearbyResult: Result<NearbyTreasures> = Result.success(nearby())
    var openHandler: suspend (String, DeviceLocation) -> Result<TreasureOpenResult> = { id, _ ->
        Result.success(TreasureOpenResult(id, points = 2, balance = 2, alreadyOpened = false))
    }
    val openCalls = mutableListOf<Pair<String, DeviceLocation>>()
    val nearbyCalls = mutableListOf<GeoPoint>()

    override suspend fun getNearbyTreasures(around: GeoPoint): Result<NearbyTreasures> {
        nearbyCalls += around
        return nearbyResult
    }

    override suspend fun open(treasureId: String, location: DeviceLocation): Result<TreasureOpenResult> {
        openCalls += treasureId to location
        return openHandler(treasureId, location)
    }

    companion object {
        /** 역삼역 근처, 0.005도 칸 (7500, 25407) 안쪽 */
        val ORIGIN = GeoPoint(37.5012, 127.0371)

        /** ORIGIN 에서 북쪽 200m(t0), 동쪽 500m(t1) 에 있는 상자 */
        fun nearby(openedToday: Int = 0) = NearbyTreasures(
            date = LocalDate.of(2026, 10, 26),
            openRadiusMeters = 50,
            dailyOpenLimit = 5,
            openedToday = openedToday,
            treasures = listOf(
                Treasure("t0", ORIGIN.offsetMeters(northMeters = 200.0, eastMeters = 0.0), points = 2, opened = false),
                Treasure("t1", ORIGIN.offsetMeters(northMeters = 0.0, eastMeters = 500.0), points = 2, opened = false),
            ),
        )
    }
}
