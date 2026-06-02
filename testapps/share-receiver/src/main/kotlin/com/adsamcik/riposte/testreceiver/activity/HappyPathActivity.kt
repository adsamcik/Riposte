package com.adsamcik.riposte.testreceiver.activity

import android.net.Uri
import com.adsamcik.riposte.testreceiver.telemetry.ShareOutcome

/**
 * The control case.
 *
 * Just reads every URI fully and records how many bytes we got. If this fails
 * for a given share strategy, that strategy is fundamentally broken — there's
 * no exotic behavior here to blame.
 */
class HappyPathActivity : BaseReceiverActivity() {
    override fun handleShare(uris: List<Uri>): ShareOutcome {
        val totalBytes = uris.sumOf { readUriBytes(it) }
        return successOutcome(uris = uris, bytesRead = totalBytes)
    }
}
