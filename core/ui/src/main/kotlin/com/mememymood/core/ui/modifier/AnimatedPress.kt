package com.mememymood.core.ui.modifier

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mememymood.core.ui.theme.MotionTokens
import com.mememymood.core.ui.util.LocalReducedMotion

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
 * @return Modifier with press scale animation applied.
 */
fun Modifier.animatedPressScale(): Modifier = composed {
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

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
