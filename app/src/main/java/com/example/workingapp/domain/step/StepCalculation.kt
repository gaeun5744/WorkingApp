package com.example.workingapp.domain.step

/**
 * 측정값 하나를 처리한 결과.
 * [deltas]와 [checkpoint]는 반드시 함께(한 트랜잭션으로) 저장해야 걸음이 중복·누락되지 않는다.
 * 측정 공백이 길면 증분 하나가 여러 시간대로 나뉘어 [deltas]가 여러 개가 된다.
 */
data class StepCalculation(
    val deltas: List<StepDelta>,
    val checkpoint: CounterCheckpoint,
)
