package com.adsamcik.riposte.testreceiver.activity

import android.content.Intent
import android.net.Uri
import com.adsamcik.riposte.testreceiver.telemetry.ShareOutcome

/**
 * Discord's secondary bug: blindly calls
 * [Intent.getParcelableArrayListExtra] for `EXTRA_STREAM` regardless of
 * whether the intent is `ACTION_SEND` (single URI) or
 * `ACTION_SEND_MULTIPLE` (URI list).
 *
 * For single-share intents this returns null with a BadTypeParcelableException
 * warning, which silently breaks any code that expects a non-null list. We
 * model the strict version: throw if the list extraction returns null on a
 * SEND intent.
 *
 * Riposte's regression test for this asserts our multi-share path uses a
 * proper ArrayList<Uri> in EXTRA_STREAM (not a HashSet or other Collection).
 */
class ArrayListOnlyActivity : BaseReceiverActivity() {
    override fun handleShare(uris: List<Uri>): ShareOutcome {
        // Force the same call Discord makes — ignore the parsed uris from the
        // base class and re-extract via the array-only API.
        val arrayList =
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                ?: error("EXTRA_STREAM not an ArrayList<Uri> on ${intent.action}")

        val totalBytes = arrayList.sumOf { readUriBytes(it) }
        return successOutcome(uris = arrayList.toList(), bytesRead = totalBytes)
    }
}
