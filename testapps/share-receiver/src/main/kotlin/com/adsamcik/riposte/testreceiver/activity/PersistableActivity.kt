package com.adsamcik.riposte.testreceiver.activity

import android.content.Intent
import android.net.Uri
import com.adsamcik.riposte.testreceiver.telemetry.ShareOutcome

/**
 * Calls [android.content.ContentResolver.takePersistableUriPermission] on the
 * received URI to validate our FLAG_GRANT_PERSISTABLE_URI_PERMISSION.
 *
 * The takePersistableUriPermission call only succeeds when:
 *   1. The intent flagged the grant as persistable (which we do), AND
 *   2. The URI's provider supports persistable grants (MediaStore does;
 *      FileProvider does too but with some caveats).
 *
 * After the test, the persistable grant is released via
 * releasePersistableUriPermission so we don't leak grants across test runs.
 */
class PersistableActivity : BaseReceiverActivity() {
    override fun handleShare(uris: List<Uri>): ShareOutcome {
        val totalBytes = uris.sumOf { readUriBytes(it) }

        uris.forEach { uri ->
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        // Clean up immediately so grants don't accumulate across runs
        uris.forEach { uri ->
            @Suppress("TooGenericExceptionCaught")
            try {
                contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Throwable) {
                // Best-effort cleanup; not failing the test on release errors
            }
        }

        return successOutcome(
            uris = uris,
            bytesRead = totalBytes,
            persistableTaken = true,
        )
    }
}
