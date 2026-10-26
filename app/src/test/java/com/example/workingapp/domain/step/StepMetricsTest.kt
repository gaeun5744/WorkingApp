package com.example.workingapp.domain.step

import org.junit.Assert.assertEquals
import org.junit.Test

class StepMetricsTest {

    @Test
    fun `만 걸음은 7km, 400kcal, 100분이다`() {
        assertEquals(StepMetrics(distanceKm = 7.0, kcal = 400, minutes = 100), StepMetrics.from(10_000))
    }

    @Test
    fun `0걸음은 모두 0이다`() {
        assertEquals(StepMetrics(distanceKm = 0.0, kcal = 0, minutes = 0), StepMetrics.from(0))
    }

    @Test
    fun `분과 칼로리는 내림한다`() {
        val metrics = StepMetrics.from(199)

        assertEquals(1, metrics.minutes)
        assertEquals(7, metrics.kcal)
        assertEquals(0.1393, metrics.distanceKm, 1e-9)
    }
}
