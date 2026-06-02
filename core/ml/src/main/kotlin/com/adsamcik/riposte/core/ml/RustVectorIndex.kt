package com.adsamcik.riposte.core.ml

import timber.log.Timber
import java.io.Closeable

/**
 * JNI bridge to the Rust USearch HNSW vector index.
 *
 * Provides approximate nearest neighbor (ANN) search using cosine similarity.
 * The index operates on f32 vectors and supports save/load for persistence.
 *
 * Thread-safe: the underlying Rust index is wrapped in `Arc<VectorIndex>`.
 *
 * @see RustTokenizer for the same JNI handle pattern.
 */
internal class RustVectorIndex private constructor(
    private var nativeHandle: Long,
    /** Number of dimensions the index was created with. */
    val dimensions: Int,
) : Closeable {

    /**
     * Reserves capacity for the given number of vectors.
     * Call before bulk-adding to avoid reallocations.
     */
    fun reserve(capacity: Int) {
        val handle = requireHandle()
        nativeReserve(handle, capacity)
    }

    /**
     * Adds a vector with the given key to the index.
     *
     * @param key Unique identifier for the vector (e.g., memeId hash).
     * @param vector The embedding vector (must have [dimensions] elements).
     */
    fun add(key: Long, vector: FloatArray) {
        val handle = requireHandle()
        nativeAdd(handle, key, vector)
    }

    /**
     * Searches for the `count` nearest neighbors to the query vector.
     *
     * @return Pair of (keys, distances) arrays. Distances are cosine distances
     *         (lower = more similar; for normalized vectors: distance ≈ 1 - similarity).
     */
    fun search(query: FloatArray, count: Int): Pair<LongArray, FloatArray> {
        val handle = requireHandle()
        val result = nativeSearch(handle, query, count)
        @Suppress("UNCHECKED_CAST")
        val resultArray = result as Array<Any>
        return Pair(resultArray[0] as LongArray, resultArray[1] as FloatArray)
    }

    /**
     * Removes a vector by key.
     * @return true if the vector was found and removed.
     */
    fun remove(key: Long): Boolean {
        val handle = requireHandle()
        return nativeRemove(handle, key)
    }

    /** Number of vectors currently in the index. */
    fun size(): Int {
        val handle = requireHandle()
        return nativeLen(handle)
    }

    /** Returns true if a vector with the given key exists. */
    fun contains(key: Long): Boolean {
        val handle = requireHandle()
        return nativeContains(handle, key)
    }

    /** Saves the index to a file for persistence. */
    fun save(path: String) {
        val handle = requireHandle()
        nativeSave(handle, path)
    }

    /** Loads the index from a previously saved file. */
    fun load(path: String) {
        val handle = requireHandle()
        nativeLoad(handle, path)
    }

    override fun close() {
        val handle = nativeHandle
        if (handle != 0L) {
            nativeHandle = 0L
            nativeRelease(handle)
        }
    }

    @Suppress("removal")
    protected fun finalize() {
        if (nativeHandle != 0L) {
            Timber.w("RustVectorIndex was not closed before GC")
            close()
        }
    }

    private fun requireHandle(): Long {
        val handle = nativeHandle
        check(handle != 0L) { "RustVectorIndex has been closed" }
        return handle
    }

    companion object {
        /**
         * Creates a new HNSW index with cosine similarity metric.
         *
         * @param dimensions Number of dimensions per vector (e.g., 256 for Matryoshka).
         * @param useF16 If true, quantize vectors to f16 for lower memory (slight quality loss).
         */
        fun create(dimensions: Int, useF16: Boolean = false): RustVectorIndex {
            RustTokenizer.loadNativeLibrary() // same .so
            val handle = nativeCreate(dimensions, useF16)
            check(handle != 0L) { "Failed to create vector index" }
            return RustVectorIndex(handle, dimensions)
        }

        /**
         * Returns true if the native library is available.
         */
        fun isAvailable(): Boolean = RustTokenizer.isAvailable()

        @JvmStatic
        private external fun nativeCreate(dimensions: Int, useF16: Boolean): Long

        @JvmStatic
        private external fun nativeReserve(handle: Long, capacity: Int)

        @JvmStatic
        private external fun nativeAdd(handle: Long, key: Long, vector: FloatArray)

        @JvmStatic
        private external fun nativeSearch(handle: Long, query: FloatArray, count: Int): Any

        @JvmStatic
        private external fun nativeRemove(handle: Long, key: Long): Boolean

        @JvmStatic
        private external fun nativeLen(handle: Long): Int

        @JvmStatic
        private external fun nativeSave(handle: Long, path: String)

        @JvmStatic
        private external fun nativeLoad(handle: Long, path: String)

        @JvmStatic
        private external fun nativeContains(handle: Long, key: Long): Boolean

        @JvmStatic
        private external fun nativeRelease(handle: Long)
    }
}
