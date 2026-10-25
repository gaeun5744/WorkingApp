package com.example.workingapp.service

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.workingapp.domain.repository.StepRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 앱이 화면에 없어도 걸음을 측정하는 Foreground Service (type=health).
 * 센서 측정값을 받아 [StepRepository.record] 로 반영하고, 상시 알림에 오늘 걸음을 보여준다.
 */
@AndroidEntryPoint
class StepCounterService : LifecycleService() {

    @Inject internal lateinit var repository: StepRepository
    @Inject internal lateinit var notificationFactory: StepNotificationFactory

    private var trackingJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        notificationFactory.createChannel()
        startForeground(
            StepNotificationFactory.NOTIFICATION_ID,
            notificationFactory.build(todaySteps = 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
        )
        if (trackingJob == null) trackingJob = startTracking()
        // 시스템이 서비스를 종료해도 다시 살려 측정을 이어간다
        return START_STICKY
    }

    private fun startTracking(): Job = lifecycleScope.launch {
        launch {
            repository.readings()
                .catch { e -> Timber.e(e, "걸음 센서 수신 실패") }
                .collect { reading ->
                    runCatching { repository.record(reading) }
                        .onFailure { e -> Timber.e(e, "걸음 기록 실패") }
                }
        }
        launch {
            repository.observeTodaySteps().collect { today -> notificationFactory.update(today.steps) }
        }
    }

    companion object {
        /**
         * 걸음 측정 권한이 있을 때만 서비스를 시작한다.
         * 백그라운드 시작 제한 등으로 실패하면 false 를 돌려주고, 15분 스냅샷 워커가 측정을 보완한다.
         */
        fun startIfPermitted(context: Context): Boolean {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return false
            return try {
                ContextCompat.startForegroundService(context, Intent(context, StepCounterService::class.java))
                true
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Timber.w(e, "Foreground Service 시작 불가")
                false
            }
        }
    }
}
