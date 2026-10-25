package com.example.workingapp.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.provider.Settings
import com.example.workingapp.domain.model.StepReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Clock
import javax.inject.Inject

/** 하드웨어 STEP_COUNTER 센서 구현. */
internal class AndroidStepDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : StepDataSource {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    override val isAvailable: Boolean get() = sensor != null

    override fun readings(): Flow<StepReading> {
        val stepSensor = sensor ?: return emptyFlow()
        val manager = sensorManager ?: return emptyFlow()
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    trySend(event.toReading())
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            // 배칭(maxReportLatency)을 걸지 않는다. 삼성 기기에서 30초 배칭을 요청하면 HAL 이 걸음 변화를
            // 모아 띄엄띄엄 보고해 화면이 실시간으로 갱신되지 않았다 (docs/architecture.md §3 디버깅 기록).
            // 누적 카운터라 중간 이벤트를 놓쳐도 최신 값이 전체를 담고, non-wakeup 센서라 화면이 꺼지면 CPU 를 깨우지 않는다.
            manager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            awaitClose { manager.unregisterListener(listener) }
        }
    }

    // event.timestamp 는 부팅 이후 경과 시간(ns)이라, 늦게 전달된 만큼을 현재 시각에서 빼 실제 측정 시각을 구한다.
    // 센서를 등록하면 직전 값이 즉시 다시 전달되는데(실기기에서 239초 전 값 확인), 이 값도 원래 측정 시각으로 기록된다
    private fun SensorEvent.toReading(): StepReading {
        val delayNanos = (SystemClock.elapsedRealtimeNanos() - timestamp).coerceAtLeast(0)
        return StepReading(
            rawCount = values[0].toLong(),
            bootCount = readBootCount(),
            timestamp = clock.instant().minusNanos(delayNanos),
        )
    }

    private fun readBootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, StepReading.UNKNOWN_BOOT_COUNT)
}
