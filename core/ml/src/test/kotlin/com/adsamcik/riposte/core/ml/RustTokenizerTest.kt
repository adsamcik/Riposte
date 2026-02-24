package com.adsamcik.riposte.core.ml

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [RustTokenizer] JNI wrapper.
 *
 * These tests verify the Kotlin API surface and error handling.
 * The actual Rust tokenizer is tested via Rust unit tests in native/riposte-core/.
 * On-device parity tests (Rust vs Kotlin producing identical token IDs) require
 * the native library loaded on an Android device/emulator.
 */
class RustTokenizerTest {

    @Test
    fun `isAvailable returns false when native library not loaded`() {
        // In unit test environment (JVM), the native .so is not available
        val available = RustTokenizer.isAvailable()
        assertThat(available).isFalse()
    }

    @Test(expected = UnsatisfiedLinkError::class)
    fun `parse throws UnsatisfiedLinkError when native library not available`() {
        RustTokenizer.parse(byteArrayOf(1, 2, 3))
    }

    @Test(expected = UnsatisfiedLinkError::class)
    fun `loadNativeLibrary throws when not on Android`() {
        RustTokenizer.loadNativeLibrary()
    }
}
