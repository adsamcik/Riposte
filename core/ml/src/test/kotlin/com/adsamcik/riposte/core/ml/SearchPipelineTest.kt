package com.adsamcik.riposte.core.ml

import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.Meme
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt

/**
 * Full search pipeline end-to-end tests with mocked [EmbeddingGenerator]
 * but realistic data flow through [DefaultSemanticSearchEngine].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SearchPipelineTest {

    @MockK
    private lateinit var mockGenerator: EmbeddingGenerator

    private lateinit var engine: DefaultSemanticSearchEngine

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        engine = DefaultSemanticSearchEngine(mockGenerator)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Full Pipeline Tests ====================

    @Test
    fun `full pipeline returns results sorted by relevance`() = runTest {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f, 0f)
        coEvery { mockGenerator.generateFromQuery("cat meme") } returns queryEmbedding

        val candidates = listOf(
            memeWithEmbedding(1L, floatArrayOf(0.3f, 0.7f, 0f, 0f)),  // low similarity
            memeWithEmbedding(2L, floatArrayOf(1f, 0f, 0f, 0f)),      // perfect match
            memeWithEmbedding(3L, floatArrayOf(0.9f, 0.1f, 0f, 0f)),  // high similarity
            memeWithEmbedding(4L, floatArrayOf(0.6f, 0.4f, 0f, 0f)),  // medium similarity
        )

        val results = engine.findSimilar("cat meme", candidates, threshold = 0f)

        assertThat(results.map { it.meme.id }).containsExactly(2L, 3L, 4L, 1L).inOrder()
    }

    @Test
    fun `full pipeline threshold filtering removes low-similarity results`() = runTest {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockGenerator.generateFromQuery("dog") } returns queryEmbedding

        val candidates = listOf(
            memeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)),          // 1.0
            memeWithEmbedding(2L, floatArrayOf(0.5f, 0.5f, 0f)),     // ~0.71
            memeWithEmbedding(3L, floatArrayOf(0f, 1f, 0f)),          // 0.0
            memeWithEmbedding(4L, floatArrayOf(0.1f, 0.9f, 0f)),     // ~0.11
        )

        val results = engine.findSimilar("dog", candidates, threshold = 0.5f)

        assertThat(results.map { it.meme.id }).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `full pipeline respects limit parameter`() = runTest {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockGenerator.generateFromQuery("query") } returns queryEmbedding

        val candidates = (1L..20L).map { id ->
            memeWithEmbedding(id, floatArrayOf(1f, 0f, 0f))
        }

        val results = engine.findSimilar("query", candidates, limit = 5, threshold = 0f)

        assertThat(results).hasSize(5)
    }

    @Test
    fun `full pipeline multi-vector max-pooling picks highest slot`() = runTest {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockGenerator.generateFromQuery("funny") } returns queryEmbedding

        val candidates = listOf(
            MemeWithEmbeddings(
                meme = testMeme(1L),
                embeddings = mapOf(
                    "content" to floatArrayOf(0f, 1f, 0f),     // 0.0 similarity
                    "intent" to floatArrayOf(0.9f, 0.1f, 0f),  // ~0.99 similarity
                ),
            ),
            MemeWithEmbeddings(
                meme = testMeme(2L),
                embeddings = mapOf(
                    "content" to floatArrayOf(0.5f, 0.5f, 0f), // ~0.71 similarity
                    "intent" to floatArrayOf(0f, 0f, 1f),       // 0.0 similarity
                ),
            ),
        )

        val results = engine.findSimilarMultiVector("funny", candidates, threshold = 0f)

        // Meme 1 wins (max=~0.99 from intent), meme 2 second (max=~0.71 from content)
        assertThat(results).hasSize(2)
        assertThat(results[0].meme.id).isEqualTo(1L)
        assertThat(results[1].meme.id).isEqualTo(2L)
        assertThat(results[0].relevanceScore).isGreaterThan(results[1].relevanceScore)
    }

    @Test
    fun `full pipeline empty candidates returns empty results`() = runTest {
        coEvery { mockGenerator.generateFromQuery(any()) } returns floatArrayOf(1f, 0f, 0f)

        val results = engine.findSimilar("anything", emptyList())

        assertThat(results).isEmpty()
    }

    @Test
    fun `full pipeline same text produces high similarity`() = runTest {
        val embedding = floatArrayOf(0.5f, 0.3f, 0.2f, 0.8f)
        coEvery { mockGenerator.generateFromQuery("identical") } returns embedding

        val candidates = listOf(memeWithEmbedding(1L, embedding.copyOf()))

        val results = engine.findSimilar("identical", candidates, threshold = 0f)

        assertThat(results).hasSize(1)
        assertThat(results[0].relevanceScore).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun `full pipeline unrelated text produces low similarity`() = runTest {
        coEvery { mockGenerator.generateFromQuery("cats") } returns floatArrayOf(1f, 0f, 0f, 0f)

        // Orthogonal embedding = 0 similarity
        val candidates = listOf(memeWithEmbedding(1L, floatArrayOf(0f, 0f, 0f, 1f)))

        val results = engine.findSimilar("cats", candidates, threshold = 0f)

        assertThat(results).hasSize(1)
        assertThat(results[0].relevanceScore).isWithin(0.001f).of(0f)
    }

    @Test
    fun `full pipeline all results have SEMANTIC match type`() = runTest {
        coEvery { mockGenerator.generateFromQuery("test") } returns floatArrayOf(1f, 0f, 0f)

        val candidates = listOf(
            memeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)),
            memeWithEmbedding(2L, floatArrayOf(0.5f, 0.5f, 0f)),
        )

        val results = engine.findSimilar("test", candidates, threshold = 0f)

        results.forEach { result ->
            assertThat(result.matchType).isEqualTo(MatchType.SEMANTIC)
        }
    }

    // ==================== Query Embedding Cache Tests ====================

    @Test
    fun `query embedding cache reuses cached embedding for same query`() = runTest {
        val embedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockGenerator.generateFromQuery("cached query") } returns embedding

        val candidates = listOf(memeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)))

        engine.findSimilar("cached query", candidates, threshold = 0f)
        engine.findSimilar("cached query", candidates, threshold = 0f)
        engine.findSimilar("cached query", candidates, threshold = 0f)

        coVerify(exactly = 1) { mockGenerator.generateFromQuery("cached query") }
    }

    @Test
    fun `query embedding cache generates new embedding for different query`() = runTest {
        coEvery { mockGenerator.generateFromQuery("query A") } returns floatArrayOf(1f, 0f, 0f)
        coEvery { mockGenerator.generateFromQuery("query B") } returns floatArrayOf(0f, 1f, 0f)

        val candidates = listOf(memeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)))

        engine.findSimilar("query A", candidates, threshold = 0f)
        engine.findSimilar("query B", candidates, threshold = 0f)

        coVerify(exactly = 1) { mockGenerator.generateFromQuery("query A") }
        coVerify(exactly = 1) { mockGenerator.generateFromQuery("query B") }
    }

    @Test
    fun `clearCache forces re-generation of previously cached query`() = runTest {
        val embedding = floatArrayOf(1f, 0f, 0f)
        coEvery { mockGenerator.generateFromQuery("query") } returns embedding

        val candidates = listOf(memeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)))

        engine.findSimilar("query", candidates, threshold = 0f)
        engine.clearCache()
        engine.findSimilar("query", candidates, threshold = 0f)

        coVerify(exactly = 2) { mockGenerator.generateFromQuery("query") }
    }

    // ==================== L2-Normalized Input Tests ====================

    @Test
    fun `L2-normalized vectors have cosine similarity equal to dot product`() {
        val v1 = EmbeddingUtils.normalize(floatArrayOf(3f, 4f, 0f))
        val v2 = EmbeddingUtils.normalize(floatArrayOf(1f, 2f, 2f))

        val cosineSim = engine.cosineSimilarity(v1, v2)

        // For normalized vectors, cosine similarity = dot product
        var dotProduct = 0f
        for (i in v1.indices) dotProduct += v1[i] * v2[i]

        assertThat(cosineSim).isWithin(0.0001f).of(dotProduct)
    }

    @Test
    fun `L2-normalized identical vectors produce similarity of 1`() {
        val v = EmbeddingUtils.normalize(floatArrayOf(7f, 3f, 1f, 5f))

        val similarity = engine.cosineSimilarity(v, v)

        assertThat(similarity).isWithin(0.0001f).of(1.0f)
    }

    // ==================== Matryoshka Truncation in Pipeline ====================

    @Test
    fun `Matryoshka truncation from 768d to 256d preserves similarity ordering`() = runTest {
        // Create 768d embeddings with known structure
        val embeddingA = FloatArray(768) { if (it < 256) 1f else 0f }
        val embeddingB = FloatArray(768) { if (it < 128) 1f else 0.5f }
        val embeddingC = FloatArray(768) { if (it % 2 == 0) 1f else -1f }
        val query = FloatArray(768) { if (it < 256) 1f else 0f }

        // Compute full-dimension ordering
        val simA = EmbeddingUtils.cosineSimilarity(query, embeddingA)
        val simB = EmbeddingUtils.cosineSimilarity(query, embeddingB)
        val simC = EmbeddingUtils.cosineSimilarity(query, embeddingC)

        // Truncate to 256d
        val truncA = EmbeddingUtils.truncateEmbedding(embeddingA, 256)
        val truncB = EmbeddingUtils.truncateEmbedding(embeddingB, 256)
        val truncC = EmbeddingUtils.truncateEmbedding(embeddingC, 256)
        val truncQ = EmbeddingUtils.truncateEmbedding(query, 256)

        val truncSimA = EmbeddingUtils.cosineSimilarity(truncQ, truncA)
        val truncSimB = EmbeddingUtils.cosineSimilarity(truncQ, truncB)
        val truncSimC = EmbeddingUtils.cosineSimilarity(truncQ, truncC)

        // The ranking should be preserved after truncation:
        // A is closest to query in both full and truncated space
        val fullOrder = listOf(simA to "A", simB to "B", simC to "C")
            .sortedByDescending { it.first }
            .map { it.second }
        val truncOrder = listOf(truncSimA to "A", truncSimB to "B", truncSimC to "C")
            .sortedByDescending { it.first }
            .map { it.second }

        assertThat(truncOrder[0]).isEqualTo(fullOrder[0])
    }

    @Test
    fun `truncated embeddings are re-normalized to unit length`() {
        val embedding = FloatArray(768) { it.toFloat() }
        val truncated = EmbeddingUtils.truncateEmbedding(embedding, 256)

        var sumSquares = 0f
        for (v in truncated) sumSquares += v * v

        assertThat(sqrt(sumSquares)).isWithin(0.001f).of(1.0f)
    }

    // ==================== Error Propagation ====================

    @Test
    fun `generator RuntimeException propagates through pipeline`() = runTest {
        coEvery { mockGenerator.generateFromQuery("fail") } throws
            RuntimeException("Model crashed")

        val candidates = listOf(memeWithEmbedding(1L, floatArrayOf(1f, 0f, 0f)))

        var caught: Throwable? = null
        try {
            engine.findSimilar("fail", candidates, threshold = 0f)
        } catch (e: RuntimeException) {
            caught = e
        }

        assertThat(caught).isNotNull()
        assertThat(caught).isInstanceOf(RuntimeException::class.java)
        assertThat(caught!!.message).isEqualTo("Model crashed")
    }

    @Test
    fun `generator exception in multi-vector search propagates`() = runTest {
        coEvery { mockGenerator.generateFromQuery("fail") } throws
            IllegalStateException("Not initialized")

        val candidates = listOf(
            MemeWithEmbeddings(
                meme = testMeme(1L),
                embeddings = mapOf("content" to floatArrayOf(1f, 0f, 0f)),
            ),
        )

        var caught: Throwable? = null
        try {
            engine.findSimilarMultiVector("fail", candidates, threshold = 0f)
        } catch (e: IllegalStateException) {
            caught = e
        }

        assertThat(caught).isNotNull()
        assertThat(caught!!.message).isEqualTo("Not initialized")
    }

    // ==================== Helpers ====================

    private fun testMeme(id: Long): Meme = Meme(
        id = id,
        filePath = "/test/$id.jpg",
        fileName = "test_$id.jpg",
        mimeType = "image/jpeg",
        width = 100,
        height = 100,
        fileSizeBytes = 1000,
        importedAt = 1000L,
        emojiTags = listOf(EmojiTag("😂", "face_with_tears_of_joy")),
    )

    private fun memeWithEmbedding(id: Long, embedding: FloatArray): MemeWithEmbedding =
        MemeWithEmbedding(testMeme(id), embedding)
}
