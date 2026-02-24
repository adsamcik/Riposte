package com.adsamcik.riposte.core.ml

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [RustVectorIndex] JNI wrapper.
 *
 * In the JVM test environment the native library is unavailable,
 * so we verify the error handling paths. On-device integration tests
 * verify actual ANN functionality.
 */
class RustVectorIndexTest {

    @Test
    fun `isAvailable returns false when native library not loaded`() {
        assertThat(RustVectorIndex.isAvailable()).isFalse()
    }

    @Test(expected = UnsatisfiedLinkError::class)
    fun `create throws UnsatisfiedLinkError when native library not available`() {
        RustVectorIndex.create(dimensions = 256)
    }

    @Test(expected = UnsatisfiedLinkError::class)
    fun `create with f16 throws when native library not available`() {
        RustVectorIndex.create(dimensions = 256, useF16 = true)
    }
}
