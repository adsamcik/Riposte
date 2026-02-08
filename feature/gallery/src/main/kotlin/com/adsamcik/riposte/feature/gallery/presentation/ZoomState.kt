package com.adsamcik.riposte.feature.gallery.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Holds zoom, pan, and control-visibility state for the meme detail image viewer.
 *
 * Because this is a [Stable] class with snapshot-backed fields, a `pointerInput`
 * lambda that captures a [ZoomState] instance always reads the latest values
 * without needing the lambda to be relaunched.
 */
@Stable
class ZoomState {

    var scale by mutableFloatStateOf(1f)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    var showControls by mutableStateOf(true)
        private set

    /** Whether the image is currently zoomed beyond its default scale. */
    val isZoomed: Boolean get() = scale > 1f

    fun toggleControls() {
        showControls = !showControls
    }

    /**
     * Applies a zoom [factor] to the current scale, clamped to [[MIN_SCALE], [MAX_SCALE]].
     */
    fun zoomBy(factor: Float) {
        scale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /**
     * Sets the scale directly, clamped to [[MIN_SCALE], [MAX_SCALE]].
     */
    fun snapToScale(newScale: Float) {
        scale = newScale.coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /**
     * Pans by [delta], clamping the offset so the image edges don't leave the
     * viewport.
     *
     * @param delta pixel-space drag delta
     * @param viewportWidth width of the viewport in pixels
     * @param viewportHeight height of the viewport in pixels
     */
    fun panBy(delta: Offset, viewportWidth: Float, viewportHeight: Float) {
        val raw = offset + delta
        offset = clampOffset(raw, scale, viewportWidth, viewportHeight)
    }

    /**
     * Toggles between zoomed-in (2×) and default (1×) scale, resetting offset.
     */
    fun doubleTapToggle() {
        if (isZoomed) {
            scale = 1f
        } else {
            scale = 2f
        }
        offset = Offset.Zero
    }

    /**
     * Resets to default (1×, no offset, controls visible).
     */
    fun reset() {
        scale = 1f
        offset = Offset.Zero
        showControls = true
    }

    companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 4f

        /**
         * Clamps [offset] so the image edges stay within the viewport.
         *
         * The maximum translation in each axis is `(scale − 1) × dimension / 2`,
         * which is the distance from the image center to the edge of the visible
         * portion when zoomed.
         */
        fun clampOffset(
            offset: Offset,
            scale: Float,
            viewportWidth: Float,
            viewportHeight: Float,
        ): Offset {
            val maxX = ((scale - 1f) * viewportWidth / 2f).coerceAtLeast(0f)
            val maxY = ((scale - 1f) * viewportHeight / 2f).coerceAtLeast(0f)
            return Offset(
                x = offset.x.coerceIn(-maxX, maxX),
                y = offset.y.coerceIn(-maxY, maxY),
            )
        }
    }
}

/**
 * Remembers a [ZoomState] instance across recompositions.
 */
@Composable
fun rememberZoomState(): ZoomState = remember { ZoomState() }
