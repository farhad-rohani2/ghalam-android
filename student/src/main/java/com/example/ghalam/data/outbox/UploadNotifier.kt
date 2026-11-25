package com.example.ghalam.data.outbox

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * One simple notification after a background upload finished. It never asks for a permission:
 * it is shown only when the system already allows it, and never while the app is on screen
 * (the screens show the same information).
 */
class UploadNotifier(
    private val context: Context,
    private val prefs: SubmitPrefs,
    private val appVisible: () -> Boolean,
) {
    @SuppressLint("MissingPermission")
    fun report(sentPhotos: Int, failed: Int) {
        if (!prefs.notifyResult || appVisible()) return
        if (sentPhotos == 0 && failed == 0) return

        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("وضعیت ارسال تکالیف")
                .build(),
        )

        val text = when {
            failed > 0 && sentPhotos > 0 -> "$sentPhotos عکس ارسال شد و $failed مورد ناموفق بود"
            failed > 0 -> "$failed مورد ارسال نشد؛ برنامه را باز کنید"
            else -> "$sentPhotos عکس تکلیف ارسال شد"
        }

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("تکالیف")
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private companion object {
        const val CHANNEL_ID = "upload_status"
        const val NOTIFICATION_ID = 4101
    }
}
