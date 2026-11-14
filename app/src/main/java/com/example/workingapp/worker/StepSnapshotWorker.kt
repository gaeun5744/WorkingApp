package com.example.workingapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.workingapp.domain.repository.StepRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * 측정 서비스가 시스템에 의해 종료됐을 때를 대비해 15분마다 센서값을 한 번 읽어 반영한다.
 * 측정 간격을 짧게 유지해 자정을 넘긴 증분이 다음 날로 몰리는 오차도 줄인다.
 */
@HiltWorker
class StepSnapshotWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: StepRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!repository.isSensorAvailable) return Result.success()
        // STEP_COUNTER 는 걸음이 없으면 이벤트를 늦게 줄 수 있어 제한 시간 안에 못 받으면 이번 회차는 건너뛴다
        val reading = withTimeoutOrNull(READ_TIMEOUT_MILLIS) { repository.readings().first() }
            ?: return Result.success()
        repository.record(reading)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "step_snapshot"
        private const val READ_TIMEOUT_MILLIS = 10_000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StepSnapshotWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
