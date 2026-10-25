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
import java.time.Duration
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
            // 배칭: 하드웨어가 최대 MAX_REPORT_LATENCY 동안 이벤트를 모아 한 번에 전달해 배터리를 아낀다
            manager.registerListener(
                listener,
                stepSensor,
                SensorManager.SENSOR_DELAY_NORMAL,
                (MAX_REPORT_LATENCY.toMillis() * MICROS_PER_MILLI).toInt(),
            )
            awaitClose { manager.unregisterListener(listener) }
        }
    }

    // event.timestamp 는 부팅 이후 경과 시간(ns)이라, 배칭으로 늦게 전달된 만큼을 현재 시각에서 빼 실제 측정 시각을 구한다
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

    private companion object {
        val MAX_REPORT_LATENCY: Duration = Duration.ofSeconds(30)
        const val MICROS_PER_MILLI = 1_000L
    }
}
