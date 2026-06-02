package com.adsamcik.riposte.testreceiver.activity

import android.content.Context
import android.net.Uri
import com.adsamcik.riposte.testreceiver.telemetry.ShareOutcome

/**
 * Simulates intermittent receivers — succeeds on the first share, fails on
 * the second. State persists across activity instances via SharedPreferences
 * so the failure is deterministic within a single test process.
 *
 * Useful for testing that our adaptive detection (future feature) actually
 * notices and routes around a flaky receiver after N failures.
 *
 * Reset by deleting the receiver app data, or by querying
 * `content://...telemetry/reset` from the test setUp.
 */
class CrashOnSecondShareActivity : BaseReceiverActivity() {

    override fun handleShare(uris: List<Uri>): ShareOutcome {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_COUNT, 0) + 1
        prefs.edit().putInt(KEY_COUNT, count).apply()

        if (count >= 2) {
            error("Simulated intermittent failure on share #$count")
        }

        val totalBytes = uris.sumOf { readUriBytes(it) }
        return successOutcome(uris = uris, bytesRead = totalBytes)
    }

    private companion object {
        const val PREFS = "crash_on_second_share"
        const val KEY_COUNT = "share_count"
    }
}
