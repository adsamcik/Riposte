package com.adsamcik.riposte.testreceiver.activity

import android.content.Intent
import android.net.Uri
import com.adsamcik.riposte.testreceiver.service.DelayedReadService
import com.adsamcik.riposte.testreceiver.telemetry.ShareOutcome

/**
 * Acknowledges receipt synchronously but defers the actual URI read by ~5
 * seconds via [DelayedReadService] (a foreground service so Android 14+ lets
 * us survive the calling activity finishing).
 *
 * Models the real-world pattern of upload pipelines that queue media for
 * batch upload rather than reading immediately. Critical for testing that
 * Riposte's cleanup-on-next-share isn't so aggressive that it kills the URI
 * out from under a slow consumer.
 *
 * The outcome recorded here is just "scheduled" — the service will write its
 * own outcome record once the delayed read completes (or fails because we
 * cleaned up too early). Tests should query the LATEST outcome AFTER waiting
 * for the delay window to pass.
 */
class LateReadActivity : BaseReceiverActivity() {
    override fun handleShare(uris: List<Uri>): ShareOutcome {
        // Forward URIs to the delayed-read service. We do NOT read them here
        // because the whole point is to defer the read.
        val serviceIntent =
            Intent(this, DelayedReadService::class.java).apply {
                putParcelableArrayListExtra(
                    DelayedReadService.EXTRA_URIS,
                    ArrayList(uris),
                )
                // Re-forward our intent's grant flags so the service inherits
                // permission to read the URIs from the foreground context.
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                // ClipData carries the grants too — required for services in
                // some Android versions.
                clipData = intent.clipData
            }
        startForegroundService(serviceIntent)

        return successOutcome(
            uris = uris,
            bytesRead = 0,
            readSucceeded = false,
        )
    }
}
