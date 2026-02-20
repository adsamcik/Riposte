package com.adsamcik.riposte.core.testing

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.delay

/**
 * Fake implementation of a text recognizer for testing.
 *
 * Allows configuration of predefined recognition results and error simulation.
 *
 * Usage:
 * ```kotlin
 * val fakeRecognizer = FakeTextRecognizer()
 * fakeRecognizer.setResult("Hello World!")
 *
 * val result = fakeRecognizer.recognizeText(bitmap)
 * assertThat(result).isEqualTo("Hello World!")
 * ```
 */
class FakeTextRecognizer {
    private var defaultResult: String = ""
    private var resultsByUri = mutableMapOf<String, String>()
    private var simulatedError: Throwable? = null
    private var simulatedDelay: Long = 0L
    private var isReady: Boolean = true
    private var processCount: Int = 0

    // ============ Configuration ============

    /**
     * Sets the default text result for all recognition calls.
     */
    fun setResult(text: String) {
        defaultResult = text
    }

    /**
     * Sets a specific result for a given URI.
     */
    fun setResultForUri(
        uri: Uri,
        text: String,
    ) {
        resultsByUri[uri.toString()] = text
    }

    /**
     * Sets a specific result for a URI string.
     */
    fun setResultForUri(
        uriString: String,
        text: String,
    ) {
        resultsByUri[uriString] = text
    }

    /**
     * Configures the recognizer to throw an error on the next call.
     */
    fun setError(error: Throwable?) {
        simulatedError = error
    }

    /**
     * Configures an artificial delay for recognition calls.
     */
    fun setDelay(delayMs: Long) {
        simulatedDelay = delayMs
    }

    /**
     * Sets whether the recognizer is ready.
     */
    fun setReady(ready: Boolean) {
        isReady = ready
    }

    // ============ Recognition Methods ============

    /**
     * Recognizes text from a bitmap.
     */
    // API compliance with TextRecognizer interface
    @Suppress("UnusedParameter")
    suspend fun recognizeText(bitmap: Bitmap): Result<String> {
        return executeRecognition {
            defaultResult
        }
    }

    /**
     * Recognizes text from a URI.
     */
    suspend fun recognizeText(uri: Uri): Result<String> {
        return executeRecognition {
            resultsByUri[uri.toString()] ?: defaultResult
        }
    }

    /**
     * Checks if the recognizer is ready to process.
     */
    fun isReady(): Boolean = isReady

    /**
     * Gets the number of recognition calls made.
     */
    fun getProcessCount(): Int = processCount

    // ============ Test Helpers ============

    /**
     * Resets the recognizer to its initial state.
     */
    fun reset() {
        defaultResult = ""
        resultsByUri.clear()
        simulatedError = null
        simulatedDelay = 0L
        isReady = true
        processCount = 0
    }

    /**
     * Clears all configured results.
     */
    fun clearResults() {
        defaultResult = ""
        resultsByUri.clear()
    }

    // ============ Internal ============

    private suspend fun executeRecognition(textProvider: () -> String): Result<String> {
        processCount++

        if (!isReady) {
            return Result.failure(IllegalStateException("Text recognizer is not ready"))
        }

        if (simulatedDelay > 0) {
            delay(simulatedDelay)
        }

        return simulatedError?.let { error ->
            simulatedError = null
            Result.failure(error)
        } ?: Result.success(textProvider())
    }
}

/**
 * Fake implementation of an embedding generator for testing.
 *
 * Generates deterministic embeddings based on input hashing.
 *
 * Usage:
 * ```kotlin
 * val fakeGenerator = FakeEmbeddingGenerator()
 * val embedding = fakeGenerator.generateFromText("hello")
 * // Same input always produces same output
 * assertThat(fakeGenerator.generateFromText("hello")).isEqualTo(embedding)
 * ```
 */
class FakeEmbeddingGenerator(
    private val dimensions: Int = 128,
) {
    private var presetEmbeddings = mutableMapOf<String, FloatArray>()
    private var simulatedError: Throwable? = null
    private var simulatedDelay: Long = 0L
    private var generateCount: Int = 0

    // ============ Configuration ============

    /**
     * Sets a preset embedding for a specific input.
     */
    fun setEmbeddingForInput(
        input: String,
        embedding: FloatArray,
    ) {
        presetEmbeddings[input] = embedding
    }

    /**
     * Configures the generator to throw an error on the next call.
     */
    fun setError(error: Throwable?) {
        simulatedError = error
    }

    /**
     * Configures an artificial delay for generation calls.
     */
    fun setDelay(delayMs: Long) {
        simulatedDelay = delayMs
    }

    // ============ Generation Methods ============

    /**
     * Generates an embedding from text input.
     * Uses deterministic hashing to ensure same input produces same output.
     */
    suspend fun generateFromText(text: String): Result<FloatArray> {
        return executeGeneration {
            presetEmbeddings[text] ?: generateDeterministicEmbedding(text)
        }
    }

    /**
     * Generates an embedding from a bitmap.
     * Uses bitmap dimensions for deterministic output.
     */
    suspend fun generateFromImage(bitmap: Bitmap): Result<FloatArray> {
        return executeGeneration {
            val key = "image_${bitmap.width}_${bitmap.height}"
            presetEmbeddings[key] ?: generateDeterministicEmbedding(key)
        }
    }

    /**
     * Gets the number of generation calls made.
     */
    fun getGenerateCount(): Int = generateCount

    // ============ Test Helpers ============

    /**
     * Resets the generator to its initial state.
     */
    fun reset() {
        presetEmbeddings.clear()
        simulatedError = null
        simulatedDelay = 0L
        generateCount = 0
    }

    /**
     * Creates a zero embedding for testing.
     */
    fun createZeroEmbedding(): FloatArray = FloatArray(dimensions) { 0f }

    /**
     * Creates a ones embedding for testing.
     */
    fun createOnesEmbedding(): FloatArray = FloatArray(dimensions) { 1f }

    // ============ Internal ============

    private suspend fun executeGeneration(embeddingProvider: () -> FloatArray): Result<FloatArray> {
        generateCount++

        if (simulatedDelay > 0) {
            delay(simulatedDelay)
        }

        simulatedError?.let { error ->
            simulatedError = null
            return Result.failure(error)
        }

        return Result.success(embeddingProvider())
    }

    /**
     * Generates a deterministic embedding based on input hash.
     * Same input always produces the same output.
     */
    private fun generateDeterministicEmbedding(input: String): FloatArray {
        val hash = input.hashCode()
        val random = java.util.Random(hash.toLong())
        val embedding = FloatArray(dimensions) { random.nextFloat() * 2 - 1 }

        // Normalize to unit vector
        val magnitude = kotlin.math.sqrt(embedding.map { it * it }.sum())
        return if (magnitude > 0) {
            embedding.map { it / magnitude }.toFloatArray()
        } else {
            embedding
        }
    }
}

/**
 * Fake implementation of a semantic search engine for testing.
 *
 * Allows configuration of search results and similarity scores.
 *
 * Usage:
 * ```kotlin
 * val fakeSearch = FakeSemanticSearchEngine()
 * fakeSearch.setSearchResults(listOf(searchResult1, searchResult2))
 *
 * val results = fakeSearch.findSimilar(queryEmbedding, candidates)
 * assertThat(results).hasSize(2)
 * ```
 */
class FakeSemanticSearchEngine {
    private var presetResults: List<Pair<Any, Float>>? = null
    private var simulatedError: Throwable? = null
    private var simulatedDelay: Long = 0L
    private var searchCount: Int = 0

    // ============ Configuration ============

    /**
     * Sets preset search results as pairs of (item, similarity score).
     */
    fun setSearchResults(results: List<Pair<Any, Float>>) {
        presetResults = results
    }

    /**
     * Configures the engine to throw an error on the next call.
     */
    fun setError(error: Throwable?) {
        simulatedError = error
    }

    /**
     * Configures an artificial delay for search calls.
     */
    fun setDelay(delayMs: Long) {
        simulatedDelay = delayMs
    }

    // ============ Search Methods ============

    /**
     * Finds items similar to the query embedding.
     *
     * @param queryEmbedding The embedding to search for.
     * @param candidates List of candidate items with their embeddings.
     * @param topK Maximum number of results.
     * @param threshold Minimum similarity score.
     * @return List of pairs (item, similarity score) sorted by score descending.
     */
    suspend fun <T> findSimilar(
        queryEmbedding: FloatArray,
        candidates: List<Pair<T, FloatArray>>,
        topK: Int = 10,
        threshold: Float = 0.5f,
    ): Result<List<Pair<T, Float>>> {
        searchCount++

        if (simulatedDelay > 0) {
            delay(simulatedDelay)
        }

        simulatedError?.let { error ->
            simulatedError = null
            return Result.failure(error)
        }

        // Use preset results if available, otherwise calculate actual cosine similarity
        @Suppress("UNCHECKED_CAST")
        val resultList = presetResults?.let { preset ->
            preset.take(topK) as List<Pair<T, Float>>
        } ?: candidates.map { (item, embedding) ->
            val similarity = cosineSimilarity(queryEmbedding, embedding)
            item to similarity
        }
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
            .take(topK)

        return Result.success(resultList)
    }

    /**
     * Calculates cosine similarity between two embeddings.
     */
    fun cosineSimilarity(
        a: FloatArray,
        b: FloatArray,
    ): Float {
        require(a.size == b.size) { "Embeddings must have the same dimensions" }

        var dotProduct = 0f
        var magnitudeA = 0f
        var magnitudeB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            magnitudeA += a[i] * a[i]
            magnitudeB += b[i] * b[i]
        }

        val magnitude = kotlin.math.sqrt(magnitudeA) * kotlin.math.sqrt(magnitudeB)
        return if (magnitude > 0) dotProduct / magnitude else 0f
    }

    /**
     * Gets the number of search calls made.
     */
    fun getSearchCount(): Int = searchCount

    // ============ Test Helpers ============

    /**
     * Resets the engine to its initial state.
     */
    fun reset() {
        presetResults = null
        simulatedError = null
        simulatedDelay = 0L
        searchCount = 0
    }
}
