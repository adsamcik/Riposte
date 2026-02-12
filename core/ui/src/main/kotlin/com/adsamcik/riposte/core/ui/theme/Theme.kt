package com.adsamcik.riposte.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.adsamcik.riposte.core.ui.util.LocalReducedMotion
import com.adsamcik.riposte.core.ui.util.rememberReducedMotion

private val DarkColorScheme =
    darkColorScheme(
        primary = MoodPrimaryDark,
        onPrimary = OnMoodPrimaryDark,
        secondary = MoodSecondaryDark,
        onSecondary = OnMoodSecondaryDark,
        tertiary = MoodTertiaryDark,
        surface = SurfaceDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        error = ErrorDark,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = MoodPrimary,
        onPrimary = OnMoodPrimary,
        secondary = MoodSecondary,
        onSecondary = OnMoodSecondary,
        tertiary = MoodTertiary,
        surface = SurfaceLight,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        error = Error,
    )

@Composable
fun RiposteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on all supported devices (minSdk 31+)
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor -> {
                val context = LocalContext.current
                val dynamic =
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                // Hybrid: keep brand primary/secondary/tertiary, adapt surfaces
                dynamic.copy(
                    primary = if (darkTheme) MoodPrimaryDark else MoodPrimary,
                    onPrimary = if (darkTheme) OnMoodPrimaryDark else OnMoodPrimary,
                    secondary = if (darkTheme) MoodSecondaryDark else MoodSecondary,
                    tertiary = if (darkTheme) MoodTertiaryDark else MoodTertiary,
                )
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use transparent status bar for edge-to-edge display
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val reducedMotion = rememberReducedMotion()

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}
