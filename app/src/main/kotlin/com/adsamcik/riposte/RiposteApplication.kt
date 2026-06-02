package com.adsamcik.riposte

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.adsamcik.riposte.core.common.crash.CrashReportWriter
import com.adsamcik.riposte.core.common.lifecycle.AppLifecycleTracker
import com.adsamcik.riposte.core.common.share.ShareRepository
import com.adsamcik.riposte.core.ml.EmbeddingManager
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.adsamcik.riposte.log.ReleaseTree
import timber.log.Timber
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
    lateinit var appLifecycleTracker: AppLifecycleTracker

    @Inject
    lateinit var embeddingManager: Lazy<EmbeddingManager>

    @Inject
    lateinit var shareRepository: Lazy<ShareRepository>

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
        installCrashHandler()
        appLifecycleTracker.init()
        // Launch heavy dependency construction off the main thread
        applicationScope.launch {
            embeddingManager.get().warmUpAndResumeIndexing(applicationScope)
        }
        // Clean up any transient MediaStore share entries left behind from a previous
        // process. Anything still present is by definition stale — the receiving app
        // has already consumed it (or we crashed before cleanup). Fire-and-forget.
        applicationScope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                val removed = shareRepository.get().cleanupStaleShares()
                if (removed > 0) {
                    Timber.d("Cleaned up %d stale share entries on app start", removed)
                }
            } catch (e: Exception) {
                Timber.w(e, "Stale share cleanup failed on app start")
            }
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
