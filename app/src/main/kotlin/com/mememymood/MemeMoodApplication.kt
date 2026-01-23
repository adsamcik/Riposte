package com.mememymood

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Main Application class for Meme My Mood.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 * Implements [Configuration.Provider] to provide custom WorkManager configuration
 * with Hilt-enabled workers.
 */
@HiltAndroidApp
class MemeMoodApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
