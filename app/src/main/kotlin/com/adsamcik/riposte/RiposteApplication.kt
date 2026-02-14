package com.adsamcik.riposte

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.adsamcik.riposte.core.common.crash.CrashReportWriter
import com.adsamcik.riposte.core.common.lifecycle.AppLifecycleTracker
import com.adsamcik.riposte.core.ml.EmbeddingManager
import com.adsamcik.riposte.sharing.SharingShortcutUpdater
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Main Application class for Riposte.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 * Implements [Configuration.Provider] to provide custom WorkManager configuration
 * with Hilt-enabled workers.
 */
@HiltAndroidApp
class RiposteApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var sharingShortcutUpdater: Lazy<SharingShortcutUpdater>

    @Inject
    lateinit var appLifecycleTracker: AppLifecycleTracker

    @Inject
    lateinit var embeddingManager: Lazy<EmbeddingManager>

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        appLifecycleTracker.init()
        // Launch heavy dependency construction off the main thread
        applicationScope.launch {
            sharingShortcutUpdater.get().start(applicationScope)
            embeddingManager.get().warmUpAndResumeIndexing(applicationScope)
        }
    }

    private fun installCrashHandler() {
        val crashDir = File(filesDir, CrashReportWriter.CRASH_DIR_NAME)
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        CrashReportWriter(crashDir, versionName).install()
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
