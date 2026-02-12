package com.adsamcik.riposte.core.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Motion design tokens for Riposte.
 *
 * Original tokens preserved for backward compatibility.
 * New [RiposteMotionScheme] adds M3 Expressive springs.
 */
object MotionTokens {
    /** Standard pressed scale spring for interactive elements. */
    val PressedScale =
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        )

    /** Quick bounce spring for snappy interactions. */
    val QuickBounce =
        spring<Float>(
            dampingRatio = 0.6f,
            stiffness = 800f,
        )
}

/**
 * M3 Expressive motion scheme for Riposte.
 *
 * Three-tier spring system:
 * - [FastSpatial]: Immediate feedback (cards, chips, taps)
 * - [DefaultEffects]: Transitions, navigation, content changes
 * - [SlowSpatial]: Sheets, complex layouts, celebrations
 */
object RiposteMotionScheme {
    /** High stiffness + bouncy for meme card taps and emoji chips. */
    val FastSpatial =
        spring<Float>(
            dampingRatio = 0.65f,
            stiffness = 1400f,
        )

    /** Medium bounce for screen transitions and navigation. */
    val DefaultEffects =
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        )

    /** Gentle spring for sheet expansion and complex layouts. */
    val SlowSpatial =
        spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        )

    /** Extra bouncy for favorite hearts and emoji reactions. */
    val EmojiReaction =
        spring<Float>(
            dampingRatio = 0.4f,
            stiffness = 1000f,
        )

    /** Controlled spring for search bar and toolbar expansion. */
    val Expansion =
        spring<Float>(
            dampingRatio = 0.8f,
            stiffness = 600f,
        )
}

/** Animation duration constants in milliseconds. */
object AnimationDurations {
    const val CHIP_TAP = 100
    const val GRID_TAP = 80
    const val SECTION_EXPAND = 200
    const val EMPTY_STATE = 300
    const val HOLD_TO_SHARE_MIN = 800
    const val HOLD_TO_SHARE_MAX = 2000

    // M3 Expressive durations
    const val SCREEN_TRANSITION = 300
    const val CELEBRATION = 400
    const val STAGGER_ITEM = 30
    const val FILTER_STAGGER_ITEM = 15
    const val COLOR_CONTEXT_SHIFT = 300
}
