package com.example.workingapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.workingapp.domain.model.AppError
import com.example.workingapp.domain.repository.AccountRepository
import com.example.workingapp.domain.repository.StepRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** 미동기화 걸음을 서버에 올린다. 네트워크가 있을 때만 실행하고, 일시적 실패는 지수 백오프로 재시도한다. */
@HiltWorker
class StepSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accountRepository: AccountRepository,
    private val stepRepository: StepRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!accountRepository.hasSignedUp()) return Result.success()
        return stepRepository.syncPendingSteps().fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                when (error) {
                    AppError.Network, AppError.Server -> Result.retry()
                    else -> Result.failure()
                }
            },
        )
    }

    companion object {
        private const val PERIODIC_NAME = "step_sync_periodic"
        private const val IMMEDIATE_NAME = "step_sync_now"
        private const val BACKOFF_SECONDS = 30L

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<StepSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** 앱 진입·온보딩 완료 시 즉시 한 번 동기화한다. */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<StepSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
