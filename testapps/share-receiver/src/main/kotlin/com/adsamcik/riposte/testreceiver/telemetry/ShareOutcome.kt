package com.adsamcik.riposte.testreceiver.telemetry

/**
 * Schema for a single share-handling outcome recorded by a receiver Activity.
 *
 * Stable wire format read by Riposte's integration test via [ShareTelemetryProvider]
 * — keep column names in sync if you change them and update [TelemetryColumns].
 */
data class ShareOutcome(
    /** Monotonically increasing row id assigned by the provider. */
    val id: Long = 0,
    /** When the receiver finished processing (millis since epoch). */
    val recordedAt: Long,
    /** Simple class name of the receiving activity, e.g. "HappyPathActivity". */
    val activityName: String,
    /** Either ACTION_SEND or ACTION_SEND_MULTIPLE. */
    val intentAction: String,
    /** The URI(s) we received via EXTRA_STREAM, comma-joined for multi-share. */
    val uris: String,
    /** True if the receiver successfully read at least one byte from the URI. */
    val readSucceeded: Boolean,
    /** Total bytes read from all URIs combined. */
    val bytesRead: Long,
    /** True if a write-back attempt completed without throwing. */
    val writeSucceeded: Boolean,
    /** True if a Context.grantUriPermission re-grant call returned without throwing. */
    val grantSucceeded: Boolean,
    /** True if takePersistableUriPermission completed without throwing. */
    val persistableTaken: Boolean,
    /** Class name of any exception thrown, or null if the activity finished cleanly. */
    val exceptionClass: String?,
    /** Message from the exception, or null. */
    val exceptionMessage: String?,
)

/**
 * Cursor column names exposed by [ShareTelemetryProvider]. Pulled into a
 * separate object so both sides of the wire (receiver writes, test reads) can
 * import the same constants — accidental drift in column names would be a
 * silent test failure.
 */
object TelemetryColumns {
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

    val ALL = arrayOf(
        ID,
        RECORDED_AT,
        ACTIVITY_NAME,
        INTENT_ACTION,
        URIS,
        READ_SUCCEEDED,
        BYTES_READ,
        WRITE_SUCCEEDED,
        GRANT_SUCCEEDED,
        PERSISTABLE_TAKEN,
        EXCEPTION_CLASS,
        EXCEPTION_MESSAGE,
    )
}

/**
 * Stable authority + URIs for the telemetry provider. Importing these in
 * Riposte's integration test (rather than hardcoding strings) means we get
 * compile-time errors if the receiver ever moves namespaces.
 */
object TelemetryContract {
    const val AUTHORITY = "com.adsamcik.riposte.testreceiver.telemetry"
    const val PATH_OUTCOMES = "outcomes"
    const val PATH_LATEST = "latest"
    const val PATH_RESET = "reset"

    val OUTCOMES_URI: android.net.Uri = android.net.Uri.parse("content://$AUTHORITY/$PATH_OUTCOMES")
    val LATEST_URI: android.net.Uri = android.net.Uri.parse("content://$AUTHORITY/$PATH_LATEST")
    val RESET_URI: android.net.Uri = android.net.Uri.parse("content://$AUTHORITY/$PATH_RESET")
}
