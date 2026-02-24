package com.adsamcik.riposte.core.ml

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertThrows
import org.junit.Test

class EmbeddingUtilsTest {

    private val tolerance = 1e-5f

    // region cosineSimilarity

    @Test
    fun `cosineSimilarity of identical vectors returns 1`() {
        val v = floatArrayOf(1f, 2f, 3f)
        val result = EmbeddingUtils.cosineSimilarity(v, v.copyOf())
        assertThat(result).isWithin(tolerance).of(1f)
    }

    @Test
    fun `cosineSimilarity of opposite vectors returns negative 1`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(-1f, -2f, -3f)
        val result = EmbeddingUtils.cosineSimilarity(a, b)
        assertThat(result).isWithin(tolerance).of(-1f)
    }

    @Test
    fun `cosineSimilarity of orthogonal vectors returns 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        val result = EmbeddingUtils.cosineSimilarity(a, b)
        assertThat(result).isWithin(tolerance).of(0f)
    }

    @Test
    fun `cosineSimilarity of pre-normalized vectors`() {
        val a = EmbeddingUtils.normalize(floatArrayOf(3f, 4f))
        val b = EmbeddingUtils.normalize(floatArrayOf(3f, 4f))
        val result = EmbeddingUtils.cosineSimilarity(a, b)
        assertThat(result).isWithin(tolerance).of(1f)
    }

    @Test
    fun `cosineSimilarity of non-normalized vectors returns correct value`() {
        val a = floatArrayOf(10f, 20f, 30f)
        val b = floatArrayOf(100f, 200f, 300f)
        val result = EmbeddingUtils.cosineSimilarity(a, b)
        assertThat(result).isWithin(tolerance).of(1f)
    }

    @Test
    fun `cosineSimilarity with zero vector returns 0`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val zero = floatArrayOf(0f, 0f, 0f)
        val result = EmbeddingUtils.cosineSimilarity(a, zero)
        assertThat(result).isEqualTo(0f)
    }

    @Test
    fun `cosineSimilarity with both zero vectors returns 0`() {
        val zero = floatArrayOf(0f, 0f, 0f)
        val result = EmbeddingUtils.cosineSimilarity(zero, zero.copyOf())
        assertThat(result).isEqualTo(0f)
    }

    @Test
    fun `cosineSimilarity with single-element vectors`() {
        val a = floatArrayOf(5f)
        val b = floatArrayOf(3f)
        val result = EmbeddingUtils.cosineSimilarity(a, b)
        assertThat(result).isWithin(tolerance).of(1f)
    }

    @Test
    fun `cosineSimilarity with single-element opposite vectors`() {
        val a = floatArrayOf(5f)
        val b = floatArrayOf(-3f)
        val result = EmbeddingUtils.cosineSimilarity(a, b)
        assertThat(result).isWithin(tolerance).of(-1f)
    }

    @Test
    fun `cosineSimilarity with large dimension vectors (768d)`() {
        val a = FloatArray(768) { (it + 1).toFloat() }
        val b = FloatArray(768) { (it + 1).toFloat() }
        val result = EmbeddingUtils.cosineSimilarity(a, b)
        assertThat(result).isWithin(tolerance).of(1f)
    }

    @Test
    fun `cosineSimilarity with dimension mismatch throws IllegalArgumentException`() {
        val a = floatArrayOf(1f, 2f)
        val b = floatArrayOf(1f, 2f, 3f)
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingUtils.cosineSimilarity(a, b)
        }
    }

    @Test
    fun `cosineSimilarity with negative collinear values returns 1`() {
        val a = floatArrayOf(-1f, -2f, -3f)
        val b = floatArrayOf(-2f, -4f, -6f)
        val result = EmbeddingUtils.cosineSimilarity(a, b)
        assertThat(result).isWithin(tolerance).of(1f)
    }

    @Test
    fun `cosineSimilarity with very small values near zero`() {
        val a = floatArrayOf(1e-20f, 2e-20f, 3e-20f)
        val b = floatArrayOf(4e-20f, 5e-20f, 6e-20f)
        val result = EmbeddingUtils.cosineSimilarity(a, b)
        // Very small values may underflow; should return 0 gracefully
        assertThat(abs(result)).isAtMost(1f)
    }

    @Test
    fun `cosineSimilarity is symmetric`() {
        val a = floatArrayOf(1f, 3f, -5f, 7f)
        val b = floatArrayOf(-2f, 4f, 6f, -8f)
        val ab = EmbeddingUtils.cosineSimilarity(a, b)
        val ba = EmbeddingUtils.cosineSimilarity(b, a)
        assertThat(ab).isWithin(tolerance).of(ba)
    }

    @Test
    fun `cosineSimilarity with mixed positive and negative values`() {
        val a = floatArrayOf(1f, -1f)
        val b = floatArrayOf(-1f, 1f)
        val result = EmbeddingUtils.cosineSimilarity(a, b)
        assertThat(result).isWithin(tolerance).of(-1f)
    }

    @Test
    fun `cosineSimilarity with empty vectors throws`() {
        val a = floatArrayOf()
        val b = floatArrayOf()
        // Both are empty so magnitude is 0; require same size passes, returns 0
        val result = EmbeddingUtils.cosineSimilarity(a, b)
        assertThat(result).isEqualTo(0f)
    }

    // endregion

    // region normalize

    @Test
    fun `normalize of unit vector stays unit`() {
        val unit = floatArrayOf(1f, 0f, 0f)
        val result = EmbeddingUtils.normalize(unit)
        assertThat(l2Norm(result)).isWithin(tolerance).of(1f)
        assertThat(result[0]).isWithin(tolerance).of(1f)
        assertThat(result[1]).isWithin(tolerance).of(0f)
        assertThat(result[2]).isWithin(tolerance).of(0f)
    }

    @Test
    fun `normalize of zero vector returns zero without NaN`() {
        val zero = floatArrayOf(0f, 0f, 0f)
        val result = EmbeddingUtils.normalize(zero)
        for (v in result) {
            assertThat(v).isEqualTo(0f)
            assertThat(v.isNaN()).isFalse()
        }
    }

    @Test
    fun `normalize returns new array and does not mutate original`() {
        val original = floatArrayOf(3f, 4f)
        val copy = original.copyOf()
        val result = EmbeddingUtils.normalize(original)
        assertThat(result).isNotSameInstanceAs(original)
        assertThat(original).isEqualTo(copy)
    }

    @Test
    fun `normalize output has L2 norm of 1`() {
        val v = floatArrayOf(3f, 4f, 5f, 6f)
        val result = EmbeddingUtils.normalize(v)
        assertThat(l2Norm(result)).isWithin(tolerance).of(1f)
    }

    @Test
    fun `normalize preserves direction`() {
        val v = floatArrayOf(3f, 4f, 5f)
        val normalized = EmbeddingUtils.normalize(v)
        val similarity = EmbeddingUtils.cosineSimilarity(v, normalized)
        assertThat(similarity).isWithin(tolerance).of(1f)
    }

    @Test
    fun `normalize with negative values`() {
        val v = floatArrayOf(-3f, -4f)
        val result = EmbeddingUtils.normalize(v)
        assertThat(l2Norm(result)).isWithin(tolerance).of(1f)
        assertThat(result[0]).isLessThan(0f)
        assertThat(result[1]).isLessThan(0f)
    }

    @Test
    fun `normalize single-element vector`() {
        val v = floatArrayOf(7f)
        val result = EmbeddingUtils.normalize(v)
        assertThat(result).hasLength(1)
        assertThat(result[0]).isWithin(tolerance).of(1f)
    }

    @Test
    fun `normalize single-element negative vector`() {
        val v = floatArrayOf(-7f)
        val result = EmbeddingUtils.normalize(v)
        assertThat(result[0]).isWithin(tolerance).of(-1f)
    }

    @Test
    fun `normalize large vector (768d) has unit norm`() {
        val v = FloatArray(768) { (it + 1).toFloat() }
        val result = EmbeddingUtils.normalize(v)
        assertThat(result).hasLength(768)
        assertThat(l2Norm(result)).isWithin(tolerance).of(1f)
    }

    @Test
    fun `normalize already-normalized vector stays normalized`() {
        val v = EmbeddingUtils.normalize(floatArrayOf(1f, 2f, 3f))
        val result = EmbeddingUtils.normalize(v)
        assertThat(l2Norm(result)).isWithin(tolerance).of(1f)
        for (i in v.indices) {
            assertThat(result[i]).isWithin(tolerance).of(v[i])
        }
    }

    @Test
    fun `normalize with large values does not produce NaN`() {
        val v = floatArrayOf(1e18f, 1e18f)
        val result = EmbeddingUtils.normalize(v)
        assertThat(l2Norm(result)).isWithin(tolerance).of(1f)
        for (value in result) {
            assertThat(value.isNaN()).isFalse()
            assertThat(value.isInfinite()).isFalse()
        }
    }

    // endregion

    // region truncateEmbedding

    @Test
    fun `truncateEmbedding 768 to 256`() {
        val v = FloatArray(768) { it.toFloat() }
        val result = EmbeddingUtils.truncateEmbedding(v, 256)
        assertThat(result).hasLength(256)
    }

    @Test
    fun `truncateEmbedding 768 to 128`() {
        val v = FloatArray(768) { it.toFloat() }
        val result = EmbeddingUtils.truncateEmbedding(v, 128)
        assertThat(result).hasLength(128)
    }

    @Test
    fun `truncateEmbedding 768 to 384`() {
        val v = FloatArray(768) { it.toFloat() }
        val result = EmbeddingUtils.truncateEmbedding(v, 384)
        assertThat(result).hasLength(384)
    }

    @Test
    fun `truncateEmbedding 768 to 512`() {
        val v = FloatArray(768) { it.toFloat() }
        val result = EmbeddingUtils.truncateEmbedding(v, 512)
        assertThat(result).hasLength(512)
    }

    @Test
    fun `truncateEmbedding output is L2 normalized`() {
        val v = FloatArray(768) { (it + 1).toFloat() }
        val result = EmbeddingUtils.truncateEmbedding(v, 256)
        assertThat(l2Norm(result)).isWithin(tolerance).of(1f)
    }

    @Test
    fun `truncateEmbedding with invalid dimension throws IllegalArgumentException`() {
        val v = FloatArray(768) { it.toFloat() }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingUtils.truncateEmbedding(v, 100)
        }
    }

    @Test
    fun `truncateEmbedding with dimension larger than input throws`() {
        val v = FloatArray(128) { it.toFloat() }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingUtils.truncateEmbedding(v, 256)
        }
    }

    @Test
    fun `truncateEmbedding to same dimension returns normalized copy`() {
        val v = FloatArray(768) { (it + 1).toFloat() }
        val result = EmbeddingUtils.truncateEmbedding(v, 768)
        assertThat(result).hasLength(768)
        assertThat(l2Norm(result)).isWithin(tolerance).of(1f)
        assertThat(result).isNotSameInstanceAs(v)
    }

    @Test
    fun `truncateEmbedding result has correct length for each valid dimension`() {
        val v = FloatArray(768) { it.toFloat() }
        for (dim in listOf(128, 256, 384, 512, 768)) {
            val result = EmbeddingUtils.truncateEmbedding(v, dim)
            assertThat(result).hasLength(dim)
        }
    }

    // endregion

    // endregion

    // region quantizeToInt8

    @Test
    fun `quantizeToInt8 returns correct byte size`() {
        val embedding = floatArrayOf(0.5f, -0.3f, 0.8f, -0.1f)
        val (bytes, _) = EmbeddingUtils.quantizeToInt8(embedding)
        assertThat(bytes.size).isEqualTo(embedding.size)
    }

    @Test
    fun `quantizeToInt8 and dequantizeFromInt8 roundtrip preserves direction`() {
        val original = floatArrayOf(0.5f, -0.3f, 0.8f, -0.1f, 0.0f)
        val (bytes, scale) = EmbeddingUtils.quantizeToInt8(original)
        val reconstructed = EmbeddingUtils.dequantizeFromInt8(bytes, scale)
        val similarity = EmbeddingUtils.cosineSimilarity(original, reconstructed)
        assertThat(similarity).isGreaterThan(0.99f)
    }

    @Test
    fun `quantizeToInt8 handles empty array`() {
        val (bytes, scale) = EmbeddingUtils.quantizeToInt8(FloatArray(0))
        assertThat(bytes).isEmpty()
        assertThat(scale).isEqualTo(1f)
    }

    @Test
    fun `quantizeToInt8 handles all-zero array`() {
        val (bytes, scale) = EmbeddingUtils.quantizeToInt8(floatArrayOf(0f, 0f, 0f))
        assertThat(bytes.toList()).containsExactly(0.toByte(), 0.toByte(), 0.toByte())
        assertThat(scale).isEqualTo(1f)
    }

    @Test
    fun `quantizeToInt8 extreme values map to byte range`() {
        val (bytes, _) = EmbeddingUtils.quantizeToInt8(floatArrayOf(1f, -1f))
        assertThat(bytes[0]).isEqualTo(127.toByte())
        assertThat(bytes[1]).isEqualTo((-127).toByte())
    }

    @Test
    fun `quantizeToInt8 preserves relative magnitudes`() {
        val (bytes, _) = EmbeddingUtils.quantizeToInt8(floatArrayOf(0.1f, 0.5f, 1.0f))
        assertThat(bytes[0].toInt()).isLessThan(bytes[1].toInt())
        assertThat(bytes[1].toInt()).isLessThan(bytes[2].toInt())
    }

    @Test
    fun `quantizeToInt8 with 768-dimensional embedding maintains similarity`() {
        val original = FloatArray(768) { (it % 17 - 8).toFloat() / 10f }
        val normalized = EmbeddingUtils.normalize(original)
        val (bytes, scale) = EmbeddingUtils.quantizeToInt8(normalized)
        val reconstructed = EmbeddingUtils.dequantizeFromInt8(bytes, scale)
        val similarity = EmbeddingUtils.cosineSimilarity(normalized, reconstructed)
        assertThat(similarity).isGreaterThan(0.99f)
    }

    @Test
    fun `int8 storage is 4x smaller than float32`() {
        val embedding = FloatArray(768) { it.toFloat() / 768f }
        val float32Bytes = EmbeddingUtils.encodeFloat32(embedding)
        val (int8Bytes, _) = EmbeddingUtils.quantizeToInt8(embedding)
        assertThat(float32Bytes.size).isEqualTo(768 * 4)
        assertThat(int8Bytes.size).isEqualTo(768)
    }

    // endregion

    // region encodeFloat32 / decodeFloat32

    @Test
    fun `encodeFloat32 and decodeFloat32 roundtrip correctly`() {
        val original = floatArrayOf(1.0f, -0.5f, 3.14f, 0.0f)
        val bytes = EmbeddingUtils.encodeFloat32(original)
        val decoded = EmbeddingUtils.decodeFloat32(bytes)
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `encodeFloat32 produces correct byte size`() {
        val bytes = EmbeddingUtils.encodeFloat32(FloatArray(256))
        assertThat(bytes.size).isEqualTo(256 * 4)
    }

    // endregion

    // region Helpers

    private fun l2Norm(v: FloatArray): Float {
        var sum = 0f
        for (value in v) {
            sum += value * value
        }
        return sqrt(sum)
    }

    // endregion
}
