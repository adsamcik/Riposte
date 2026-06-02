package com.adsamcik.riposte.testreceiver.service

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import com.adsamcik.riposte.testreceiver.telemetry.ShareOutcome
import com.adsamcik.riposte.testreceiver.telemetry.TelemetryRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs in `:remote` process. Reads URIs forwarded from
 * [com.adsamcik.riposte.testreceiver.activity.MultiProcessActivity] to verify
 * URI grants survive IPC.
 *
 * Because this is a separate process, the same-process [TelemetryRecorder.record]
 * fast path won't reach the provider's in-memory list (each process has its
 * own static state). We use [TelemetryRecorder.recordCrossProcess] instead,
 * which routes through ContentResolver and Binder.
 *
 * Note: this is NOT a foreground service. We accept that the OS may kill it
 * if it runs long; for short reads (millis) that's fine. Don't add delays here.
 */
class RemoteWorkerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val uris: List<Uri> =
            intent?.getParcelableArrayListExtra(EXTRA_URIS, Uri::class.java) ?: emptyList()

        scope.launch {
            val outcome = readAndBuildOutcome(uris)
            TelemetryRecorder.recordCrossProcess(contentResolver, outcome)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun readAndBuildOutcome(uris: List<Uri>): ShareOutcome {
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
            activityName = "RemoteWorkerService",
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URIS = "extra_uris"
    }
}
