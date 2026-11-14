package com.example.workingapp.domain.geo

import com.example.workingapp.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoDistanceTest {

    private val seongsu = GeoPoint(37.5446, 127.0560)

    @Test
    fun `같은 점은 0m 다`() {
        assertEquals(0.0, seongsu.distanceMetersTo(seongsu), 1e-6)
    }

    @Test
    fun `위도 0도1 차이는 약 11km 다`() {
        assertEquals(11_119.5, seongsu.distanceMetersTo(seongsu.copy(latitude = seongsu.latitude + 0.1)), 1.0)
    }

    @Test
    fun `북쪽·동쪽으로 옮긴 만큼의 거리가 나온다`() {
        val moved = seongsu.offsetMeters(northMeters = 30.0, eastMeters = 40.0)

        assertEquals(50.0, seongsu.distanceMetersTo(moved), 0.1)
    }
}
