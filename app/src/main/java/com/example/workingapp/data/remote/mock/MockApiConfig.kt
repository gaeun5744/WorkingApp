package com.example.workingapp.data.remote.mock

import java.time.Duration

/**
 * Mock 서버 동작 설정.
 * - [latencyMillis]: 응답 지연 범위 (로딩 UI 확인용)
 * - [failureRate]: 0.0~1.0, 이 확률로 500 INTERNAL_ERROR 를 돌려준다 (오류·재시도 UI 확인용)
 * - [accessTokenTtl]: 실제 계약(1시간)보다 짧게 두면 401 → 재발급 흐름을 자주 재현할 수 있다
 */
internal data class MockApiConfig(
    val latencyMillis: LongRange,
    val failureRate: Double,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
)
