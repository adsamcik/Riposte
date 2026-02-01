package com.mememymood.core.ui.util

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Accessibility utilities per VISUAL_DESIGN_SPEC.md Section 10.
 */

/**
 * Composition local to provide reduced motion preference throughout the tree.
 * When true, animations should be skipped or minimized.
 */
val LocalReducedMotion = compositionLocalOf { false }

/**
 * Material 3 minimum touch target size for accessibility.
 */
val MIN_TOUCH_TARGET = 48.dp

/**
 * Remembers whether the user has enabled reduced motion in system settings.
 *
 * Checks the ANIMATOR_DURATION_SCALE system setting. When set to 0,
 * the user has disabled animations for accessibility.
 *
 * @return true if animations should be skipped/reduced
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        } catch (_: Settings.SettingNotFoundException) {
            false
        }
    }
}
