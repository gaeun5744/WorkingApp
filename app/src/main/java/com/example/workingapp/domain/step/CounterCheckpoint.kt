package com.example.workingapp.domain.step

import java.time.Instant

/** 직전에 반영한 센서 측정값. 다음 측정과의 차이(증분)를 계산하는 기준이 된다. */
data class CounterCheckpoint(
    val rawCount: Long,
    val bootCount: Int,
    val timestamp: Instant,
)
