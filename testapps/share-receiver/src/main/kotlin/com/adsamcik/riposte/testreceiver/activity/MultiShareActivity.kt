package com.adsamcik.riposte.testreceiver.activity

import android.net.Uri
import com.adsamcik.riposte.testreceiver.telemetry.ShareOutcome

/**
 * Well-behaved ACTION_SEND_MULTIPLE handler — reads every URI in the list,
 * sums total bytes, records outcome.
 *
 * The cooperative counterpart to [ArrayListOnlyActivity]. Validates that our
 * multi-share path produces a properly-structured intent that any reasonable
 * receiver can consume.
 */
class MultiShareActivity : BaseReceiverActivity() {
    override fun handleShare(uris: List<Uri>): ShareOutcome {
        val totalBytes = uris.sumOf { readUriBytes(it) }
        return successOutcome(uris = uris, bytesRead = totalBytes)
    }
}
