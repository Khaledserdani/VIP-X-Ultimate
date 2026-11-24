package com.vipx.ultimate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf

internal class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            setForeground(createForegroundInfo(0))
            var lastReportedProgress = -1
            LocalLlmRuntime.downloadModel(applicationContext) { percent ->
                if (percent != lastReportedProgress) {
                    lastReportedProgress = percent
                    setProgressAsync(workDataOf(KEY_PROGRESS to percent))
                    updateNotification(percent)
                }
            }
            Result.success(workDataOf(KEY_PROGRESS to 100))
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            // LocalLlmRuntime already performs bounded retries and preserves verified partial bytes.
            // Do not wrap those retries in another WorkManager retry loop: that resets UI progress
            // and can make a near-complete download appear to restart forever.
            Result.failure(workDataOf(KEY_ERROR to (error.message ?: "تعذر تنزيل النموذج")))
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        createNotificationChannel()
        val notification = buildNotification(progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(progress: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(progress))
    }

    private fun buildNotification(progress: Int) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VIP X • تنزيل الذكاء المحلي")
            .setContentText("تنزيل نموذج Qwen3: $progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "تنزيل النموذج المحلي", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "vipx_qwen3_model_download"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "vipx_model_download"
        private const val NOTIFICATION_ID = 2406
    }
}
