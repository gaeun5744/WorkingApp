package com.example.workingapp.domain.step

import com.example.workingapp.domain.model.StepReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class StepDeltaCalculatorTest {

    private val calculator = StepDeltaCalculator(ZoneId.of("Asia/Seoul"))

    private fun kst(dateTime: String): Instant = OffsetDateTime.parse("$dateTime+09:00").toInstant()

    private fun reading(raw: Long, boot: Int = 1, at: String = "2026-10-25T10:00:00") =
        StepReading(rawCount = raw, bootCount = boot, timestamp = kst(at))

    private fun checkpoint(raw: Long, boot: Int = 1, at: String = "2026-10-25T09:00:00") =
        CounterCheckpoint(rawCount = raw, bootCount = boot, timestamp = kst(at))

    @Test
    fun `첫 측정은 설치 전 걸음을 알 수 없으므로 더하지 않고 기준만 저장한다`() {
        val result = calculator.calculate(previous = null, reading = reading(raw = 5_000))

        assertNull(result.delta)
        assertEquals(5_000L, result.checkpoint.rawCount)
    }

    @Test
    fun `같은 부팅 안에서는 직전 값과의 차이만큼 더한다`() {
        val result = calculator.calculate(checkpoint(raw = 5_000), reading(raw = 5_230))

        assertEquals(StepDelta(LocalDate.of(2026, 10, 25), hour = 10, steps = 230), result.delta)
        assertEquals(5_230L, result.checkpoint.rawCount)
    }

    @Test
    fun `값이 그대로면 더할 걸음이 없다`() {
        val result = calculator.calculate(checkpoint(raw = 5_000), reading(raw = 5_000))

        assertNull(result.delta)
    }

    @Test
    fun `부팅 횟수가 바뀌면 값이 커졌더라도 재부팅으로 보고 현재 값 전체를 더한다`() {
        // 재부팅 전 5,000 → 재부팅 후 첫 측정 전에 6,000보를 걸은 경우
        val result = calculator.calculate(checkpoint(raw = 5_000, boot = 1), reading(raw = 6_000, boot = 2))

        assertEquals(6_000, result.delta?.steps)
        assertEquals(2, result.checkpoint.bootCount)
    }

    @Test
    fun `부팅 횟수를 알 수 없으면 값이 줄었을 때 재부팅으로 본다`() {
        val unknown = StepReading.UNKNOWN_BOOT_COUNT
        val result = calculator.calculate(checkpoint(raw = 5_000, boot = unknown), reading(raw = 120, boot = unknown))

        assertEquals(120, result.delta?.steps)
    }

    @Test
    fun `같은 부팅인데 값이 줄었으면 늦게 도착한 측정이므로 무시하고 기준도 유지한다`() {
        // 서비스가 5,000 을 반영한 뒤 워커가 더 일찍 읽어 둔 4,800 이 늦게 도착한 경우
        val previous = checkpoint(raw = 5_000, at = "2026-10-25T10:00:00")
        val result = calculator.calculate(previous, reading(raw = 4_800, at = "2026-10-25T09:59:00"))

        assertNull(result.delta)
        assertEquals(previous, result.checkpoint)
    }

    @Test
    fun `날짜와 시간대는 기기 시간대와 무관하게 KST 기준이다`() {
        // UTC 2026-10-25 15:30 = KST 2026-10-26 00:30
        val reading = StepReading(rawCount = 1_100, bootCount = 1, timestamp = Instant.parse("2026-10-25T15:30:00Z"))
        val result = calculator.calculate(checkpoint(raw = 1_000), reading)

        assertEquals(StepDelta(LocalDate.of(2026, 10, 26), hour = 0, steps = 100), result.delta)
    }

    @Test
    fun `자정을 넘긴 측정의 증분은 측정 시각의 날짜에 더한다`() {
        val result = calculator.calculate(
            checkpoint(raw = 9_000, at = "2026-10-25T23:50:00"),
            reading(raw = 9_300, at = "2026-10-26T00:10:00"),
        )

        assertEquals(StepDelta(LocalDate.of(2026, 10, 26), hour = 0, steps = 300), result.delta)
    }
}
