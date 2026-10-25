package com.example.workingapp.domain.step

/**
 * 측정값 하나를 처리한 결과.
 * [delta]와 [checkpoint]는 반드시 함께(한 트랜잭션으로) 저장해야 걸음이 중복·누락되지 않는다.
 */
data class StepCalculation(
    val delta: StepDelta?,
    val checkpoint: CounterCheckpoint,
)
