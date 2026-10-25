package com.example.workingapp.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 테스트에서 시간을 앞으로 돌릴 수 있는 Clock. */
class MutableClock(
    private var current: Instant,
    private val zone: ZoneId = ZoneId.of("Asia/Seoul"),
) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
