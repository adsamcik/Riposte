package com.adsamcik.riposte.testreceiver.telemetry

import android.content.ContentResolver
import android.content.ContentValues

/**
 * Convenience writer used by receiver Activities to record what happened
 * during a share. Wraps both the in-process append (fast path) and the
 * cross-process ContentResolver insert (works even from the :remote process).
 *
 * The default is the in-process append because all activities run in the
 * default process. The cross-process insert is reserved for services that
 * declare android:process=":remote" (see RemoteWorkerService).
 */
object TelemetryRecorder {

    /** Records an outcome from same-process code (Activities, default-process services). */
    fun record(outcome: ShareOutcome) {
        ShareTelemetryProvider.append(outcome)
    }

    /**
     * Records an outcome from a different process via ContentResolver.
     * Higher latency than [record] but works across process boundaries.
     */
    fun recordCrossProcess(
        resolver: ContentResolver,
        outcome: ShareOutcome,
    ) {
        val values =
            ContentValues().apply {
                put(TelemetryColumns.RECORDED_AT, outcome.recordedAt)
                put(TelemetryColumns.ACTIVITY_NAME, outcome.activityName)
                put(TelemetryColumns.INTENT_ACTION, outcome.intentAction)
                put(TelemetryColumns.URIS, outcome.uris)
                put(TelemetryColumns.READ_SUCCEEDED, if (outcome.readSucceeded) 1 else 0)
                put(TelemetryColumns.BYTES_READ, outcome.bytesRead)
                put(TelemetryColumns.WRITE_SUCCEEDED, if (outcome.writeSucceeded) 1 else 0)
                put(TelemetryColumns.GRANT_SUCCEEDED, if (outcome.grantSucceeded) 1 else 0)
                put(TelemetryColumns.PERSISTABLE_TAKEN, if (outcome.persistableTaken) 1 else 0)
                put(TelemetryColumns.EXCEPTION_CLASS, outcome.exceptionClass)
                put(TelemetryColumns.EXCEPTION_MESSAGE, outcome.exceptionMessage)
            }
        resolver.insert(TelemetryContract.OUTCOMES_URI, values)
    }
}
