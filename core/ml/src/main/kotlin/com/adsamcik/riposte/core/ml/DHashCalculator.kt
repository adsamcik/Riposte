package com.adsamcik.riposte.core.ml

import android.graphics.Bitmap
import android.graphics.Color
import javax.inject.Inject

/**
 * Calculates difference hash (dHash) for perceptual image similarity detection.
 *
 * dHash works by:
 * 1. Resize image to 9×8 pixels (grayscale)
 * 2. Compare each pixel with its right neighbor
 * 3. Produce a 64-bit hash (8 rows × 8 comparisons)
 *
 * Similar images produce similar hashes. Hamming distance between hashes
 * indicates visual similarity (0 = identical, ≤5 = very similar, ≤10 = somewhat similar).
 */
class DHashCalculator @Inject constructor() {

    companion object {
        /** Width of the resized image (one extra column for comparison). */
        private const val HASH_WIDTH = 9

        /** Height of the resized image. */
        private const val HASH_HEIGHT = 8

        /** Number of bits in the hash. */
        private const val HASH_BITS = 64

        /** Maximum possible Hamming distance between two dHash values. */
        const val MAX_HAMMING_DISTANCE = HASH_BITS

        /**
         * Compute the Hamming distance between two dHash values.
         * Returns the number of bits that differ (0 = identical).
         */
        fun hammingDistance(hash1: Long, hash2: Long): Int =
            java.lang.Long.bitCount(hash1 xor hash2)
    }

    /**
     * Calculate the dHash of a bitmap.
     *
     * @param bitmap The source image (any size, any format).
     * @return 64-bit perceptual hash, or null if the bitmap cannot be processed.
     */
    fun calculate(bitmap: Bitmap): Long? {
        if (bitmap.width == 0 || bitmap.height == 0) return null

        // Resize to 9×8 using bilinear filtering for smooth gradients
        val scaled = Bitmap.createScaledBitmap(bitmap, HASH_WIDTH, HASH_HEIGHT, true)

        var hash = 0L
        var bit = 0

        for (y in 0 until HASH_HEIGHT) {
            for (x in 0 until HASH_WIDTH - 1) {
                val leftPixel = scaled.getPixel(x, y)
                val rightPixel = scaled.getPixel(x + 1, y)

                val leftGray = luminance(leftPixel)
                val rightGray = luminance(rightPixel)

                if (leftGray > rightGray) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }

        if (scaled !== bitmap) {
            scaled.recycle()
        }

        return hash
    }

    /**
     * Convert an ARGB pixel to grayscale luminance using ITU-R BT.601.
     */
    private fun luminance(pixel: Int): Double {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}
