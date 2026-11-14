package com.example.workingapp.domain.model

import java.time.Instant

/**
 * STEP_COUNTER 센서 한 번의 측정값.
 * - [rawCount]: 부팅 이후 누적 걸음 (재부팅 시 0부터 다시 시작)
 * - [bootCount]: 측정 시점의 부팅 횟수. 값이 바뀌면 재부팅이 있었다는 뜻. 읽을 수 없으면 [UNKNOWN_BOOT_COUNT]
 * - [timestamp]: 실제로 걸음이 측정된 시각 (센서 전달 지연을 보정한 값)
 * - [bootTime]: 기기가 부팅된 시각(현재 시각 - 부팅 후 경과 시간). 재부팅 후 걸음이 생길 수 있는 구간의 시작점이다
 */
data class StepReading(
    val rawCount: Long,
    val bootCount: Int,
    val timestamp: Instant,
    val bootTime: Instant,
) {
    companion object {
        const val UNKNOWN_BOOT_COUNT = -1
    }
}
