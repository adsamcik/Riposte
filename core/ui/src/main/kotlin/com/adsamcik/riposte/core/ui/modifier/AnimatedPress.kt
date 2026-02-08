package com.adsamcik.riposte.core.ui.modifier

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.adsamcik.riposte.core.ui.theme.MotionTokens
import com.adsamcik.riposte.core.ui.util.LocalReducedMotion

/**
 * Applies an animated scale effect when the element is pressed.
 *
 * Per VISUAL_DESIGN_SPEC.md Section 9, provides tactile feedback
 * for interactive elements using spring physics. The element scales
 * down to 0.96f when pressed.
 *
 * Features:
 * - Respects [LocalReducedMotion] preference - skips animation when true
 * - Enforces minimum 48dp touch target per Material 3 guidelines
 * - Uses [MotionTokens.PressedScale] spring for natural feel
 *
 * @param interactionSource The interaction source to observe for press state.
 *                          If null, creates an internal source (won't detect presses
 *                          unless used with a clickable that shares the same source).
 * @return Modifier with press scale animation applied.
 */
fun Modifier.animatedPressScale(
    interactionSource: InteractionSource? = null,
): Modifier = composed {
    val reducedMotion = LocalReducedMotion.current
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.96f else 1f,
        animationSpec = MotionTokens.PressedScale,
        label = "pressScale",
    )

    this
        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}
