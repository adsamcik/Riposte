package com.adsamcik.riposte.core.common.crash

import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Installs a custom [Thread.UncaughtExceptionHandler] that writes crash
 * reports to files before delegating to the previous (default) handler.
 *
 * This class is intentionally free of any DI framework annotations so it
 * can be created early in [android.app.Application.onCreate].
 */
class CrashReportWriter(
    private val crashDir: File,
    private val appVersion: String,
) : Thread.UncaughtExceptionHandler {
    private val previousHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    /** Install this handler as the default uncaught-exception handler. */
    fun install() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        try {
            writeCrashReport(thread, throwable)
        } catch (_: Exception) {
            // Best-effort — never let logging prevent the default handler from running.
        }
        previousHandler?.uncaughtException(thread, throwable)
    }

    private fun writeCrashReport(
        thread: Thread,
        throwable: Throwable,
    ) {
        crashDir.mkdirs()
        val timestamp = System.currentTimeMillis()
        val file = File(crashDir, "crash_$timestamp.txt")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()

        file.writeText(
            buildString {
                appendLine("=== Riposte Crash Report ===")
                appendLine("Time:       ${dateFormat.format(Date(timestamp))}")
                appendLine("App:        $appVersion")
                appendLine("Device:     ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android:    ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Thread:     ${thread.name}")
                appendLine()
                appendLine("--- Stack Trace ---")
                append(stackTrace)
            },
        )
    }

    companion object {
        /** Subdirectory name used under the app's files directory. */
        const val CRASH_DIR_NAME = "crash_reports"
    }
}
