package com.example.workingapp.domain.step

/**
 * 걸음 수를 거리·칼로리·시간으로 환산한 값.
 * 키·몸무게 입력은 비범위라 성인 평균값으로 계산한다: 보폭 0.7m, 걸음당 0.04kcal, 분당 100보.
 */
data class StepMetrics(
    val distanceKm: Double,
    val kcal: Int,
    val minutes: Int,
) {
    companion object {
        private const val STRIDE_METERS = 0.7
        private const val KCAL_PER_STEP = 0.04
        private const val STEPS_PER_MINUTE = 100

        fun from(steps: Int): StepMetrics = StepMetrics(
            distanceKm = steps * STRIDE_METERS / 1_000,
            kcal = (steps * KCAL_PER_STEP).toInt(),
            minutes = steps / STEPS_PER_MINUTE,
        )
    }
}
