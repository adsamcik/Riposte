package com.adsamcik.riposte.core.ml

import timber.log.Timber
import java.io.Closeable

/**
 * JNI bridge to the Rust SentencePiece tokenizer.
 *
 * Provides the same tokenization interface as the Kotlin [SentencePieceTokenizer]
 * but delegates to a Rust implementation for better performance and lower memory usage.
 *
 * Usage:
 * ```kotlin
 * val tokenizer = RustTokenizer.parse(modelBytes)
 * val ids = tokenizer.encode("hello world")
 * tokenizer.close() // or use `use { }` block
 * ```
 *
 * Thread-safe: the underlying Rust tokenizer is wrapped in `Arc<Tokenizer>`
 * and all operations are read-only after construction.
 */
internal class RustTokenizer private constructor(
    private var nativeHandle: Long,
) : Closeable {

    /**
     * Tokenizes text using Gemma-compatible preprocessing and Viterbi segmentation.
     *
     * @return List of token IDs. Does NOT include BOS/EOS.
     * @throws IllegalStateException if the tokenizer has been closed.
     */
    fun encode(text: String): List<Int> {
        val handle = nativeHandle
        check(handle != 0L) { "RustTokenizer has been closed" }
        val ids = nativeEncode(handle, text)
        return ids.toList()
    }

    /**
     * Returns the vocabulary size.
     */
    fun vocabSize(): Int {
        val handle = nativeHandle
        check(handle != 0L) { "RustTokenizer has been closed" }
        return nativeVocabSize(handle)
    }

    /**
     * Releases the native tokenizer resources.
     * Safe to call multiple times.
     */
    override fun close() {
        val handle = nativeHandle
        if (handle != 0L) {
            nativeHandle = 0L
            nativeRelease(handle)
        }
    }

    protected fun finalize() {
        if (nativeHandle != 0L) {
            Timber.w("RustTokenizer was not closed before GC — leaking native memory")
            close()
        }
    }

    companion object {
        private var nativeLoaded = false

        /**
         * Loads the native library. Safe to call multiple times.
         */
        @Synchronized
        fun loadNativeLibrary() {
            if (!nativeLoaded) {
                try {
                    System.loadLibrary("riposte_jni")
                    nativeLoaded = true
                    Timber.i("Loaded riposte_jni native library")
                } catch (e: UnsatisfiedLinkError) {
                    Timber.e(e, "Failed to load riposte_jni native library")
                    throw e
                }
            }
        }

        /**
         * Returns true if the native library is available.
         */
        fun isAvailable(): Boolean {
            return try {
                loadNativeLibrary()
                true
            } catch (_: UnsatisfiedLinkError) {
                false
            }
        }

        /**
         * Parses a SentencePiece `.model` from raw bytes and creates a tokenizer.
         *
         * @throws RuntimeException if parsing fails or native library unavailable.
         */
        fun parse(modelData: ByteArray): RustTokenizer {
            loadNativeLibrary()
            val handle = nativeParse(modelData)
            if (handle == 0L) {
                throw RuntimeException("Failed to parse SentencePiece model in Rust")
            }
            return RustTokenizer(handle)
        }

        // JNI native methods — implemented in riposte-jni Rust crate
        @JvmStatic
        private external fun nativeParse(modelData: ByteArray): Long

        @JvmStatic
        private external fun nativeEncode(handle: Long, text: String): IntArray

        @JvmStatic
        private external fun nativeVocabSize(handle: Long): Int

        @JvmStatic
        private external fun nativeRelease(handle: Long)
    }
}
