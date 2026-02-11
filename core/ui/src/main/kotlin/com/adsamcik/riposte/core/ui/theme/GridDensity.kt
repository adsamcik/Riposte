package com.adsamcik.riposte.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.adsamcik.riposte.core.model.UserDensityPreference

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
 * Calculates the optimal number of grid columns based on screen width and user preference.
 *
 * @param userPreference User's density override setting
 * @return Number of columns to display (3, 4, or 5)
 */
@Composable
fun rememberGridColumns(userPreference: UserDensityPreference = UserDensityPreference.AUTO): Int {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    return remember(screenWidthDp, userPreference) {
        when (userPreference) {
            UserDensityPreference.AUTO ->
                when {
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

/**
 * Quick Access item count breakpoints (screen width in dp).
 */
object QuickAccessDensity {
    const val PHONE_LANDSCAPE_MIN = 480
    const val MEDIUM_TABLET_MIN = 600
    const val LARGE_TABLET_MIN = 840

    const val ITEMS_PHONE_PORTRAIT = 5
    const val ITEMS_PHONE_LANDSCAPE = 6
    const val ITEMS_MEDIUM_TABLET = 8
    const val ITEMS_LARGE_TABLET = 10
}

/**
 * Calculates the optimal number of Quick Access items based on screen width.
 *
 * Breakpoints per design spec:
 * - Phone portrait: 5 items
 * - Phone landscape / small tablet (>= 480dp): 6 items
 * - Medium tablet (>= 600dp): 8 items
 * - Large tablet (>= 840dp): 10 items
 *
 * @return Number of Quick Access items to display
 */
@Composable
fun rememberQuickAccessCount(): Int {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    return remember(screenWidthDp) {
        when {
            screenWidthDp >= QuickAccessDensity.LARGE_TABLET_MIN -> QuickAccessDensity.ITEMS_LARGE_TABLET
            screenWidthDp >= QuickAccessDensity.MEDIUM_TABLET_MIN -> QuickAccessDensity.ITEMS_MEDIUM_TABLET
            screenWidthDp >= QuickAccessDensity.PHONE_LANDSCAPE_MIN -> QuickAccessDensity.ITEMS_PHONE_LANDSCAPE
            else -> QuickAccessDensity.ITEMS_PHONE_PORTRAIT
        }
    }
}
