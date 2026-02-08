package com.adsamcik.riposte.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Thumbnail sizing constants per VISUAL_DESIGN_SPEC.md Section 2.
 *
 * Provides consistent dimensions for thumbnails across different contexts
 * while ensuring accessibility touch targets are met.
 */
object ThumbnailSizes {
    // Quick Access horizontal row
    val QUICK_ACCESS_HEIGHT = 72.dp
    val QUICK_ACCESS_MAX_WIDTH = 96.dp

    // Main grid cells
    val MIN_THUMBNAIL_SIZE = 80.dp
    val MAX_THUMBNAIL_HEIGHT = 160.dp

    // Touch target (Material 3 accessibility minimum)
    val MIN_TOUCH_TARGET = 48.dp

    // Corner radius
    val THUMBNAIL_CORNER_RADIUS = 12.dp
}
