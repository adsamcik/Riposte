package com.adsamcik.riposte.core.ml

import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.Meme
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import kotlin.math.sqrt

class DefaultSemanticSearchEngineTest {

    @MockK
    private lateinit var mockEmbeddingGenerator: EmbeddingGenerator

    private lateinit var searchEngine: DefaultSemanticSearchEngine

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        searchEngine = DefaultSemanticSearchEngine(mockEmbeddingGenerator)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Cosine Similarity Tests ====================

    @Test
    fun `cosineSimilarity returns 1 for identical vectors`() {
        val embedding = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        
        val similarity = searchEngine.cosineSimilarity(embedding, embedding)
        
        assertThat(similarity).isWithin(0.0001f).of(1.0f)
    }

    @Test
    fun `cosineSimilarity returns -1 for opposite vectors`() {
        val embedding1 = floatArrayOf(1f, 2f, 3f)
        val embedding2 = floatArrayOf(-1f, -2f, -3f)
        
        val similarity = searchEngine.cosineSimilarity(embedding1, embedding2)
        
        assertThat(similarity).isWithin(0.0001f).of(-1.0f)
    }

    @Test
    fun `cosineSimilarity returns 0 for orthogonal vectors`() {
        val embedding1 = floatArrayOf(1f, 0f, 0f)
        val embedding2 = floatArrayOf(0f, 1f, 0f)
        
        val similarity = searchEngine.cosineSimilarity(embedding1, embedding2)
        
        assertThat(similarity).isWithin(0.0001f).of(0f)
    }

    @Test
    fun `cosineSimilarity handles normalized vectors correctly`() {
        // Two normalized vectors at 45 degrees
        val norm = sqrt(2f)
        val embedding1 = floatArrayOf(1f / norm, 1f / norm, 0f)
        val embedding2 = floatArrayOf(1f, 0f, 0f)
        
        val similarity = searchEngine.cosineSimilarity(embedding1, embedding2)
        
        // cos(45°) ≈ 0.707
        assertThat(similarity).isWithin(0.001f).of(0.707f)
    }

    @Test
    fun `cosineSimilarity returns 0 for zero vectors`() {
        val zeroVector = floatArrayOf(0f, 0f, 0f)
        val normalVector = floatArrayOf(1f, 2f, 3f)
        
        val similarity = searchEngine.cosineSimilarity(zeroVector, normalVector)
        
        assertThat(similarity).isEqualTo(0f)
    }

    @Test
    fun `cosineSimilarity returns 0 when both vectors are zero`() {
        val zeroVector1 = floatArrayOf(0f, 0f, 0f)
        val zeroVector2 = floatArrayOf(0f, 0f, 0f)
        
        val similarity = searchEngine.cosineSimilarity(zeroVector1, zeroVector2)
        
        assertThat(similarity).isEqualTo(0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cosineSimilarity throws when dimensions do not match`() {
        val embedding1 = floatArrayOf(1f, 2f, 3f)
        val embedding2 = floatArrayOf(1f, 2f)
        
        searchEngine.cosineSimilarity(embedding1, embedding2)
    }

    @Test
    fun `cosineSimilarity works with large dimension vectors`() {
        val embedding1 = FloatArray(128) { 1f }
        val embedding2 = FloatArray(128) { 1f }
        
        val similarity = searchEngine.cosineSimilarity(embedding1, embedding2)
        
        assertThat(similarity).isWithin(0.0001f).of(1.0f)
    }

    @Test
    fun `cosineSimilarity handles negative values correctly`() {
        val embedding1 = floatArrayOf(-1f, -2f, -3f)
        val embedding2 = floatArrayOf(1f, 2f, 3f)
        
        val similarity = searchEngine.cosineSimilarity(embedding1, embedding2)
        
        assertThat(similarity).isWithin(0.0001f).of(-1.0f)
    }

    // ==================== Find Similar Tests ====================

    @Test
    fun `findSimilar returns empty list when candidates is empty`() = runTest {
        coEvery { mockEmbeddingGenerator.generateFromText(any()) } returns FloatArray(128)
        
        val results = searchEngine.findSimilar(
            query = "test query",
            candidates = emptyList()
        )
        
        assertThat(results).isEmpty()
    }

    @Test
    fun `findSimilar returns results sorted by relevance descending`() = runTest {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockEmbeddingGenerator.generateFromText("test") } returns queryEmbedding
        
        // Create candidates with different similarities
        val candidates = listOf(
            createMemeWithEmbedding(1L, floatArrayOf(0.5f, 0.5f, 0f)), // ~0.707 similarity
            createMemeWithEmbedding(2L, floatArrayOf(1f, 0f, 0f)),     // 1.0 similarity
            createMemeWithEmbedding(3L, floatArrayOf(0.8f, 0.2f, 0f))  // ~0.97 similarity
        )
        
        val results = searchEngine.findSimilar(
            query = "test",
            candidates = candidates,
            threshold = 0f
        )
        
        // Should be sorted by relevance: 2 > 3 > 1
        assertThat(results.map { it.meme.id }).containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test
    fun `findSimilar filters results below threshold`() = runTest {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockEmbeddingGenerator.generateFromText("test") } returns queryEmbedding
        
        val candidates = listOf(
            createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)),       // 1.0 similarity
            createMemeWithEmbedding(2L, floatArrayOf(0.9f, 0.1f, 0f)),   // ~0.99 similarity
            createMemeWithEmbedding(3L, floatArrayOf(0.5f, 0.5f, 0f)),   // ~0.71 similarity
            createMemeWithEmbedding(4L, floatArrayOf(0.3f, 0.7f, 0f))    // ~0.39 similarity
        )
        
        val results = searchEngine.findSimilar(
            query = "test",
            candidates = candidates,
            threshold = 0.7f
        )
        
        // Only IDs 1, 2, 3 should pass the 0.7 threshold
        assertThat(results.map { it.meme.id }).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `findSimilar respects limit parameter`() = runTest {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockEmbeddingGenerator.generateFromText("test") } returns queryEmbedding
        
        val candidates = (1..10).map { id ->
            createMemeWithEmbedding(id.toLong(), floatArrayOf(1f, 0f, 0f))
        }
        
        val results = searchEngine.findSimilar(
            query = "test",
            candidates = candidates,
            limit = 3,
            threshold = 0f
        )
        
        assertThat(results).hasSize(3)
    }

    @Test
    fun `findSimilar returns top results when limit is less than candidates`() = runTest {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockEmbeddingGenerator.generateFromText("test") } returns queryEmbedding
        
        val candidates = listOf(
            createMemeWithEmbedding(1L, floatArrayOf(0.5f, 0.5f, 0f)),  // Lower similarity
            createMemeWithEmbedding(2L, floatArrayOf(1f, 0f, 0f)),      // Highest similarity
            createMemeWithEmbedding(3L, floatArrayOf(0.8f, 0.2f, 0f)),  // Medium similarity
            createMemeWithEmbedding(4L, floatArrayOf(0.9f, 0.1f, 0f))   // High similarity
        )
        
        val results = searchEngine.findSimilar(
            query = "test",
            candidates = candidates,
            limit = 2,
            threshold = 0f
        )
        
        // Should return top 2: IDs 2 and 4 (highest similarities)
        assertThat(results).hasSize(2)
        assertThat(results[0].meme.id).isEqualTo(2L)
        assertThat(results[1].meme.id).isEqualTo(4L)
    }

    @Test
    fun `findSimilar sets match type to SEMANTIC`() = runTest {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockEmbeddingGenerator.generateFromText("test") } returns queryEmbedding
        
        val candidates = listOf(
            createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f))
        )
        
        val results = searchEngine.findSimilar(
            query = "test",
            candidates = candidates,
            threshold = 0f
        )
        
        assertThat(results).hasSize(1)
        assertThat(results[0].matchType).isEqualTo(MatchType.SEMANTIC)
    }

    @Test
    fun `findSimilar calculates correct relevance score`() = runTest {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockEmbeddingGenerator.generateFromText("test") } returns queryEmbedding
        
        val candidates = listOf(
            createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f))  // Perfect match
        )
        
        val results = searchEngine.findSimilar(
            query = "test",
            candidates = candidates,
            threshold = 0f
        )
        
        assertThat(results[0].relevanceScore).isWithin(0.0001f).of(1.0f)
    }

    @Test
    fun `findSimilar uses default limit of 20`() = runTest {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockEmbeddingGenerator.generateFromText("test") } returns queryEmbedding
        
        val candidates = (1..50).map { id ->
            createMemeWithEmbedding(id.toLong(), floatArrayOf(1f, 0f, 0f))
        }
        
        val results = searchEngine.findSimilar(
            query = "test",
            candidates = candidates,
            threshold = 0f
        )
        
        assertThat(results).hasSize(20)
    }

    @Test
    fun `findSimilar uses default threshold of 0_3`() = runTest {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockEmbeddingGenerator.generateFromText("test") } returns queryEmbedding
        
        val candidates = listOf(
            createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)),       // 1.0 similarity
            createMemeWithEmbedding(2L, floatArrayOf(0.4f, 0.6f, 0f)),   // ~0.55 similarity (above 0.3)
            createMemeWithEmbedding(3L, floatArrayOf(0.2f, 0.8f, 0f))    // ~0.24 similarity (below 0.3)
        )
        
        val results = searchEngine.findSimilar(
            query = "test",
            candidates = candidates
            // Using default threshold of 0.3
        )
        
        // ID 3 should be filtered out (similarity < 0.3)
        assertThat(results.map { it.meme.id }).doesNotContain(3L)
    }

    @Test
    fun `findSimilar handles high dimensional embeddings`() = runTest {
        val dimension = 128
        val queryEmbedding = FloatArray(dimension) { 1f / sqrt(dimension.toFloat()) }
        coEvery { mockEmbeddingGenerator.generateFromText("test") } returns queryEmbedding
        
        val candidates = listOf(
            createMemeWithEmbedding(1L, FloatArray(dimension) { 1f / sqrt(dimension.toFloat()) })
        )
        
        val results = searchEngine.findSimilar(
            query = "test",
            candidates = candidates,
            threshold = 0f
        )
        
        assertThat(results).hasSize(1)
        assertThat(results[0].relevanceScore).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun `findSimilar excludes all results when threshold is too high`() = runTest {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockEmbeddingGenerator.generateFromText("test") } returns queryEmbedding
        
        val candidates = listOf(
            createMemeWithEmbedding(1L, floatArrayOf(0.5f, 0.5f, 0f))  // ~0.71 similarity
        )
        
        val results = searchEngine.findSimilar(
            query = "test",
            candidates = candidates,
            threshold = 0.8f
        )
        
        assertThat(results).isEmpty()
    }

    @Test
    fun `findSimilar handles negative similarity scores`() = runTest {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockEmbeddingGenerator.generateFromText("test") } returns queryEmbedding
        
        val candidates = listOf(
            createMemeWithEmbedding(1L, floatArrayOf(-1f, 0f, 0f)),     // -1.0 similarity
            createMemeWithEmbedding(2L, floatArrayOf(0f, 1f, 0f)),      // 0 similarity
            createMemeWithEmbedding(3L, floatArrayOf(1f, 0f, 0f))       // 1.0 similarity
        )
        
        val results = searchEngine.findSimilar(
            query = "test",
            candidates = candidates,
            threshold = 0.5f
        )
        
        // Only ID 3 should pass threshold of 0.5
        assertThat(results).hasSize(1)
        assertThat(results[0].meme.id).isEqualTo(3L)
    }

    // ==================== State and Lifecycle Tests ====================

    @Test
    fun `findSimilar returns empty list when UnsatisfiedLinkError is thrown`() = runTest {
        coEvery { mockEmbeddingGenerator.generateFromText("test") } throws
            UnsatisfiedLinkError("libgemma_embedding_model_jni.so not found")

        val candidates = listOf(
            createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)),
        )

        val results = searchEngine.findSimilar(
            query = "test",
            candidates = candidates,
            threshold = 0f,
        )

        assertThat(results).isEmpty()
    }

    @Test
    fun `findSimilar returns empty list when ExceptionInInitializerError is thrown`() = runTest {
        coEvery { mockEmbeddingGenerator.generateFromText("test") } throws
            ExceptionInInitializerError(UnsatisfiedLinkError("libgemma_embedding_model_jni.so not found"))

        val candidates = listOf(
            createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)),
        )

        val results = searchEngine.findSimilar(
            query = "test",
            candidates = candidates,
            threshold = 0f,
        )

        assertThat(results).isEmpty()
    }

    @Test
    fun `findSimilar returns empty list when general exception is thrown during embedding`() = runTest {
        coEvery { mockEmbeddingGenerator.generateFromText("test") } throws
            RuntimeException("Model initialization failed")

        val candidates = listOf(
            createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)),
        )

        val results = searchEngine.findSimilar(
            query = "test",
            candidates = candidates,
            threshold = 0f,
        )

        assertThat(results).isEmpty()
    }

    @Test
    fun `isReady delegates to embedding generator`() = runTest {
        coEvery { mockEmbeddingGenerator.isReady() } returns true
        
        val result = searchEngine.isReady()
        
        assertThat(result).isTrue()
        coVerify { mockEmbeddingGenerator.isReady() }
    }

    @Test
    fun `isReady returns false when generator is not ready`() = runTest {
        coEvery { mockEmbeddingGenerator.isReady() } returns false
        
        val result = searchEngine.isReady()
        
        assertThat(result).isFalse()
    }

    @Test
    fun `initialize delegates to embedding generator`() = runTest {
        coEvery { mockEmbeddingGenerator.initialize() } just Runs
        
        searchEngine.initialize()
        
        coVerify { mockEmbeddingGenerator.initialize() }
    }

    @Test
    fun `close delegates to embedding generator`() {
        every { mockEmbeddingGenerator.close() } just Runs
        
        searchEngine.close()
        
        verify { mockEmbeddingGenerator.close() }
    }

    // ==================== Helper Functions ====================

    private fun createMemeWithEmbedding(id: Long, embedding: FloatArray): MemeWithEmbedding {
        val meme = Meme(
            id = id,
            filePath = "/test/path/$id.jpg",
            fileName = "test_$id.jpg",
            mimeType = "image/jpeg",
            width = 100,
            height = 100,
            fileSizeBytes = 1000,
            importedAt = System.currentTimeMillis(),
            emojiTags = listOf(EmojiTag("😂", "face_with_tears_of_joy"))
        )
        return MemeWithEmbedding(meme, embedding)
    }
}
