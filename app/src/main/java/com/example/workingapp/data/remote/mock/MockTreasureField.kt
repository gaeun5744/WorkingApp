package com.example.workingapp.data.remote.mock

import com.example.workingapp.domain.geo.GeoCell
import com.example.workingapp.domain.model.GeoPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.random.Random

/**
 * 격자 칸마다 (칸, 날짜)를 시드로 보물상자 위치를 만든다 (PRD Q25).
 * 저장하지 않아도 같은 장소·같은 날이면 누구에게나, 프로세스를 재시작해도 같은 위치가 나온다.
 * 상자 id 에 칸·날짜가 들어 있어 id 만으로 위치를 다시 계산할 수 있다.
 */
internal class MockTreasureField @Inject constructor() {

    fun treasuresAround(point: GeoPoint, date: LocalDate): List<Spot> =
        GeoCell.of(point).withNeighbors().flatMap { treasuresIn(it, date) }

    fun treasuresIn(cell: GeoCell, date: LocalDate): List<Spot> {
        val random = Random(seedOf(cell, date))
        val southWest = cell.southWest
        return (0 until COUNT_PER_CELL).map { index ->
            Spot(
                id = "${cell.latIndex}_${cell.lngIndex}-${date.format(ID_DATE)}-$index",
                point = GeoPoint(
                    latitude = southWest.latitude + random.nextDouble() * GeoCell.SIZE_DEGREES,
                    longitude = southWest.longitude + random.nextDouble() * GeoCell.SIZE_DEGREES,
                ),
            )
        }
    }

    /** id 를 해석해 그 상자를 다시 만든다. 형식이 틀리거나 없는 번호면 null. */
    fun find(id: String): Found? {
        val match = ID_PATTERN.matchEntire(id) ?: return null
        val (lat, lng, rawDate, rawIndex) = match.destructured
        val date = runCatching { LocalDate.parse(rawDate, ID_DATE) }.getOrNull() ?: return null
        val spot = treasuresIn(GeoCell(lat.toInt(), lng.toInt()), date).getOrNull(rawIndex.toInt()) ?: return null
        return Found(spot, date)
    }

    // 칸 번호·날짜를 서로 다른 큰 소수로 섞어 이웃 칸끼리 시드가 겹치지 않게 한다
    private fun seedOf(cell: GeoCell, date: LocalDate): Long =
        cell.latIndex * 73_856_093L xor cell.lngIndex * 19_349_663L xor date.toEpochDay() * 83_492_791L

    data class Spot(val id: String, val point: GeoPoint)
    data class Found(val spot: Spot, val date: LocalDate)

    companion object {
        const val COUNT_PER_CELL = 2
        private val ID_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val ID_PATTERN = Regex("""(-?\d+)_(-?\d+)-(\d{8})-(\d+)""")
    }
}
