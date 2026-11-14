package com.example.workingapp.domain.step

import com.example.workingapp.domain.model.StepReading
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 센서 누적값을 "언제 몇 걸음"으로 바꾸는 순수 계산기.
 *
 * baseline(0시 기준값) 방식 대신 측정마다 직전 값과의 증분을 측정 시각의 (날짜, 시간대)에 더한다.
 * 자정 리셋이 따로 필요 없고 시간대별 집계가 함께 나온다.
 *
 * 측정 공백: 직전 측정 이후 [GAP_THRESHOLD]보다 오래 측정하지 못했다면(앱 강제 중지, 재부팅 후 미실행 등)
 * 그 걸음이 언제 생겼는지 알 수 없다. 이때는 증분을 공백 구간의 시간대에 시간 길이 비율로 나눈다.
 * 재부팅했다면 걸음은 부팅 이후에만 생길 수 있으므로 구간 시작은 부팅 시각보다 앞서지 않는다.
 * 앱이 정상 동작 중이면 걸음마다 센서 이벤트가 오므로 짧은 간격은 "측정 시각 = 걸은 시각"으로 본다.
 *
 * 알려진 한계
 * - 짧은 간격 안에 자정이 끼면 증분 전체가 뒤 측정의 날짜로 들어간다.
 * - 공백 구간을 나눌 때 잠든 시간까지 고르게 나뉜다. 날짜별 합계는 대략 맞지만 시간대별 값은 추정치다.
 * - 정상 동작 중에도 1시간 넘게 가만히 있다가 걸으면 첫 이벤트의 몇 걸음이 그 시간에 나뉠 수 있다.
 */
class StepDeltaCalculator(private val zone: ZoneId) {

    fun calculate(previous: CounterCheckpoint?, reading: StepReading): StepCalculation {
        val checkpoint = CounterCheckpoint(reading.rawCount, reading.bootCount, reading.timestamp)
        if (previous == null) return StepCalculation(deltas = emptyList(), checkpoint = checkpoint)

        // 값 감소만으로 재부팅을 판단하면 "재부팅 후 직전 값보다 많이 걸은 경우"를 놓치므로 부팅 횟수를 우선 본다.
        // 같은 부팅 안에서 누적값은 줄지 않으므로, 부팅 횟수가 같은데 값이 줄었다면 서비스·워커가 읽은
        // 측정이 순서가 바뀌어 늦게 도착한 것이다. 재부팅으로 오인하면 걸음이 크게 중복되므로 무시한다.
        val bootKnown = reading.bootCount != StepReading.UNKNOWN_BOOT_COUNT
        val decreased = reading.rawCount < previous.rawCount
        if (bootKnown && reading.bootCount == previous.bootCount && decreased) {
            return StepCalculation(deltas = emptyList(), checkpoint = previous)
        }
        val rebooted = reading.bootCount != previous.bootCount || decreased
        val steps = if (rebooted) reading.rawCount else reading.rawCount - previous.rawCount
        if (steps <= 0) return StepCalculation(deltas = emptyList(), checkpoint = checkpoint)

        val gapStart = if (rebooted) maxOf(previous.timestamp, reading.bootTime) else previous.timestamp
        val deltas = if (Duration.between(gapStart, reading.timestamp) > GAP_THRESHOLD) {
            spread(steps, gapStart, reading.timestamp)
        } else {
            listOf(deltaAt(reading.timestamp, steps))
        }
        return StepCalculation(deltas = deltas, checkpoint = checkpoint)
    }

    private fun deltaAt(instant: Instant, steps: Long): StepDelta {
        val time = LocalDateTime.ofInstant(instant, zone)
        return StepDelta(date = time.toLocalDate(), hour = time.hour, steps = steps.toInt())
    }

    /**
     * [start, end) 를 KST 시간대 칸으로 자르고 칸 길이에 비례해 [steps]를 나눈다.
     * 내림한 뒤 남은 걸음은 나머지가 큰 칸부터 1보씩 더해 합계가 정확히 [steps]가 되게 한다 (최대 나머지 방식).
     */
    private fun spread(steps: Long, start: Instant, end: Instant): List<StepDelta> {
        val slots = mutableListOf<HourSlot>()
        var slotStart = start
        while (slotStart < end) {
            val nextHour = slotStart.atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()
            val slotEnd = minOf(nextHour, end)
            slots += HourSlot(start = slotStart, millis = Duration.between(slotStart, slotEnd).toMillis())
            slotStart = slotEnd
        }
        val totalMillis = slots.sumOf { it.millis }
        val shares = slots.map { slot -> steps * slot.millis / totalMillis }.toMutableList()
        val leftover = (steps - shares.sum()).toInt()
        slots.indices
            .sortedByDescending { index -> (steps * slots[index].millis) % totalMillis }
            .take(leftover)
            .forEach { index -> shares[index] += 1 }

        return slots.zip(shares)
            .filter { (_, share) -> share > 0 }
            .map { (slot, share) -> deltaAt(slot.start, share) }
    }

    /** 공백 구간을 시간대 경계로 자른 한 칸. [millis]는 그 칸이 구간 안에서 차지하는 길이. */
    private data class HourSlot(val start: Instant, val millis: Long)

    private companion object {
        /** 정상 동작 중의 최대 측정 간격(15분 스냅샷 워커)보다 넉넉히 길게 잡는다. */
        val GAP_THRESHOLD: Duration = Duration.ofHours(1)
    }
}
