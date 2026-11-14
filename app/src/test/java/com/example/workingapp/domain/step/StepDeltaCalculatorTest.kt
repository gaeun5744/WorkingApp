package com.example.workingapp.domain.step

import com.example.workingapp.domain.model.StepReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class StepDeltaCalculatorTest {

    private val calculator = StepDeltaCalculator(ZoneId.of("Asia/Seoul"))

    private fun kst(dateTime: String): Instant = OffsetDateTime.parse("$dateTime+09:00").toInstant()

    // bootedAt 기본값은 모든 체크포인트보다 앞선 시각이라 재부팅 구간 시작이 직전 측정 시각이 된다
    private fun reading(raw: Long, boot: Int = 1, at: String = "2026-10-25T10:00:00", bootedAt: String = "2026-10-01T00:00:00") =
        StepReading(rawCount = raw, bootCount = boot, timestamp = kst(at), bootTime = kst(bootedAt))

    private fun checkpoint(raw: Long, boot: Int = 1, at: String = "2026-10-25T09:00:00") =
        CounterCheckpoint(rawCount = raw, bootCount = boot, timestamp = kst(at))

    @Test
    fun `첫 측정은 설치 전 걸음을 알 수 없으므로 더하지 않고 기준만 저장한다`() {
        val result = calculator.calculate(previous = null, reading = reading(raw = 5_000))

        assertTrue(result.deltas.isEmpty())
        assertEquals(5_000L, result.checkpoint.rawCount)
    }

    @Test
    fun `같은 부팅 안에서는 직전 값과의 차이만큼 더한다`() {
        val result = calculator.calculate(checkpoint(raw = 5_000), reading(raw = 5_230))

        assertEquals(listOf(StepDelta(LocalDate.of(2026, 10, 25), hour = 10, steps = 230)), result.deltas)
        assertEquals(5_230L, result.checkpoint.rawCount)
    }

    @Test
    fun `값이 그대로면 더할 걸음이 없다`() {
        val result = calculator.calculate(checkpoint(raw = 5_000), reading(raw = 5_000))

        assertTrue(result.deltas.isEmpty())
    }

    @Test
    fun `부팅 횟수가 바뀌면 값이 커졌더라도 재부팅으로 보고 현재 값 전체를 더한다`() {
        // 재부팅 전 5,000 → 재부팅 후 첫 측정 전에 6,000보를 걸은 경우
        val result = calculator.calculate(checkpoint(raw = 5_000, boot = 1), reading(raw = 6_000, boot = 2))

        assertEquals(6_000, result.deltas.sumOf { it.steps })
        assertEquals(2, result.checkpoint.bootCount)
    }

    @Test
    fun `부팅 횟수를 알 수 없으면 값이 줄었을 때 재부팅으로 본다`() {
        val unknown = StepReading.UNKNOWN_BOOT_COUNT
        val result = calculator.calculate(checkpoint(raw = 5_000, boot = unknown), reading(raw = 120, boot = unknown))

        assertEquals(120, result.deltas.sumOf { it.steps })
    }

    @Test
    fun `같은 부팅인데 값이 줄었으면 늦게 도착한 측정이므로 무시하고 기준도 유지한다`() {
        // 서비스가 5,000 을 반영한 뒤 워커가 더 일찍 읽어 둔 4,800 이 늦게 도착한 경우
        val previous = checkpoint(raw = 5_000, at = "2026-10-25T10:00:00")
        val result = calculator.calculate(previous, reading(raw = 4_800, at = "2026-10-25T09:59:00"))

        assertTrue(result.deltas.isEmpty())
        assertEquals(previous, result.checkpoint)
    }

    @Test
    fun `날짜와 시간대는 기기 시간대와 무관하게 KST 기준이다`() {
        // UTC 2026-10-25 15:30 = KST 2026-10-26 00:30
        val reading = StepReading(
            rawCount = 1_100,
            bootCount = 1,
            timestamp = Instant.parse("2026-10-25T15:30:00Z"),
            bootTime = Instant.EPOCH,
        )
        val result = calculator.calculate(checkpoint(raw = 1_000, at = "2026-10-26T00:00:00"), reading)

        assertEquals(listOf(StepDelta(LocalDate.of(2026, 10, 26), hour = 0, steps = 100)), result.deltas)
    }

    @Test
    fun `자정을 넘긴 짧은 간격의 증분은 측정 시각의 날짜에 더한다`() {
        val result = calculator.calculate(
            checkpoint(raw = 9_000, at = "2026-10-25T23:50:00"),
            reading(raw = 9_300, at = "2026-10-26T00:10:00"),
        )

        assertEquals(listOf(StepDelta(LocalDate.of(2026, 10, 26), hour = 0, steps = 300)), result.deltas)
    }

    // region 측정 공백 (실기기 SM-S916N: 강제 중지 후 재부팅, 2.5일치 19,544보가 한 시간에 몰림)

    @Test
    fun `측정 공백이 1시간보다 길면 증분을 공백 구간의 시간대에 시간 비율로 나눈다`() {
        val result = calculator.calculate(
            checkpoint(raw = 1_000, at = "2026-10-25T08:00:00"),
            reading(raw = 1_300, at = "2026-10-25T11:00:00"),
        )

        val day = LocalDate.of(2026, 10, 25)
        assertEquals(
            listOf(StepDelta(day, 8, 100), StepDelta(day, 9, 100), StepDelta(day, 10, 100)),
            result.deltas,
        )
    }

    @Test
    fun `재부팅 후 긴 공백은 부팅 시각부터 측정 시각까지만 나누고 합계는 그대로다`() {
        // 실기기 재현: 10/27 마지막 측정 → 11/1 09:13 재부팅 → 11/3 20:37 첫 측정 19,544
        val result = calculator.calculate(
            checkpoint(raw = 5_000, boot = 71, at = "2026-10-27T19:28:00"),
            reading(raw = 19_544, boot = 72, at = "2026-11-03T20:37:00", bootedAt = "2026-11-01T09:13:00"),
        )

        assertEquals(19_544, result.deltas.sumOf { it.steps })
        assertEquals(StepDelta(LocalDate.of(2026, 11, 1), hour = 9, steps = result.deltas.first().steps), result.deltas.first())
        assertEquals(StepDelta(LocalDate.of(2026, 11, 3), hour = 20, steps = result.deltas.last().steps), result.deltas.last())
        // 구간 3,564분 중 오늘 1,237분 → 약 6,783보 (시간대별 반올림 오차 허용)
        val today = result.deltas.filter { it.date == LocalDate.of(2026, 11, 3) }.sumOf { it.steps }
        assertTrue("오늘 몫 $today", today in 6_760..6_810)
        assertTrue(result.deltas.all { it.steps <= 330 })
    }

    @Test
    fun `재부팅 시각이 최근이면 직전 측정이 오래됐어도 짧은 간격으로 보고 측정 시각에 더한다`() {
        val result = calculator.calculate(
            checkpoint(raw = 5_000, boot = 1, at = "2026-10-23T10:00:00"),
            reading(raw = 400, boot = 2, at = "2026-10-25T10:00:00", bootedAt = "2026-10-25T09:30:00"),
        )

        assertEquals(listOf(StepDelta(LocalDate.of(2026, 10, 25), hour = 10, steps = 400)), result.deltas)
    }

    // endregion
}
