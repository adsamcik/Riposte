package com.adsamcik.riposte.testreceiver.activity

import android.content.Intent
import android.net.Uri
import com.adsamcik.riposte.testreceiver.telemetry.ShareOutcome

/**
 * Reproduces Discord's React Native ShareActivity behavior: receives the
 * share, then immediately calls [android.content.Context.grantUriPermission]
 * to forward the URI to a hypothetical upload worker.
 *
 * That API requires the caller to either own the URI's provider or hold a
 * persistable grant. Neither holds for transient FileProvider grants, so the
 * call throws SecurityException — exactly the crash the user originally
 * reported. MediaStore URIs work because READ_MEDIA_IMAGES satisfies the OS
 * check independently.
 *
 * For this activity, "success" means the grant call returned without throwing.
 * It does NOT actually do anything with the grant — we don't need a real
 * worker, just to verify the OS accepts the re-grant.
 */
class DiscordStyleActivity : BaseReceiverActivity() {
    override fun handleShare(uris: List<Uri>): ShareOutcome {
        val totalBytes = uris.sumOf { readUriBytes(it) }

        // The crash-inducing call — forward the grant to a fake target package
        // exactly like Discord forwards to its RN upload bridge.
        uris.forEach { uri ->
            grantUriPermission(
                FAKE_WORKER_PACKAGE,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }

        return successOutcome(
            uris = uris,
            bytesRead = totalBytes,
            grantSucceeded = true,
        )
    }

    private companion object {
        // Any installed package would do; using ourselves means the test
        // doesn't depend on a third app being present. The grant call still
        // exercises the same OS code path.
        const val FAKE_WORKER_PACKAGE = "com.adsamcik.riposte.testreceiver"
    }
}
