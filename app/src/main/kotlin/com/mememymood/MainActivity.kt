package com.mememymood

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mememymood.core.ui.theme.MemeMoodTheme
import com.mememymood.navigation.MemeMoodNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity for Meme My Mood.
 * Uses edge-to-edge display and hosts the main navigation graph.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
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
