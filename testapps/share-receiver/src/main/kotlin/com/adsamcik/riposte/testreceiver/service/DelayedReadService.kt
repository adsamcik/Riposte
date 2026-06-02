package com.adsamcik.riposte.testreceiver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import com.adsamcik.riposte.testreceiver.telemetry.ShareOutcome
import com.adsamcik.riposte.testreceiver.telemetry.TelemetryRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reads URIs after a [READ_DELAY_MS] delay. Used by [LateReadActivity] to
 * model receivers that defer URI reads (upload pipelines, batching).
 *
 * Foreground service so it survives the calling activity finishing.
 * Records its outcome via [TelemetryRecorder.record] (same process as the
 * provider, so no cross-process insert needed).
 *
 * If the delayed read fails — e.g. Riposte cleaned up the MediaStore entry
 * before we got to it — the exception is captured into the outcome rather
 * than crashing the process. That's exactly the signal the test wants to
 * assert against.
 */
class DelayedReadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startInForeground()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val uris: List<Uri> =
            intent?.getParcelableArrayListExtra(EXTRA_URIS, Uri::class.java) ?: emptyList()

        scope.launch {
            delay(READ_DELAY_MS)
            val outcome = readAndRecord(uris)
            TelemetryRecorder.record(outcome)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun readAndRecord(uris: List<Uri>): ShareOutcome {
        var totalBytes = 0L
        var exceptionClass: String? = null
        var exceptionMessage: String? = null

        for (uri in uris) {
            @Suppress("TooGenericExceptionCaught")
            try {
                contentResolver.openInputStream(uri).use { stream ->
                    totalBytes += (stream?.readBytes()?.size?.toLong() ?: 0L)
                }
            } catch (e: Throwable) {
                exceptionClass = e.javaClass.name
                exceptionMessage = e.message
                break
            }
        }

        return ShareOutcome(
            recordedAt = System.currentTimeMillis(),
            activityName = "DelayedReadService",
            intentAction = "android.intent.action.SEND",
            uris = uris.joinToString(",") { it.toString() },
            readSucceeded = totalBytes > 0 && exceptionClass == null,
            bytesRead = totalBytes,
            writeSucceeded = false,
            grantSucceeded = false,
            persistableTaken = false,
            exceptionClass = exceptionClass,
            exceptionMessage = exceptionMessage,
        )
    }

    private fun startInForeground() {
        val notification: Notification =
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Riposte test receiver")
                .setContentText("Reading shared URI on a delay")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setOngoing(true)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm?.getNotificationChannel(CHANNEL_ID) == null) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Delayed share read",
                    NotificationManager.IMPORTANCE_LOW,
                )
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URIS = "extra_uris"

        /** Roughly enough to cover any reasonable post-share cleanup window. */
        private const val READ_DELAY_MS = 5_000L

        private const val CHANNEL_ID = "delayed_read"
        private const val FOREGROUND_ID = 1001
    }
}
