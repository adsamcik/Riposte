package com.adsamcik.riposte.share

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Test-side mirror of [com.adsamcik.riposte.testreceiver.telemetry.TelemetryContract]
 * and friends. Intentionally duplicated rather than depending on the receiver
 * module — the receiver is an Android app, not a library, so cross-module type
 * sharing would require an artificial intermediate library. Cheap to mirror;
 * if the receiver renames a column, the test will fail loudly on the next run.
 */
object TestReceiver {

    const val PACKAGE = "com.adsamcik.riposte.testreceiver"

    /** Cross-process URIs used to read or reset the receiver's recorded outcomes. */
    val OUTCOMES_URI: Uri = Uri.parse("content://$PACKAGE.telemetry/outcomes")
    val LATEST_URI: Uri = Uri.parse("content://$PACKAGE.telemetry/latest")
    val RESET_URI: Uri = Uri.parse("content://$PACKAGE.telemetry/reset")

    object Activities {
        const val HAPPY_PATH = "$PACKAGE.activity.HappyPathActivity"
        const val DISCORD_STYLE = "$PACKAGE.activity.DiscordStyleActivity"
        const val ARRAY_LIST_ONLY = "$PACKAGE.activity.ArrayListOnlyActivity"
        const val CRASH_ON_SECOND_SHARE = "$PACKAGE.activity.CrashOnSecondShareActivity"
        const val WRITE_REQUIRED = "$PACKAGE.activity.WriteRequiredActivity"
        const val PERSISTABLE = "$PACKAGE.activity.PersistableActivity"
        const val LATE_READ = "$PACKAGE.activity.LateReadActivity"
        const val MULTI_PROCESS = "$PACKAGE.activity.MultiProcessActivity"
        const val MULTI_SHARE = "$PACKAGE.activity.MultiShareActivity"
    }

    object Columns {
        const val ID = "_id"
        const val RECORDED_AT = "recorded_at"
        const val ACTIVITY_NAME = "activity_name"
        const val INTENT_ACTION = "intent_action"
        const val URIS = "uris"
        const val READ_SUCCEEDED = "read_succeeded"
        const val BYTES_READ = "bytes_read"
        const val WRITE_SUCCEEDED = "write_succeeded"
        const val GRANT_SUCCEEDED = "grant_succeeded"
        const val PERSISTABLE_TAKEN = "persistable_taken"
        const val EXCEPTION_CLASS = "exception_class"
        const val EXCEPTION_MESSAGE = "exception_message"
    }

    /** Snapshot of one share outcome read from the receiver's telemetry provider. */
    data class Outcome(
        val id: Long,
        val recordedAt: Long,
        val activityName: String,
        val intentAction: String,
        val uris: String,
        val readSucceeded: Boolean,
        val bytesRead: Long,
        val writeSucceeded: Boolean,
        val grantSucceeded: Boolean,
        val persistableTaken: Boolean,
        val exceptionClass: String?,
        val exceptionMessage: String?,
    )

    /**
     * Build an explicit Intent targeting one of the receiver's activities.
     * Caller is responsible for setting EXTRA_STREAM and grant flags.
     */
    fun explicitIntent(
        action: String,
        activityFqcn: String,
    ): Intent =
        Intent(action).apply {
            component = ComponentName(PACKAGE, activityFqcn)
            // Required when starting an Activity from a non-Activity context
            // (instrumentation's getContext() returns Application context).
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}

/**
 * Cross-process client for the receiver's telemetry provider. Used by
 * integration tests to assert what the receiver observed.
 *
 * `await*` methods poll with a small backoff because the receiver writes its
 * outcome asynchronously w.r.t. our `startActivity` return — even though the
 * write itself is synchronous in onCreate, the activity launch + IPC takes
 * a few hundred ms.
 */
class TestReceiverClient(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    /** Wipe all recorded outcomes. Call from `@Before` to isolate each test. */
    fun reset() {
        resolver.delete(TestReceiver.RESET_URI, null, null)
    }

    /** Read every outcome recorded by the receiver so far. */
    fun all(): List<TestReceiver.Outcome> = readOutcomes(TestReceiver.OUTCOMES_URI)

    /** Read the most recent outcome, if any. */
    fun latest(): TestReceiver.Outcome? = readOutcomes(TestReceiver.LATEST_URI).firstOrNull()

    /**
     * Poll for the most recent outcome until one matching [activityName]
     * appears, or [timeoutMs] elapses. Returns null on timeout.
     */
    fun awaitLatestFor(
        activityName: String,
        timeoutMs: Long = 10_000L,
        pollIntervalMs: Long = 100L,
    ): TestReceiver.Outcome? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val candidate = latest()
            if (candidate != null && candidate.activityName == activityName) {
                return candidate
            }
            Thread.sleep(pollIntervalMs)
        }
        return null
    }

    private fun readOutcomes(uri: Uri): List<TestReceiver.Outcome> {
        val out = mutableListOf<TestReceiver.Outcome>()
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(TestReceiver.Columns.ID)
            val recAtIdx = cursor.getColumnIndexOrThrow(TestReceiver.Columns.RECORDED_AT)
            val activityIdx = cursor.getColumnIndexOrThrow(TestReceiver.Columns.ACTIVITY_NAME)
            val actionIdx = cursor.getColumnIndexOrThrow(TestReceiver.Columns.INTENT_ACTION)
            val urisIdx = cursor.getColumnIndexOrThrow(TestReceiver.Columns.URIS)
            val readIdx = cursor.getColumnIndexOrThrow(TestReceiver.Columns.READ_SUCCEEDED)
            val bytesIdx = cursor.getColumnIndexOrThrow(TestReceiver.Columns.BYTES_READ)
            val writeIdx = cursor.getColumnIndexOrThrow(TestReceiver.Columns.WRITE_SUCCEEDED)
            val grantIdx = cursor.getColumnIndexOrThrow(TestReceiver.Columns.GRANT_SUCCEEDED)
            val persistableIdx = cursor.getColumnIndexOrThrow(TestReceiver.Columns.PERSISTABLE_TAKEN)
            val excClassIdx = cursor.getColumnIndexOrThrow(TestReceiver.Columns.EXCEPTION_CLASS)
            val excMsgIdx = cursor.getColumnIndexOrThrow(TestReceiver.Columns.EXCEPTION_MESSAGE)
            while (cursor.moveToNext()) {
                out.add(
                    TestReceiver.Outcome(
                        id = cursor.getLong(idIdx),
                        recordedAt = cursor.getLong(recAtIdx),
                        activityName = cursor.getString(activityIdx),
                        intentAction = cursor.getString(actionIdx),
                        uris = cursor.getString(urisIdx),
                        readSucceeded = cursor.getInt(readIdx) != 0,
                        bytesRead = cursor.getLong(bytesIdx),
                        writeSucceeded = cursor.getInt(writeIdx) != 0,
                        grantSucceeded = cursor.getInt(grantIdx) != 0,
                        persistableTaken = cursor.getInt(persistableIdx) != 0,
                        exceptionClass =
                            if (cursor.isNull(excClassIdx)) null else cursor.getString(excClassIdx),
                        exceptionMessage =
                            if (cursor.isNull(excMsgIdx)) null else cursor.getString(excMsgIdx),
                    ),
                )
            }
        }
        return out
    }
}
