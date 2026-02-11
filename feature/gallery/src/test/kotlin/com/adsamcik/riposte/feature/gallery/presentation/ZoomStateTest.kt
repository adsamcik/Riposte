package com.adsamcik.riposte.feature.gallery.presentation

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class ZoomStateTest {
    private lateinit var zoomState: ZoomState

    @Before
    fun setup() {
        zoomState = ZoomState()
    }

    // --- Scale bounds ---

    @Test
    fun `initial scale is 1f`() {
        assertThat(zoomState.scale).isEqualTo(1f)
    }

    @Test
    fun `zoomBy clamps to max scale`() {
        zoomState.zoomBy(10f) // 1f * 10 = 10f, clamped to 4f
        assertThat(zoomState.scale).isEqualTo(ZoomState.MAX_SCALE)
    }

    @Test
    fun `zoomBy clamps to min scale`() {
        zoomState.zoomBy(0.1f) // 1f * 0.1 = 0.1f, clamped to 0.5f
        assertThat(zoomState.scale).isEqualTo(ZoomState.MIN_SCALE)
    }

    @Test
    fun `zoomBy applies factor within bounds`() {
        zoomState.zoomBy(2f)
        assertThat(zoomState.scale).isEqualTo(2f)
    }

    @Test
    fun `setScale clamps to max`() {
        zoomState.snapToScale(5f)
        assertThat(zoomState.scale).isEqualTo(ZoomState.MAX_SCALE)
    }

    @Test
    fun `setScale clamps to min`() {
        zoomState.snapToScale(0.1f)
        assertThat(zoomState.scale).isEqualTo(ZoomState.MIN_SCALE)
    }

    @Test
    fun `setScale accepts value within bounds`() {
        zoomState.snapToScale(3f)
        assertThat(zoomState.scale).isEqualTo(3f)
    }

    // --- isZoomed ---

    @Test
    fun `isZoomed false at default scale`() {
        assertThat(zoomState.isZoomed).isFalse()
    }

    @Test
    fun `isZoomed true when scale greater than 1`() {
        zoomState.zoomBy(1.5f)
        assertThat(zoomState.isZoomed).isTrue()
    }

    @Test
    fun `isZoomed false when scale equals 1`() {
        zoomState.snapToScale(1f)
        assertThat(zoomState.isZoomed).isFalse()
    }

    // --- Offset clamping ---

    @Test
    fun `panBy clamps offset at default scale`() {
        // At scale 1f, max offset is (1-1)*w/2 = 0, so any pan is clamped to zero
        zoomState.panBy(Offset(100f, 200f), viewportWidth = 1000f, viewportHeight = 2000f)
        assertThat(zoomState.offset).isEqualTo(Offset.Zero)
    }

    @Test
    fun `panBy allows offset within bounds when zoomed`() {
        zoomState.snapToScale(2f)
        // max offset = (2-1)*1000/2 = 500 in x, (2-1)*2000/2 = 1000 in y
        zoomState.panBy(Offset(100f, 200f), viewportWidth = 1000f, viewportHeight = 2000f)
        assertThat(zoomState.offset).isEqualTo(Offset(100f, 200f))
    }

    @Test
    fun `panBy clamps offset to max bounds when zoomed`() {
        zoomState.snapToScale(2f)
        // max offset x = 500, y = 1000
        zoomState.panBy(Offset(600f, 1200f), viewportWidth = 1000f, viewportHeight = 2000f)
        assertThat(zoomState.offset.x).isEqualTo(500f)
        assertThat(zoomState.offset.y).isEqualTo(1000f)
    }

    @Test
    fun `panBy clamps negative offset`() {
        zoomState.snapToScale(2f)
        zoomState.panBy(Offset(-600f, -1200f), viewportWidth = 1000f, viewportHeight = 2000f)
        assertThat(zoomState.offset.x).isEqualTo(-500f)
        assertThat(zoomState.offset.y).isEqualTo(-1000f)
    }

    @Test
    fun `panBy accumulates within bounds`() {
        zoomState.snapToScale(3f)
        // max offset x = (3-1)*800/2 = 800, y = (3-1)*600/2 = 600
        zoomState.panBy(Offset(300f, 200f), viewportWidth = 800f, viewportHeight = 600f)
        assertThat(zoomState.offset).isEqualTo(Offset(300f, 200f))

        zoomState.panBy(Offset(300f, 200f), viewportWidth = 800f, viewportHeight = 600f)
        assertThat(zoomState.offset).isEqualTo(Offset(600f, 400f))

        // Third pan pushes past max — clamped
        zoomState.panBy(Offset(300f, 300f), viewportWidth = 800f, viewportHeight = 600f)
        assertThat(zoomState.offset.x).isEqualTo(800f)
        assertThat(zoomState.offset.y).isEqualTo(600f)
    }

    // --- clampOffset static ---

    @Test
    fun `clampOffset returns zero when not zoomed`() {
        val result = ZoomState.clampOffset(Offset(50f, 50f), scale = 1f, 100f, 100f)
        assertThat(result).isEqualTo(Offset.Zero)
    }

    @Test
    fun `clampOffset returns zero when below 1x scale`() {
        val result = ZoomState.clampOffset(Offset(50f, 50f), scale = 0.5f, 100f, 100f)
        assertThat(result).isEqualTo(Offset.Zero)
    }

    @Test
    fun `clampOffset with asymmetric viewport`() {
        // scale 3, viewport 400x200 -> maxX = (3-1)*400/2 = 400, maxY = (3-1)*200/2 = 200
        val result = ZoomState.clampOffset(Offset(500f, -300f), scale = 3f, 400f, 200f)
        assertThat(result.x).isEqualTo(400f)
        assertThat(result.y).isEqualTo(-200f)
    }

    // --- doubleTapToggle ---

    @Test
    fun `doubleTapToggle zooms in from default`() {
        zoomState.doubleTapToggle()
        assertThat(zoomState.scale).isEqualTo(2f)
        assertThat(zoomState.offset).isEqualTo(Offset.Zero)
    }

    @Test
    fun `doubleTapToggle resets when zoomed`() {
        zoomState.snapToScale(3f)
        zoomState.doubleTapToggle()
        assertThat(zoomState.scale).isEqualTo(1f)
        assertThat(zoomState.offset).isEqualTo(Offset.Zero)
    }

    // --- toggleControls ---

    @Test
    fun `initial showControls is true`() {
        assertThat(zoomState.showControls).isTrue()
    }

    @Test
    fun `toggleControls flips visibility`() {
        zoomState.toggleControls()
        assertThat(zoomState.showControls).isFalse()

        zoomState.toggleControls()
        assertThat(zoomState.showControls).isTrue()
    }

    // --- reset ---

    @Test
    fun `reset restores all defaults`() {
        zoomState.snapToScale(3f)
        zoomState.panBy(Offset(100f, 100f), viewportWidth = 1000f, viewportHeight = 1000f)
        zoomState.toggleControls()

        zoomState.reset()

        assertThat(zoomState.scale).isEqualTo(1f)
        assertThat(zoomState.offset).isEqualTo(Offset.Zero)
        assertThat(zoomState.showControls).isTrue()
    }
}
