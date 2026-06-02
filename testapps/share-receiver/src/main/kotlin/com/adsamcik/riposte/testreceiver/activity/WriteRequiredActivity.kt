package com.adsamcik.riposte.testreceiver.activity

import android.net.Uri
import com.adsamcik.riposte.testreceiver.telemetry.ShareOutcome

/**
 * Reads the URI, then attempts to write a single marker byte back to it.
 *
 * Validates that our FLAG_GRANT_WRITE_URI_PERMISSION actually grants what it
 * claims. With READ-only grants this throws SecurityException on the
 * openOutputStream call. With MediaStore URIs and READ_MEDIA_IMAGES, the
 * write succeeds even without the intent flag because the system grants from
 * the broader permission.
 *
 * We don't care about preserving the original content — the share file is
 * transient anyway. The receiver writes a harmless single zero byte.
 */
class WriteRequiredActivity : BaseReceiverActivity() {
    override fun handleShare(uris: List<Uri>): ShareOutcome {
        val totalBytes = uris.sumOf { readUriBytes(it) }

        // Attempt to overwrite each URI with a single zero byte
        uris.forEach { uri ->
            contentResolver.openOutputStream(uri, "wt").use { out ->
                requireNotNull(out) { "openOutputStream returned null for $uri" }
                out.write(0)
            }
        }

        return successOutcome(
            uris = uris,
            bytesRead = totalBytes,
            writeSucceeded = true,
        )
    }
}
