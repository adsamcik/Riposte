package com.adsamcik.riposte

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.model.AppPreferences
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import com.adsamcik.riposte.navigation.RiposteNavHost
import com.adsamcik.riposte.review.InAppReviewManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main Activity for Riposte.
 * Uses AppCompatActivity for per-app language support (Android 13+).
 * Implements splash screen and edge-to-edge display.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var inAppReviewManager: InAppReviewManager

    @Inject
    lateinit var preferencesDataStore: PreferencesDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)

        inAppReviewManager.trackAppOpen()

        // Enable edge-to-edge display
        enableEdgeToEdge()

        setContent {
            val prefs by preferencesDataStore.appPreferences.collectAsState(
                initial = AppPreferences(),
            )
            val darkTheme = when (prefs.darkMode) {
                DarkMode.DARK -> true
                DarkMode.LIGHT -> false
                DarkMode.SYSTEM -> isSystemInDarkTheme()
            }
            RiposteTheme(
                darkTheme = darkTheme,
                dynamicColor = prefs.dynamicColors,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RiposteNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        inAppReviewManager.requestReviewIfReady(this)
    }
}
