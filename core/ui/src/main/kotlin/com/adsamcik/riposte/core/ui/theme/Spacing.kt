package com.adsamcik.riposte.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing design tokens for consistent layout throughout the app.
 *
 * Follows a 4dp base grid scale:
 * - [xxs] (2dp): Tight internal spacing (icon-to-text gaps)
 * - [xs] (4dp): Compact spacing (chip padding, small gaps)
 * - [sm] (8dp): Standard element spacing
 * - [md] (12dp): Section padding, card internal spacing
 * - [lg] (16dp): Screen-level padding, major section gaps
 * - [xl] (24dp): Large section separation
 * - [xxl] (32dp): Major layout separation
 */
object Spacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}
