package com.adsamcik.riposte.core.ml

import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.Meme
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DefaultSemanticSearchEngineTest {
    @MockK
    private lateinit var mockEmbeddingGenerator: EmbeddingGenerator

    @MockK(relaxed = true)
    private lateinit var mockPersistentCache: PersistentQueryEmbeddingCache

    private lateinit var searchEngine: DefaultSemanticSearchEngine

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        // Explicitly stub persistent cache to return null (miss) so tests hit the generator
        coEvery { mockPersistentCache.get(any()) } returns null
        searchEngine = DefaultSemanticSearchEngine(mockEmbeddingGenerator, mockPersistentCache)
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
    fun `findSimilar returns empty list when candidates is empty`() =
        runTest {
            coEvery { mockEmbeddingGenerator.generateFromQuery(any()) } returns FloatArray(128)

            val results =
                searchEngine.findSimilar(
                    query = "test query",
                    candidates = emptyList(),
                )

            assertThat(results).isEmpty()
        }

    @Test
    fun `findSimilar returns results sorted by relevance descending`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            // Create candidates with different similarities
            val candidates =
                listOf(
                    // ~0.707 similarity
                    createMemeWithEmbedding(1L, floatArrayOf(0.5f, 0.5f, 0f)),
                    // 1.0 similarity
                    createMemeWithEmbedding(2L, floatArrayOf(1f, 0f, 0f)),
                    // ~0.97 similarity
                    createMemeWithEmbedding(3L, floatArrayOf(0.8f, 0.2f, 0f)),
                )

            val results =
                searchEngine.findSimilar(
                    query = "test",
                    candidates = candidates,
                    threshold = 0f,
                )

            // Should be sorted by relevance: 2 > 3 > 1
            assertThat(results.map { it.meme.id }).containsExactly(2L, 3L, 1L).inOrder()
        }

    @Test
    fun `findSimilar filters results below threshold`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            val candidates =
                listOf(
                    // 1.0 similarity
                    createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)),
                    // ~0.99 similarity
                    createMemeWithEmbedding(2L, floatArrayOf(0.9f, 0.1f, 0f)),
                    // ~0.71 similarity
                    createMemeWithEmbedding(3L, floatArrayOf(0.5f, 0.5f, 0f)),
                    // ~0.39 similarity
                    createMemeWithEmbedding(4L, floatArrayOf(0.3f, 0.7f, 0f)),
                )

            val results =
                searchEngine.findSimilar(
                    query = "test",
                    candidates = candidates,
                    threshold = 0.7f,
                )

            // Only IDs 1, 2, 3 should pass the 0.7 threshold
            assertThat(results.map { it.meme.id }).containsExactly(1L, 2L, 3L)
        }

    @Test
    fun `findSimilar respects limit parameter`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            val candidates =
                (1..10).map { id ->
                    createMemeWithEmbedding(id.toLong(), floatArrayOf(1f, 0f, 0f))
                }

            val results =
                searchEngine.findSimilar(
                    query = "test",
                    candidates = candidates,
                    limit = 3,
                    threshold = 0f,
                )

            assertThat(results).hasSize(3)
        }

    @Test
    fun `findSimilar returns top results when limit is less than candidates`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            val candidates =
                listOf(
                    // Lower similarity
                    createMemeWithEmbedding(1L, floatArrayOf(0.5f, 0.5f, 0f)),
                    // Highest similarity
                    createMemeWithEmbedding(2L, floatArrayOf(1f, 0f, 0f)),
                    // Medium similarity
                    createMemeWithEmbedding(3L, floatArrayOf(0.8f, 0.2f, 0f)),
                    // High similarity
                    createMemeWithEmbedding(4L, floatArrayOf(0.9f, 0.1f, 0f)),
                )

            val results =
                searchEngine.findSimilar(
                    query = "test",
                    candidates = candidates,
                    limit = 2,
                    threshold = 0f,
                )

            // Should return top 2: IDs 2 and 4 (highest similarities)
            assertThat(results).hasSize(2)
            assertThat(results[0].meme.id).isEqualTo(2L)
            assertThat(results[1].meme.id).isEqualTo(4L)
        }

    @Test
    fun `findSimilar sets match type to SEMANTIC`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            val candidates =
                listOf(
                    createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)),
                )

            val results =
                searchEngine.findSimilar(
                    query = "test",
                    candidates = candidates,
                    threshold = 0f,
                )

            assertThat(results).hasSize(1)
            assertThat(results[0].matchType).isEqualTo(MatchType.SEMANTIC)
        }

    @Test
    fun `findSimilar calculates correct relevance score`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            val candidates =
                listOf(
                    // Perfect match
                    createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)),
                )

            val results =
                searchEngine.findSimilar(
                    query = "test",
                    candidates = candidates,
                    threshold = 0f,
                )

            assertThat(results[0].relevanceScore).isWithin(0.0001f).of(1.0f)
        }

    @Test
    fun `findSimilar uses default limit of 20`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            val candidates =
                (1..50).map { id ->
                    createMemeWithEmbedding(id.toLong(), floatArrayOf(1f, 0f, 0f))
                }

            val results =
                searchEngine.findSimilar(
                    query = "test",
                    candidates = candidates,
                    threshold = 0f,
                )

            assertThat(results).hasSize(20)
        }

    @Test
    fun `findSimilar uses default threshold of 0_3`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            val candidates =
                listOf(
                    // 1.0 similarity
                    createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)),
                    // ~0.55 similarity (above 0.3)
                    createMemeWithEmbedding(2L, floatArrayOf(0.4f, 0.6f, 0f)),
                    // ~0.24 similarity (below 0.3)
                    createMemeWithEmbedding(3L, floatArrayOf(0.2f, 0.8f, 0f)),
                )

            val results =
                searchEngine.findSimilar(
                    query = "test",
                    candidates = candidates,
                    // Using default threshold of 0.3
                )

            // ID 3 should be filtered out (similarity < 0.3)
            assertThat(results.map { it.meme.id }).doesNotContain(3L)
        }

    @Test
    fun `findSimilar handles high dimensional embeddings`() =
        runTest {
            val dimension = 128
            val queryEmbedding = FloatArray(dimension) { 1f / sqrt(dimension.toFloat()) }
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            val candidates =
                listOf(
                    createMemeWithEmbedding(1L, FloatArray(dimension) { 1f / sqrt(dimension.toFloat()) }),
                )

            val results =
                searchEngine.findSimilar(
                    query = "test",
                    candidates = candidates,
                    threshold = 0f,
                )

            assertThat(results).hasSize(1)
            assertThat(results[0].relevanceScore).isWithin(0.001f).of(1.0f)
        }

    @Test
    fun `findSimilar excludes all results when threshold is too high`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            val candidates =
                listOf(
                    // ~0.71 similarity
                    createMemeWithEmbedding(1L, floatArrayOf(0.5f, 0.5f, 0f)),
                )

            val results =
                searchEngine.findSimilar(
                    query = "test",
                    candidates = candidates,
                    threshold = 0.8f,
                )

            assertThat(results).isEmpty()
        }

    @Test
    fun `findSimilar handles negative similarity scores`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            val candidates =
                listOf(
                    // -1.0 similarity
                    createMemeWithEmbedding(1L, floatArrayOf(-1f, 0f, 0f)),
                    // 0 similarity
                    createMemeWithEmbedding(2L, floatArrayOf(0f, 1f, 0f)),
                    // 1.0 similarity
                    createMemeWithEmbedding(3L, floatArrayOf(1f, 0f, 0f)),
                )

            val results =
                searchEngine.findSimilar(
                    query = "test",
                    candidates = candidates,
                    threshold = 0.5f,
                )

            // Only ID 3 should pass threshold of 0.5
            assertThat(results).hasSize(1)
            assertThat(results[0].meme.id).isEqualTo(3L)
        }

    // ==================== Find Similar Multi-Vector Tests ====================

    @Test
    fun `findSimilarMultiVector returns empty list when candidates is empty`() =
        runTest {
            coEvery { mockEmbeddingGenerator.generateFromQuery(any()) } returns FloatArray(3)

            val results =
                searchEngine.findSimilarMultiVector(
                    query = "test query",
                    candidates = emptyList(),
                )

            assertThat(results).isEmpty()
        }

    @Test
    fun `findSimilarMultiVector uses weighted fusion across slots`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            val candidates =
                listOf(
                    MemeWithEmbeddings(
                        meme = createTestMeme(1L),
                        embeddings =
                            mapOf(
                                // ~0.71 similarity
                                "content" to floatArrayOf(0.5f, 0.5f, 0f),
                                // 1.0 similarity
                                "intent" to floatArrayOf(1f, 0f, 0f),
                            ),
                    ),
                    MemeWithEmbeddings(
                        meme = createTestMeme(2L),
                        embeddings =
                            mapOf(
                                // ~0.97 similarity
                                "content" to floatArrayOf(0.8f, 0.2f, 0f),
                                // ~0.39 similarity
                                "intent" to floatArrayOf(0.3f, 0.7f, 0f),
                            ),
                    ),
                )

            val results =
                searchEngine.findSimilarMultiVector(
                    query = "test",
                    candidates = candidates,
                    threshold = 0f,
                )

            // Weighted fusion: content*0.35 + intent*0.45, normalized by total weight
            // Meme 1: (0.71*0.35 + 1.0*0.45) / 0.80 = (0.2485+0.45)/0.80 ≈ 0.873
            // Meme 2: (0.97*0.35 + 0.39*0.45) / 0.80 = (0.3395+0.1755)/0.80 ≈ 0.644
            // Meme 1 should still rank first
            assertThat(results).hasSize(2)
            assertThat(results[0].meme.id).isEqualTo(1L)
            assertThat(results[1].meme.id).isEqualTo(2L)
        }

    @Test
    fun `findSimilarMultiVector filters low-similarity results via dynamic threshold`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            // Create candidates with a wide spread so dynamic threshold filters the worst
            val candidates =
                listOf(
                    MemeWithEmbeddings(
                        meme = createTestMeme(1L),
                        embeddings = mapOf("content" to floatArrayOf(1f, 0f, 0f)),
                    ),
                    MemeWithEmbeddings(
                        meme = createTestMeme(2L),
                        embeddings = mapOf("content" to floatArrayOf(0.95f, 0.05f, 0f)),
                    ),
                    MemeWithEmbeddings(
                        meme = createTestMeme(3L),
                        embeddings = mapOf("content" to floatArrayOf(0.9f, 0.1f, 0f)),
                    ),
                    MemeWithEmbeddings(
                        meme = createTestMeme(4L),
                        // Very low similarity: ~0.16
                        embeddings = mapOf("content" to floatArrayOf(0.1f, 0.7f, 0.7f)),
                    ),
                )

            val results =
                searchEngine.findSimilarMultiVector(
                    query = "test",
                    candidates = candidates,
                    threshold = 0f,
                )

            // Dynamic threshold should filter out meme 4 (far below the cluster of ~1.0 similarity)
            // At minimum, the high-similarity memes should be present
            assertThat(results).isNotEmpty()
            assertThat(results.map { it.meme.id }).contains(1L)
            // Meme 4 should be filtered out due to dynamic threshold
            assertThat(results.map { it.meme.id }).doesNotContain(4L)
        }

    @Test
    fun `findSimilarMultiVector skips slots with mismatched dimensions`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            val candidates =
                listOf(
                    MemeWithEmbeddings(
                        meme = createTestMeme(1L),
                        embeddings =
                            mapOf(
                                // matching dimension
                                "content" to floatArrayOf(0.5f, 0.5f, 0f),
                                // wrong dimension, skipped
                                "intent" to floatArrayOf(1f, 0f, 0f, 0f, 0f),
                            ),
                    ),
                )

            val results =
                searchEngine.findSimilarMultiVector(
                    query = "test",
                    candidates = candidates,
                    threshold = 0f,
                )

            assertThat(results).hasSize(1)
            // Should use the content slot similarity (~0.71)
            assertThat(results[0].relevanceScore).isWithin(0.01f).of(0.707f)
        }

    @Test
    fun `findSimilarMultiVector sets match type to SEMANTIC`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } returns queryEmbedding

            val candidates =
                listOf(
                    MemeWithEmbeddings(
                        meme = createTestMeme(1L),
                        embeddings = mapOf("content" to floatArrayOf(1f, 0f, 0f)),
                    ),
                )

            val results =
                searchEngine.findSimilarMultiVector(
                    query = "test",
                    candidates = candidates,
                    threshold = 0f,
                )

            assertThat(results[0].matchType).isEqualTo(MatchType.SEMANTIC)
        }

    // ==================== State and Lifecycle Tests ====================

    @Test
    fun `findSimilar propagates UnsatisfiedLinkError`() =
        runTest {
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } throws
                UnsatisfiedLinkError("libgemma_embedding_model_jni.so not found")

            val candidates =
                listOf(
                    createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)),
                )

            var caughtError: Throwable? = null
            try {
                searchEngine.findSimilar(
                    query = "test",
                    candidates = candidates,
                    threshold = 0f,
                )
            } catch (e: UnsatisfiedLinkError) {
                caughtError = e
            }

            assertThat(caughtError).isInstanceOf(UnsatisfiedLinkError::class.java)
        }

    @Test
    fun `findSimilar propagates ExceptionInInitializerError`() =
        runTest {
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } throws
                ExceptionInInitializerError(UnsatisfiedLinkError("libgemma_embedding_model_jni.so not found"))

            val candidates =
                listOf(
                    createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)),
                )

            var caughtError: Throwable? = null
            try {
                searchEngine.findSimilar(
                    query = "test",
                    candidates = candidates,
                    threshold = 0f,
                )
            } catch (e: ExceptionInInitializerError) {
                caughtError = e
            }

            assertThat(caughtError).isInstanceOf(ExceptionInInitializerError::class.java)
        }

    @Test
    fun `findSimilar propagates general exception during embedding`() =
        runTest {
            coEvery { mockEmbeddingGenerator.generateFromQuery("test") } throws
                RuntimeException("Model initialization failed")

            val candidates =
                listOf(
                    createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)),
                )

            var caughtError: Throwable? = null
            try {
                searchEngine.findSimilar(
                    query = "test",
                    candidates = candidates,
                    threshold = 0f,
                )
            } catch (e: RuntimeException) {
                caughtError = e
            }

            assertThat(caughtError).isInstanceOf(RuntimeException::class.java)
        }

    @Test
    fun `isReady delegates to embedding generator`() =
        runTest {
            coEvery { mockEmbeddingGenerator.isReady() } returns true

            val result = searchEngine.isReady()

            assertThat(result).isTrue()
            coVerify { mockEmbeddingGenerator.isReady() }
        }

    @Test
    fun `isReady returns false when generator is not ready`() =
        runTest {
            coEvery { mockEmbeddingGenerator.isReady() } returns false

            val result = searchEngine.isReady()

            assertThat(result).isFalse()
        }

    @Test
    fun `initialize delegates to embedding generator`() =
        runTest {
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

    // ==================== Cache Invalidation Tests ====================

    @Test
    fun `query cache is invalidated when model version changes`() =
        runTest {
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            val candidates =
                listOf(createMemeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)))

            // Phase 1: First query — generates and caches embedding
            every { mockEmbeddingGenerator.modelVersion } returns "v1.0"
            coEvery { mockEmbeddingGenerator.generateFromQuery("cats") } returns queryEmbedding

            val results1 = searchEngine.findSimilar("cats", candidates)
            assertThat(results1).hasSize(1)
            coVerify(exactly = 1) { mockEmbeddingGenerator.generateFromQuery("cats") }

            // Phase 2: Same query, same model version — uses cache, no re-generation
            val results2 = searchEngine.findSimilar("cats", candidates)
            assertThat(results2).hasSize(1)
            coVerify(exactly = 1) { mockEmbeddingGenerator.generateFromQuery("cats") }

            // Phase 3: Model version changes — cache should be invalidated
            every { mockEmbeddingGenerator.modelVersion } returns "v2.0"
            coEvery { mockEmbeddingGenerator.generateFromQuery("cats") } returns queryEmbedding

            val results3 = searchEngine.findSimilar("cats", candidates)
            assertThat(results3).hasSize(1)
            coVerify(exactly = 2) { mockEmbeddingGenerator.generateFromQuery("cats") }
        }

    // ==================== Helper Functions ====================

    private fun createTestMeme(id: Long): Meme {
        return Meme(
            id = id,
            filePath = "/test/path/$id.jpg",
            fileName = "test_$id.jpg",
            mimeType = "image/jpeg",
            width = 100,
            height = 100,
            fileSizeBytes = 1000,
            importedAt = System.currentTimeMillis(),
            emojiTags = listOf(EmojiTag("😂", "face_with_tears_of_joy")),
        )
    }

    private fun createMemeWithEmbedding(
        id: Long,
        embedding: FloatArray,
    ): MemeWithEmbedding {
        val meme = createTestMeme(id)
        return MemeWithEmbedding(meme, embedding)
    }
}
