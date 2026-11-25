package com.example.ghalam.data.outbox

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
import com.example.ghalam.data.Graph
import java.util.concurrent.TimeUnit

/**
 * The durable part of sending: WorkManager starts the engine once the device has internet, even
 * when the app was closed or the phone restarted. While the app is open the engine is also started
 * directly (see Graph.kickUpload) and by every connectivity event.
 */
class UploadScheduler(context: Context, private val prefs: SubmitPrefs) {
    private val wm = WorkManager.getInstance(context.applicationContext)

    private fun constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    private fun oneTime() = OneTimeWorkRequestBuilder<UploadWorker>()
        .setConstraints(constraints())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    /** Makes sure one run is waiting for internet. An already waiting or running one is kept. */
    fun ensureWorker() {
        wm.enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.KEEP, oneTime())
    }

    /** The network setting changed: the waiting run must use the new rule. */
    fun replaceWorker() {
        wm.enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, oneTime())
    }

    /** Safety net: once an hour, only does something when the outbox is not empty. */
    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<UploadWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints())
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelAll() {
        wm.cancelUniqueWork(ONE_TIME)
        wm.cancelUniqueWork(PERIODIC)
    }

    private companion object {
        const val ONE_TIME = "student-upload"
        const val PERIODIC = "student-upload-periodic"
    }
}

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (Graph.engine.run()) {
        UploadEngine.Outcome.RETRY -> Result.retry()
        UploadEngine.Outcome.DONE, UploadEngine.Outcome.SIGNED_OUT -> Result.success()
    }
}
