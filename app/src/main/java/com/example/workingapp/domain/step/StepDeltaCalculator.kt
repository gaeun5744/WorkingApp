package com.example.workingapp.domain.step

import com.example.workingapp.domain.model.StepReading
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 센서 누적값을 "언제 몇 걸음"으로 바꾸는 순수 계산기.
 *
 * baseline(0시 기준값) 방식 대신 측정마다 직전 값과의 증분을 측정 시각의 (날짜, 시간대)에 더한다.
 * 자정 리셋이 따로 필요 없고 시간대별 집계가 함께 나온다.
 * 알려진 한계: 두 측정 사이에 자정이 끼면 증분 전체가 뒤 측정의 날짜로 들어간다.
 * 걸음마다 오는 센서 이벤트와 15분 스냅샷으로 측정 간격을 짧게 유지해 오차를 줄인다.
 */
class StepDeltaCalculator(private val zone: ZoneId) {

    fun calculate(previous: CounterCheckpoint?, reading: StepReading): StepCalculation {
        val checkpoint = CounterCheckpoint(reading.rawCount, reading.bootCount, reading.timestamp)
        if (previous == null) return StepCalculation(delta = null, checkpoint = checkpoint)

        // 값 감소만으로 재부팅을 판단하면 "재부팅 후 직전 값보다 많이 걸은 경우"를 놓치므로 부팅 횟수를 우선 본다.
        // 같은 부팅 안에서 누적값은 줄지 않으므로, 부팅 횟수가 같은데 값이 줄었다면 서비스·워커가 읽은
        // 측정이 순서가 바뀌어 늦게 도착한 것이다. 재부팅으로 오인하면 걸음이 크게 중복되므로 무시한다.
        val bootKnown = reading.bootCount != StepReading.UNKNOWN_BOOT_COUNT
        val decreased = reading.rawCount < previous.rawCount
        if (bootKnown && reading.bootCount == previous.bootCount && decreased) {
            return StepCalculation(delta = null, checkpoint = previous)
        }
        val rebooted = reading.bootCount != previous.bootCount || decreased
        val steps = if (rebooted) reading.rawCount else reading.rawCount - previous.rawCount
        if (steps <= 0) return StepCalculation(delta = null, checkpoint = checkpoint)

        val measuredAt = LocalDateTime.ofInstant(reading.timestamp, zone)
        return StepCalculation(
            delta = StepDelta(date = measuredAt.toLocalDate(), hour = measuredAt.hour, steps = steps.toInt()),
            checkpoint = checkpoint,
        )
    }
}
