package com.adsamcik.riposte.testreceiver.telemetry

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Exposes per-Activity share outcomes to Riposte's integration test process.
 *
 * Storage is intentionally process-local in-memory — the receiver app is
 * short-lived (each test starts it fresh via explicit Intent), and survival
 * across process death is not desired (stale outcomes would just confuse the
 * next test). When the test wants a clean slate, it calls [delete] on
 * [TelemetryContract.RESET_URI].
 *
 * Three URIs:
 * - `content://.../outcomes` — full history of share outcomes (ordered by id)
 * - `content://.../latest`   — single-row cursor of the most recent outcome
 * - `content://.../reset`    — delete to clear all stored outcomes
 *
 * NOTE: the in-memory list lives in the provider's process. The receiver
 * activities write to it via [TelemetryRecorder.record] (same process). The
 * test reads it via cross-process ContentResolver query — Android's binder
 * marshals the cursor rows over IPC, so the test sees a snapshot rather than
 * a live view. Each query gets a fresh snapshot.
 */
class ShareTelemetryProvider : ContentProvider() {

    private val matcher =
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(TelemetryContract.AUTHORITY, TelemetryContract.PATH_OUTCOMES, MATCH_OUTCOMES)
            addURI(TelemetryContract.AUTHORITY, TelemetryContract.PATH_LATEST, MATCH_LATEST)
            addURI(TelemetryContract.AUTHORITY, TelemetryContract.PATH_RESET, MATCH_RESET)
        }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val rows: List<ShareOutcome> =
            when (matcher.match(uri)) {
                MATCH_OUTCOMES -> outcomes.toList()
                MATCH_LATEST -> listOfNotNull(outcomes.lastOrNull())
                else -> emptyList()
            }
        return rows.toCursor()
    }

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? {
        // Receivers in the same process use TelemetryRecorder.record directly;
        // we still accept ContentValues inserts for completeness (e.g. a test
        // that wants to seed an outcome manually).
        if (matcher.match(uri) != MATCH_OUTCOMES || values == null) return null
        val outcome = values.toOutcome()
        outcomes.add(outcome)
        context?.contentResolver?.notifyChange(TelemetryContract.OUTCOMES_URI, null)
        context?.contentResolver?.notifyChange(TelemetryContract.LATEST_URI, null)
        return Uri.withAppendedPath(TelemetryContract.OUTCOMES_URI, outcome.id.toString())
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        return when (matcher.match(uri)) {
            MATCH_RESET, MATCH_OUTCOMES -> {
                val removed = outcomes.size
                outcomes.clear()
                nextId.set(1L)
                context?.contentResolver?.notifyChange(TelemetryContract.OUTCOMES_URI, null)
                context?.contentResolver?.notifyChange(TelemetryContract.LATEST_URI, null)
                removed
            }
            else -> 0
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun getType(uri: Uri): String? =
        when (matcher.match(uri)) {
            MATCH_OUTCOMES -> "vnd.android.cursor.dir/share-outcome"
            MATCH_LATEST -> "vnd.android.cursor.item/share-outcome"
            else -> null
        }

    private fun List<ShareOutcome>.toCursor(): Cursor {
        val cursor = MatrixCursor(TelemetryColumns.ALL)
        forEach { outcome ->
            cursor.addRow(
                arrayOf<Any?>(
                    outcome.id,
                    outcome.recordedAt,
                    outcome.activityName,
                    outcome.intentAction,
                    outcome.uris,
                    if (outcome.readSucceeded) 1 else 0,
                    outcome.bytesRead,
                    if (outcome.writeSucceeded) 1 else 0,
                    if (outcome.grantSucceeded) 1 else 0,
                    if (outcome.persistableTaken) 1 else 0,
                    outcome.exceptionClass,
                    outcome.exceptionMessage,
                ),
            )
        }
        return cursor
    }

    private fun ContentValues.toOutcome(): ShareOutcome {
        val id = nextId.getAndIncrement()
        return ShareOutcome(
            id = id,
            recordedAt = getAsLong(TelemetryColumns.RECORDED_AT) ?: System.currentTimeMillis(),
            activityName = getAsString(TelemetryColumns.ACTIVITY_NAME) ?: "",
            intentAction = getAsString(TelemetryColumns.INTENT_ACTION) ?: "",
            uris = getAsString(TelemetryColumns.URIS) ?: "",
            readSucceeded = (getAsInteger(TelemetryColumns.READ_SUCCEEDED) ?: 0) != 0,
            bytesRead = getAsLong(TelemetryColumns.BYTES_READ) ?: 0L,
            writeSucceeded = (getAsInteger(TelemetryColumns.WRITE_SUCCEEDED) ?: 0) != 0,
            grantSucceeded = (getAsInteger(TelemetryColumns.GRANT_SUCCEEDED) ?: 0) != 0,
            persistableTaken = (getAsInteger(TelemetryColumns.PERSISTABLE_TAKEN) ?: 0) != 0,
            exceptionClass = getAsString(TelemetryColumns.EXCEPTION_CLASS),
            exceptionMessage = getAsString(TelemetryColumns.EXCEPTION_MESSAGE),
        )
    }

    companion object {
        private const val MATCH_OUTCOMES = 1
        private const val MATCH_LATEST = 2
        private const val MATCH_RESET = 3

        // Shared across instances within the receiver process — receivers and
        // the provider both run in the default process, so a single static list
        // is correct here.
        private val outcomes = CopyOnWriteArrayList<ShareOutcome>()
        private val nextId = AtomicLong(1L)

        /** Append an outcome directly from same-process receiver code. */
        internal fun append(outcome: ShareOutcome) {
            val withId = outcome.copy(id = nextId.getAndIncrement())
            outcomes.add(withId)
        }
    }
}
