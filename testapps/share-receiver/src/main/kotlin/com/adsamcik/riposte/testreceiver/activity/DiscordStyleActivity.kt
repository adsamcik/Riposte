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
 * Critically, the forward target must be a DIFFERENT package from us (the
 * receiver) AND from the URI's provider — Android short-circuits same-app
 * grants ("you already have it") and provider-owner grants ("you ARE the
 * owner"), so forwarding to ourselves or to Riposte would silently succeed
 * and hide the bug. We forward to `com.android.shell` because it's always
 * present on every Android device and has no special relationship to either
 * Riposte's FileProvider or to MediaStore — exactly the cross-app forwarding
 * scenario Discord's RN bridge exercises.
 *
 * For this activity, "success" means the grant call returned without throwing.
 * It does NOT actually do anything with the grant — we don't need a real
 * worker, just to verify the OS accepts the re-grant.
 */
class DiscordStyleActivity : BaseReceiverActivity() {
    override fun handleShare(uris: List<Uri>): ShareOutcome {
        val totalBytes = uris.sumOf { readUriBytes(it) }

        // The crash-inducing call — forward the grant to a third-party
        // package exactly like Discord forwards to its RN upload bridge
        // / worker process. Using com.android.shell because:
        //   - always installed (system app)
        //   - distinct from us and from any Riposte build variant
        //   - the system enforces full grant validation rather than
        //     the "same app" / "same owner" fast paths
        uris.forEach { uri ->
            grantUriPermission(
                FORWARD_TARGET_PACKAGE,
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
        const val FORWARD_TARGET_PACKAGE = "com.android.shell"
    }
}
