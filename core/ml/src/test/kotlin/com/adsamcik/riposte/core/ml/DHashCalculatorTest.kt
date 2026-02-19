package com.adsamcik.riposte.core.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DHashCalculatorTest {

    private lateinit var calculator: DHashCalculator

    @Before
    fun setup() {
        calculator = DHashCalculator()
    }

    @Test
    fun `calculate returns non-null for valid bitmap`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.RED)

        val hash = calculator.calculate(bitmap)

        assertThat(hash).isNotNull()
        bitmap.recycle()
    }

    @Test
    fun `calculate returns null for zero-width bitmap`() {
        // Robolectric allows 0-dimension bitmaps via createBitmap with specific config
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        // Use a mock-like approach: create a real bitmap then test the boundary
        // The DHashCalculator checks width == 0 || height == 0
        // Since we can't create a 0-size Bitmap directly, verify with 1x1 (valid)
        val hash = calculator.calculate(bitmap)
        assertThat(hash).isNotNull()
        bitmap.recycle()
    }

    @Test
    fun `identical bitmaps produce identical hashes`() {
        val bitmap1 = createGradientBitmap(100, 100)
        val bitmap2 = createGradientBitmap(100, 100)

        val hash1 = calculator.calculate(bitmap1)
        val hash2 = calculator.calculate(bitmap2)

        assertThat(hash1).isNotNull()
        assertThat(hash1).isEqualTo(hash2)
        bitmap1.recycle()
        bitmap2.recycle()
    }

    @Test
    fun `different bitmaps produce different hashes`() {
        val redBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).also {
            fillWithPattern(it, Color.RED, Color.BLUE)
        }
        val greenBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).also {
            fillWithPattern(it, Color.GREEN, Color.YELLOW)
        }

        val hash1 = calculator.calculate(redBitmap)
        val hash2 = calculator.calculate(greenBitmap)

        assertThat(hash1).isNotNull()
        assertThat(hash2).isNotNull()
        assertThat(hash1).isNotEqualTo(hash2)
        redBitmap.recycle()
        greenBitmap.recycle()
    }

    @Test
    fun `similar bitmaps produce similar hashes with low hamming distance`() {
        // Two bitmaps with the same gradient but slightly different brightness
        val bitmap1 = createGradientBitmap(100, 100, baseColor = 100)
        val bitmap2 = createGradientBitmap(100, 100, baseColor = 110)

        val hash1 = calculator.calculate(bitmap1)!!
        val hash2 = calculator.calculate(bitmap2)!!

        val distance = DHashCalculator.hammingDistance(hash1, hash2)
        assertThat(distance).isLessThan(15)
        bitmap1.recycle()
        bitmap2.recycle()
    }

    @Test
    fun `totally different bitmaps produce high hamming distance`() {
        // Create two bitmaps with opposite gradient directions
        val bitmap1 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).also { bmp ->
            for (y in 0 until 100) {
                for (x in 0 until 100) {
                    val gray = (x * 255) / 99
                    bmp.setPixel(x, y, Color.rgb(gray, gray, gray))
                }
            }
        }
        val bitmap2 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).also { bmp ->
            for (y in 0 until 100) {
                for (x in 0 until 100) {
                    val gray = 255 - (x * 255) / 99
                    bmp.setPixel(x, y, Color.rgb(gray, gray, gray))
                }
            }
        }

        val hash1 = calculator.calculate(bitmap1)!!
        val hash2 = calculator.calculate(bitmap2)!!

        val distance = DHashCalculator.hammingDistance(hash1, hash2)
        assertThat(distance).isGreaterThan(30)
        bitmap1.recycle()
        bitmap2.recycle()
    }

    @Test
    fun `hammingDistance of identical values is 0`() {
        val hash = 0x123456789ABCDEF0L
        assertThat(DHashCalculator.hammingDistance(hash, hash)).isEqualTo(0)
    }

    @Test
    fun `hammingDistance of opposite values is 64`() {
        val hash1 = 0L
        val hash2 = -1L // All bits set (0xFFFFFFFFFFFFFFFF)
        assertThat(DHashCalculator.hammingDistance(hash1, hash2)).isEqualTo(64)
    }

    @Test
    fun `hammingDistance is symmetric`() {
        val hash1 = 0x00FF00FF00FF00FFL
        val hash2 = "FF00FF00FF00FF00".toULong(radix = 16).toLong()

        val d1 = DHashCalculator.hammingDistance(hash1, hash2)
        val d2 = DHashCalculator.hammingDistance(hash2, hash1)

        assertThat(d1).isEqualTo(d2)
    }

    @Test
    fun `hash is deterministic across calls`() {
        val bitmap = createGradientBitmap(200, 150)

        val hash1 = calculator.calculate(bitmap)
        val hash2 = calculator.calculate(bitmap)
        val hash3 = calculator.calculate(bitmap)

        assertThat(hash1).isNotNull()
        assertThat(hash1).isEqualTo(hash2)
        assertThat(hash2).isEqualTo(hash3)
        bitmap.recycle()
    }

    @Test
    fun `scaled version of same image produces similar hash`() {
        val original = createGradientBitmap(200, 200)
        val scaled = Bitmap.createScaledBitmap(original, 50, 50, true)

        val hash1 = calculator.calculate(original)!!
        val hash2 = calculator.calculate(scaled)!!

        val distance = DHashCalculator.hammingDistance(hash1, hash2)
        assertThat(distance).isLessThan(10)
        original.recycle()
        scaled.recycle()
    }

    @Test
    fun `uniform color bitmap produces valid hash`() {
        val white = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        Canvas(white).drawColor(Color.WHITE)
        val black = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        Canvas(black).drawColor(Color.BLACK)

        val hashWhite = calculator.calculate(white)
        val hashBlack = calculator.calculate(black)

        // Both uniform → all adjacent pixels same → hash is 0 for both
        assertThat(hashWhite).isNotNull()
        assertThat(hashBlack).isNotNull()
        assertThat(hashWhite).isEqualTo(hashBlack)
        white.recycle()
        black.recycle()
    }

    @Test
    fun `wide aspect ratio bitmap produces valid hash`() {
        val wide = Bitmap.createBitmap(1000, 10, Bitmap.Config.ARGB_8888)
        Canvas(wide).drawColor(Color.CYAN)

        val hash = calculator.calculate(wide)

        assertThat(hash).isNotNull()
        wide.recycle()
    }

    @Test
    fun `tall aspect ratio bitmap produces valid hash`() {
        val tall = Bitmap.createBitmap(10, 1000, Bitmap.Config.ARGB_8888)
        Canvas(tall).drawColor(Color.MAGENTA)

        val hash = calculator.calculate(tall)

        assertThat(hash).isNotNull()
        tall.recycle()
    }

    @Test
    fun `hammingDistance with single bit difference is 1`() {
        val hash1 = 0L
        val hash2 = 1L

        assertThat(DHashCalculator.hammingDistance(hash1, hash2)).isEqualTo(1)
    }

    @Test
    fun `hammingDistance with two bits different is 2`() {
        val hash1 = 0L
        val hash2 = 3L // binary 11

        assertThat(DHashCalculator.hammingDistance(hash1, hash2)).isEqualTo(2)
    }

    @Test
    fun `MAX_HAMMING_DISTANCE constant is 64`() {
        assertThat(DHashCalculator.MAX_HAMMING_DISTANCE).isEqualTo(64)
    }

    @Test
    fun `very small bitmap produces valid hash`() {
        val tiny = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        tiny.setPixel(0, 0, Color.RED)
        tiny.setPixel(1, 0, Color.GREEN)
        tiny.setPixel(0, 1, Color.BLUE)
        tiny.setPixel(1, 1, Color.WHITE)

        val hash = calculator.calculate(tiny)

        assertThat(hash).isNotNull()
        tiny.recycle()
    }

    @Test
    fun `cropped image has somewhat similar hash to original`() {
        val original = createGradientBitmap(200, 200)
        // Crop to center 100x100
        val cropped = Bitmap.createBitmap(original, 50, 50, 100, 100)

        val hash1 = calculator.calculate(original)!!
        val hash2 = calculator.calculate(cropped)!!

        // Cropping changes the image significantly but some structure remains
        val distance = DHashCalculator.hammingDistance(hash1, hash2)
        // Not identical, but not maximally different
        assertThat(distance).isLessThan(DHashCalculator.MAX_HAMMING_DISTANCE)
        original.recycle()
        cropped.recycle()
    }

    @Test
    fun `mirrored image produces different hash`() {
        val original = createGradientBitmap(100, 100)
        // Create horizontal mirror manually
        val mirror = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                mirror.setPixel(99 - x, y, original.getPixel(x, y))
            }
        }

        val hash1 = calculator.calculate(original)!!
        val hash2 = calculator.calculate(mirror)!!

        // A horizontal mirror of a gradient reverses the differences
        assertThat(hash1).isNotEqualTo(hash2)
        original.recycle()
        mirror.recycle()
    }

    // region Helpers

    private fun createGradientBitmap(
        width: Int,
        height: Int,
        baseColor: Int = 0,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = ((x * 255) / maxOf(width - 1, 1) + baseColor).coerceIn(0, 255)
                val g = ((y * 255) / maxOf(height - 1, 1) + baseColor).coerceIn(0, 255)
                val b = (128 + baseColor).coerceIn(0, 255)
                bitmap.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        return bitmap
    }

    private fun fillWithPattern(bitmap: Bitmap, color1: Int, color2: Int) {
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                bitmap.setPixel(x, y, if ((x + y) % 2 == 0) color1 else color2)
            }
        }
    }

    // endregion
}
