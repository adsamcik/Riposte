package com.mememymood

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mememymood.core.ui.theme.MemeMoodTheme
import com.mememymood.navigation.MemeMoodNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity for Meme My Mood.
 * Uses AppCompatActivity for per-app language support (Android 13+).
 * Implements splash screen and edge-to-edge display.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        setContent {
            MemeMoodTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MemeMoodNavHost()
                }
            }
        }
    }
}
