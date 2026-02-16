package com.adsamcik.riposte.core.ml.worker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.adsamcik.riposte.core.ml.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Manages notification channel creation and notification building for embedding indexing work.
 * Used by [EmbeddingGenerationWorker] when it needs to promote to a foreground service.
 */
class EmbeddingNotificationManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        /** Creates the embedding notification channel. Safe to call multiple times. */
        fun createChannel() {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.embedding_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.embedding_notification_channel_description)
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
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentTitle(context.getString(R.string.embedding_notification_title))
                .setContentText(context.getString(R.string.embedding_notification_progress, current, total))
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
                    failedCount == 0 ->
                        context.getString(R.string.embedding_notification_complete_success, successCount)
                    successCount == 0 ->
                        context.getString(R.string.embedding_notification_complete_failed, failedCount)
                    else ->
                        context.getString(R.string.embedding_notification_complete_partial, successCount, failedCount)
                }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentTitle(context.getString(R.string.embedding_notification_complete_title))
                .setContentText(text)
                .setAutoCancel(true)
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
            const val CHANNEL_ID = "embedding_indexing"
            const val NOTIFICATION_ID = 2001
            const val COMPLETE_NOTIFICATION_ID = 2002
        }
    }
