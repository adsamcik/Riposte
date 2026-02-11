package com.adsamcik.riposte.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.adsamcik.riposte.core.ui.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Duration in milliseconds for the hold-to-share gesture.
 * The progress indicator fills over this duration.
 */
const val HOLD_TO_SHARE_DURATION_MS = 600L

/**
 * Delay before starting the hold progress animation.
 * Allows distinguishing between tap and hold gestures.
 */
const val HOLD_START_DELAY_MS = 150L

/** Test tag for the hold-to-share progress overlay. */
const val HOLD_TO_SHARE_PROGRESS_TEST_TAG = "hold_to_share_progress"

/**
 * Container composable that provides hold-to-share gesture support.
 *
 * Wraps [content] with a gesture detector that distinguishes between
 * tap and hold gestures:
 * - **Tap** (release before [HOLD_START_DELAY_MS]): invokes [onTap]
 * - **Hold** (hold for [HOLD_START_DELAY_MS] + [HOLD_TO_SHARE_DURATION_MS]):
 *   shows a [HoldToShareProgress] overlay and invokes [onHoldComplete] when done
 * - **Cancel** (release during hold animation): cancels without callback
 *
 * If [onLongPress] is provided, it is invoked when the hold gesture is first
 * detected (after [HOLD_START_DELAY_MS]) — useful for showing a context menu
 * instead of (or alongside) the progress animation.
 *
 * @param onTap Callback for quick tap gesture.
 * @param onHoldComplete Callback when hold gesture completes.
 * @param onLongPress Optional callback when long-press is first detected.
 * @param modifier Modifier for the outer container.
 * @param content Content to display inside the container.
 */
@Composable
fun HoldToShareContainer(
    onTap: () -> Unit,
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()

    val holdProgress = remember { Animatable(0f) }
    var isHolding by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<Job?>(null) }

    val shareActionLabel = stringResource(R.string.ui_hold_to_share_action)

    Box(
        modifier =
            modifier
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(shareActionLabel) {
                                onHoldComplete()
                                true
                            },
                        )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()

                        // Start hold detection
                        holdJob =
                            scope.launch {
                                delay(HOLD_START_DELAY_MS)
                                onLongPress?.invoke()
                                isHolding = true
                                holdProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec =
                                        tween(
                                            durationMillis = HOLD_TO_SHARE_DURATION_MS.toInt(),
                                            easing = LinearEasing,
                                        ),
                                )
                            }

                        val up = waitForUpOrCancellation()

                        // Cancel or complete
                        holdJob?.cancel()
                        holdJob = null

                        if (up != null) {
                            up.consume()
                            if (holdProgress.value >= 1f) {
                                // Hold completed - trigger share
                                onHoldComplete()
                            } else if (!isHolding) {
                                // Released before hold started - treat as tap
                                onTap()
                            }
                            // If holding but not complete, just cancel (do nothing)
                        }

                        // Reset state
                        isHolding = false
                        scope.launch {
                            holdProgress.snapTo(0f)
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        content()

        // Hold-to-share progress overlay
        AnimatedVisibility(
            visible = isHolding,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.testTag(HOLD_TO_SHARE_PROGRESS_TEST_TAG),
        ) {
            HoldToShareProgress(
                progress = holdProgress.value,
                onComplete = { /* Handled in gesture detection */ },
            )
        }
    }
}
