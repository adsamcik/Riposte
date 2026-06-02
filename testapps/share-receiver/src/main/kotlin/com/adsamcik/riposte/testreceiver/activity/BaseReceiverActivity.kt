package com.adsamcik.riposte.testreceiver.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.adsamcik.riposte.testreceiver.telemetry.ShareOutcome
import com.adsamcik.riposte.testreceiver.telemetry.TelemetryRecorder
import java.io.InputStream

/**
 * Shared scaffolding for every test receiver activity.
 *
 * Subclasses implement [handleShare] with their specific (mis)behavior. The
 * base class takes care of URI extraction, exception capture, outcome
 * recording, and finishing the activity — so the bug-pattern implementation
 * itself stays tiny and focused.
 *
 * Any uncaught throwable in [handleShare] is converted to a failure outcome
 * — that's important because for some test scenarios (e.g. DiscordStyleActivity
 * on FileProvider URIs) the exception IS the expected outcome. We don't let
 * the activity crash the receiver process; we record the exception type and
 * finish cleanly so the next test can run.
 */
abstract class BaseReceiverActivity : Activity() {

    protected val activityName: String
        get() = javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val outcome =
            @Suppress("TooGenericExceptionCaught")
            try {
                val uris = extractUris(intent)
                handleShare(uris)
            } catch (e: Throwable) {
                ShareOutcome(
                    recordedAt = System.currentTimeMillis(),
                    activityName = activityName,
                    intentAction = intent.action.orEmpty(),
                    uris = extractUrisSafe(intent).joinToString(","),
                    readSucceeded = false,
                    bytesRead = 0,
                    writeSucceeded = false,
                    grantSucceeded = false,
                    persistableTaken = false,
                    exceptionClass = e.javaClass.name,
                    exceptionMessage = e.message,
                )
            }
        TelemetryRecorder.record(outcome)
        finish()
    }

    /**
     * Implement the specific (mis)behavior for this receiver. May throw —
     * the base class will catch and convert to a failure outcome.
     */
    protected abstract fun handleShare(uris: List<Uri>): ShareOutcome

    protected fun successOutcome(
        uris: List<Uri>,
        bytesRead: Long = 0,
        readSucceeded: Boolean = bytesRead > 0,
        writeSucceeded: Boolean = false,
        grantSucceeded: Boolean = false,
        persistableTaken: Boolean = false,
    ): ShareOutcome =
        ShareOutcome(
            recordedAt = System.currentTimeMillis(),
            activityName = activityName,
            intentAction = intent.action.orEmpty(),
            uris = uris.joinToString(",") { it.toString() },
            readSucceeded = readSucceeded,
            bytesRead = bytesRead,
            writeSucceeded = writeSucceeded,
            grantSucceeded = grantSucceeded,
            persistableTaken = persistableTaken,
            exceptionClass = null,
            exceptionMessage = null,
        )

    /**
     * Read the URI contents fully and return the byte count.
     * Uses content resolver; works for any content:// scheme.
     */
    protected fun readUriBytes(uri: Uri): Long {
        val resolver = contentResolver
        return resolver.openInputStream(uri).use { stream: InputStream? ->
            stream?.readBytes()?.size?.toLong() ?: 0L
        }
    }

    private fun extractUris(intent: Intent): List<Uri> {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val single =
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                listOfNotNull(single)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    ?: emptyList()
            }
            else -> emptyList()
        }
    }

    /** Same as [extractUris] but swallows extraction errors — used when we're already in a catch block. */
    private fun extractUrisSafe(intent: Intent): List<Uri> =
        @Suppress("TooGenericExceptionCaught")
        try {
            extractUris(intent)
        } catch (_: Throwable) {
            emptyList()
        }
}
