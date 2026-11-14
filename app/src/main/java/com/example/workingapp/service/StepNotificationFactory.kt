package com.example.workingapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.workingapp.MainActivity
import com.example.workingapp.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Foreground Service 상시 알림 (OS 요구사항). 그 외 알림은 비범위 (PRD Q14).
 * 소리·진동 없이 조용히 오늘 걸음만 보여준다.
 */
internal class StepNotificationFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_step_tracking),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun build(todaySteps: Int): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_steps)
            .setContentTitle(context.getString(R.string.notification_today_steps, todaySteps))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    fun update(todaySteps: Int) {
        context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, build(todaySteps))
    }

    companion object {
        const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "step_tracking"
    }
}
