package com.mememymood

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Main Application class for Meme My Mood.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class MemeMoodApplication : Application()
