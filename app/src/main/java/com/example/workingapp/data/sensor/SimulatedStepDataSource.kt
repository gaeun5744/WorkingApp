package com.example.workingapp.data.sensor

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.example.workingapp.domain.model.StepReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import javax.inject.Inject

/**
 * 하드웨어 걸음 센서가 없는 에뮬레이터 검증용 (`-PfakeStepSensor=true`).
 * 누적값을 부팅 후 경과 시간으로 만들어 실제 STEP_COUNTER 처럼 프로세스 재시작에는 유지되고
 * 재부팅하면 0부터 다시 시작한다.
 */
internal class SimulatedStepDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : StepDataSource {

    override val isAvailable: Boolean = true

    override fun readings(): Flow<StepReading> = flow {
        while (true) {
            emit(
                StepReading(
                    rawCount = SystemClock.elapsedRealtime() / MILLIS_PER_STEP,
                    bootCount = Settings.Global.getInt(
                        context.contentResolver,
                        Settings.Global.BOOT_COUNT,
                        StepReading.UNKNOWN_BOOT_COUNT,
                    ),
                    timestamp = clock.instant(),
                ),
            )
            delay(EMIT_INTERVAL_MILLIS)
        }
    }

    private companion object {
        // 1초에 1걸음 (시간당 3,600보 — 서버 시간당 상한 12,000 이내)
        const val MILLIS_PER_STEP = 1_000L
        const val EMIT_INTERVAL_MILLIS = 5_000L
    }
}
