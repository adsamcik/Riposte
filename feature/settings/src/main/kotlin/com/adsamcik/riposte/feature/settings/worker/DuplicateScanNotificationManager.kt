package com.adsamcik.riposte.feature.settings.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Manages notification channel creation and notification building for duplicate scan work.
 * Used by [DuplicateScanWorker] to provide [androidx.work.ForegroundInfo].
 */
class DuplicateScanNotificationManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        /** Creates the duplicate scan notification channel. Safe to call multiple times. */
        fun createChannel() {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows progress when scanning for duplicate memes"
                    setShowBadge(false)
                }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        /** Builds a progress notification for the duplicate scan. */
        fun buildProgressNotification(): Notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentTitle("Scanning for duplicates")
                .setContentText("Checking your meme library…")
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setSilent(true)
                .build()

        companion object {
            const val CHANNEL_ID = "duplicate_scan"
            const val NOTIFICATION_ID = 3001
            private const val CHANNEL_NAME = "Duplicate Scan"
        }
    }
