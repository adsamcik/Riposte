package com.adsamcik.riposte.core.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Motion design tokens per VISUAL_DESIGN_SPEC.md Section 9.
 *
 * Provides consistent spring physics and animation durations
 * for micro-interactions throughout the app.
 */
object MotionTokens {
    /**
     * Standard pressed scale spring for interactive elements.
     * Used for tap feedback on buttons, chips, and grid items.
     */
    val PressedScale =
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        )

    /**
     * Quick bounce spring for snappy interactions.
     * Used for quick feedback on emoji chips and fast actions.
     */
    val QuickBounce =
        spring<Float>(
            dampingRatio = 0.6f,
            stiffness = 800f,
        )
}

/**
 * Animation duration constants in milliseconds.
 */
object AnimationDurations {
    const val CHIP_TAP = 150
    const val GRID_TAP = 100
    const val SECTION_EXPAND = 200
    const val EMPTY_STATE = 300
    const val HOLD_TO_SHARE_MIN = 800
    const val HOLD_TO_SHARE_MAX = 2000
}
