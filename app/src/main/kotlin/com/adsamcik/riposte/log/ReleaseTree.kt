package com.adsamcik.riposte.log

import android.util.Log
import timber.log.Timber

/**
 * Timber tree for release builds that only logs warnings and errors to Logcat.
 * This ensures critical ML pipeline failures, search errors, and initialization
 * issues are visible even in Play Store builds.
 */
class ReleaseTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean =
        priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Log.println(priority, tag, message)
        if (t != null) {
            Log.println(priority, tag, Log.getStackTraceString(t))
        }
    }
}
