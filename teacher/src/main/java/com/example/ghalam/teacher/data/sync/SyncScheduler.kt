package com.example.ghalam.teacher.data.sync

import android.content.Context
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
import androidx.work.workDataOf
import com.example.ghalam.teacher.data.Graph
import java.util.concurrent.TimeUnit

/** Background sync: every 3 hours when there is internet, and right away when asked. */
class SyncScheduler(context: Context) {
    private val wm = WorkManager.getInstance(context.applicationContext)

    private val online = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Upload + download once the device is online. [full] = the manual refresh button: it replaces a
     * queued automatic run; an automatic run never interrupts a running one.
     */
    fun syncNow(full: Boolean) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setInputData(workDataOf(SyncWorker.KEY_FULL to full))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniqueWork(NOW, if (full) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    /** Called after every saved feedback: sends the queue as soon as there is internet. */
    fun enqueueUpload() {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniqueWork(UPLOAD, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun cancelAll() {
        wm.cancelUniqueWork(PERIODIC)
        wm.cancelUniqueWork(NOW)
        wm.cancelUniqueWork(UPLOAD)
    }

    private companion object {
        const val PERIODIC = "teacher-periodic-sync"
        const val NOW = "teacher-sync-now"
        const val UPLOAD = "teacher-upload"
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Graph.token.value.isNullOrBlank()) return Result.success() // signed out: nothing to do
        val full = inputData.getBoolean(KEY_FULL, false)
        return when (Graph.sync.run(full = full)) {
            SyncEngine.Outcome.OK, SyncEngine.Outcome.SIGNED_OUT -> Result.success()
            SyncEngine.Outcome.RETRY -> Result.retry()
        }
    }

    companion object {
        const val KEY_FULL = "full"
    }
}

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Graph.token.value.isNullOrBlank()) return Result.success()
        return when (Graph.sync.run(full = false, uploadOnly = true)) {
            SyncEngine.Outcome.OK, SyncEngine.Outcome.SIGNED_OUT -> Result.success()
            SyncEngine.Outcome.RETRY -> Result.retry()
        }
    }
}
