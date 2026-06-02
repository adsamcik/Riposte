package com.adsamcik.riposte.testreceiver.activity

import android.content.Intent
import android.net.Uri
import com.adsamcik.riposte.testreceiver.service.RemoteWorkerService
import com.adsamcik.riposte.testreceiver.telemetry.ShareOutcome

/**
 * Forwards the received URI to a service running in a separate process
 * (`:remote`). Validates that URI grants survive cross-process IPC — important
 * because real apps (especially React-Native-based ones like Discord) often
 * delegate URI handling to upload workers in different processes.
 *
 * The activity records its own "scheduled" outcome immediately; the remote
 * service records the actual read result via cross-process ContentResolver
 * insert (since it can't share static state with the default-process
 * provider). Tests should look at the LATEST outcome after waiting briefly.
 */
class MultiProcessActivity : BaseReceiverActivity() {
    override fun handleShare(uris: List<Uri>): ShareOutcome {
        val serviceIntent =
            Intent(this, RemoteWorkerService::class.java).apply {
                putParcelableArrayListExtra(
                    RemoteWorkerService.EXTRA_URIS,
                    ArrayList(uris),
                )
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
                clipData = intent.clipData
            }
        startService(serviceIntent)

        return successOutcome(
            uris = uris,
            bytesRead = 0,
            readSucceeded = false,
        )
    }
}
