package com.adsamcik.riposte.feature.import_feature.data.worker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Manages notification channel creation and notification building for import work.
 * Used by [ImportWorker] when it needs to promote to a foreground service.
 */
class ImportNotificationManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** Creates the import notification channel. Safe to call multiple times. */
        fun createChannel() {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows progress when importing memes in the background"
                    setShowBadge(false)
                }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        /** Builds a progress notification for the foreground service. */
        fun buildProgressNotification(
            current: Int,
            total: Int,
        ): Notification {
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setContentTitle("Importing memes")
                .setContentText("$current of $total")
                .setProgress(total, current, false)
                .setOngoing(true)
                .setSilent(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }

        /** Builds a completion notification. */
        fun buildCompleteNotification(
            successCount: Int,
            failedCount: Int,
        ): Notification {
            val text =
                when {
                    failedCount == 0 -> "$successCount memes imported successfully"
                    successCount == 0 -> "Import failed for $failedCount images"
                    else -> "$successCount imported, $failedCount failed"
                }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentTitle("Import complete")
                .setContentText(text)
                .setAutoCancel(true)
                .setTimeoutAfter(AUTO_DISMISS_DURATION_MS)
                .build()
        }

        /** Posts a completion notification if the app has notification permission. */
        fun showCompleteNotification(
            successCount: Int,
            failedCount: Int,
        ) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val notification = buildCompleteNotification(successCount, failedCount)
                NotificationManagerCompat.from(context).notify(COMPLETE_NOTIFICATION_ID, notification)
            }
        }

        companion object {
            const val CHANNEL_ID = "import_progress"
            const val NOTIFICATION_ID = 1001
            const val COMPLETE_NOTIFICATION_ID = 1002
            private const val CHANNEL_NAME = "Import Progress"
            private const val AUTO_DISMISS_DURATION_MS = 5_000L
        }
    }
