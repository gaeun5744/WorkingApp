package com.example.workingapp.domain.geo

import com.example.workingapp.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoCellTest {

    @Test
    fun `0_005도 단위로 내림해 칸을 정한다`() {
        assertEquals(GeoCell(7500, 25407), GeoCell.of(GeoPoint(37.5006, 127.0364)))
    }

    @Test
    fun `칸 경계 바로 안쪽과 바깥쪽은 다른 칸이다`() {
        assertEquals(GeoCell(7499, 25407), GeoCell.of(GeoPoint(37.49999, 127.0364)))
        assertEquals(GeoCell(7500, 25407), GeoCell.of(GeoPoint(37.50000, 127.0364)))
    }

    @Test
    fun `음수 좌표도 내림한다 (남반구·서반구)`() {
        assertEquals(GeoCell(-1, -1), GeoCell.of(GeoPoint(-0.001, -0.001)))
    }

    @Test
    fun `주변 칸을 포함하면 3x3 9칸이다`() {
        val cells = GeoCell(10, 20).withNeighbors()

        assertEquals(9, cells.toSet().size)
        assertEquals(GeoCell(9, 19), cells.first())
        assertEquals(GeoCell(11, 21), cells.last())
    }
}
