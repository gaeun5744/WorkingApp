package com.example.workingapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 재부팅·앱 업데이트 뒤 측정 서비스를 다시 시작한다. */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in HANDLED_ACTIONS) StepCounterService.startIfPermitted(context)
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
