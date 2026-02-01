package com.mememymood.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * Grid density configuration for adaptive layouts per VISUAL_DESIGN_SPEC.md Section 1.
 *
 * Provides breakpoints and column counts for responsive grid layouts
 * that adapt to screen width with user override capability.
 * Includes support for foldable devices.
 */
object GridDensity {
    // Breakpoints (screen width in dp)
    const val ULTRA_COMPACT_MAX = 319 // Outer fold screens
    const val COMPACT_MAX = 399
    const val MEDIUM_MAX = 479
    const val TABLET_MAX = 719 // Inner fold / tablet

    // Column counts by density
    const val COLUMNS_ULTRA_COMPACT = 2
    const val COLUMNS_COMPACT = 3
    const val COLUMNS_MEDIUM = 4
    const val COLUMNS_EXPANDED = 5
    const val COLUMNS_TABLET = 6

    // Grid spacing
    val GRID_PADDING = 8.dp
    val ITEM_SPACING = 8.dp
}

/**
 * User preference for grid density override.
 * AUTO uses adaptive columns based on screen width.
 */
enum class UserDensityPreference {
    /** Adaptive columns based on screen width */
    AUTO,

    /** Always 3 columns (larger thumbnails) */
    COMPACT,

    /** Always 4 columns */
    STANDARD,

    /** Always 5 columns (smaller thumbnails) */
    DENSE,
}

/**
 * Calculates the optimal number of grid columns based on screen width and user preference.
 *
 * @param userPreference User's density override setting
 * @return Number of columns to display (3, 4, or 5)
 */
@Composable
fun rememberGridColumns(
    userPreference: UserDensityPreference = UserDensityPreference.AUTO,
): Int {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    return remember(screenWidthDp, userPreference) {
        when (userPreference) {
            UserDensityPreference.AUTO -> when {
                screenWidthDp <= GridDensity.ULTRA_COMPACT_MAX -> GridDensity.COLUMNS_ULTRA_COMPACT
                screenWidthDp <= GridDensity.COMPACT_MAX -> GridDensity.COLUMNS_COMPACT
                screenWidthDp <= GridDensity.MEDIUM_MAX -> GridDensity.COLUMNS_MEDIUM
                screenWidthDp <= GridDensity.TABLET_MAX -> GridDensity.COLUMNS_EXPANDED
                else -> GridDensity.COLUMNS_TABLET
            }
            UserDensityPreference.COMPACT -> GridDensity.COLUMNS_COMPACT
            UserDensityPreference.STANDARD -> GridDensity.COLUMNS_MEDIUM
            UserDensityPreference.DENSE -> GridDensity.COLUMNS_EXPANDED
        }
    }
}
