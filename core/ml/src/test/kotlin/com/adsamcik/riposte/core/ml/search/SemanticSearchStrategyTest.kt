package com.adsamcik.riposte.core.ml.search

import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.database.entity.MemeWithEmbeddingData
import com.adsamcik.riposte.core.ml.DeviceTierDetector
import com.adsamcik.riposte.core.ml.MemeWithEmbeddings
import com.adsamcik.riposte.core.ml.SemanticSearchEngine
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.SearchResult
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SemanticSearchStrategyTest {

    @MockK
    private lateinit var semanticSearchEngine: SemanticSearchEngine

    @MockK
    private lateinit var memeEmbeddingDao: MemeEmbeddingDao

    @MockK
    private lateinit var deviceTierDetector: DeviceTierDetector

    private lateinit var strategy: SemanticSearchStrategy

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        strategy = SemanticSearchStrategy(semanticSearchEngine, memeEmbeddingDao, deviceTierDetector)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // region Basic behavior

    @Test
    fun `name is semantic`() {
        assertThat(strategy.name).isEqualTo("semantic")
    }

    @Test
    fun `priority is 200`() {
        assertThat(strategy.priority).isEqualTo(200)
    }

    @Test
    fun `isAvailable returns true`() {
        assertThat(strategy.isAvailable()).isTrue()
    }

    // endregion

    // region Engine delegation

    @Test
    fun `search returns empty when engine not ready`() = runTest {
        coEvery { semanticSearchEngine.isReady() } returns false

        val results = strategy.search("funny cat", 10)

        assertThat(results).isEmpty()
        coVerify(exactly = 0) { memeEmbeddingDao.getMemesWithEmbeddings() }
    }

    @Test
    fun `search returns empty when no embeddings in database`() = runTest {
        coEvery { semanticSearchEngine.isReady() } returns true
        coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns emptyList()

        val results = strategy.search("funny cat", 10)

        assertThat(results).isEmpty()
        coVerify(exactly = 0) {
            semanticSearchEngine.findSimilarMultiVector(any(), any(), any(), any())
        }
    }

    @Test
    fun `search delegates to findSimilarMultiVector`() = runTest {
        val embeddingData = createEmbeddingData(memeId = 1L, floats = floatArrayOf(1f, 2f, 3f))
        val expectedResult = SearchResult(
            meme = createTestMeme(1L),
            relevanceScore = 0.9f,
            matchType = MatchType.SEMANTIC,
        )
        coEvery { semanticSearchEngine.isReady() } returns true
        coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns listOf(embeddingData)
        coEvery {
            semanticSearchEngine.findSimilarMultiVector(any(), any(), any(), any())
        } returns listOf(expectedResult)

        val results = strategy.search("funny cat", 10)

        assertThat(results).hasSize(1)
        assertThat(results[0]).isEqualTo(expectedResult)
        coVerify {
            semanticSearchEngine.findSimilarMultiVector(
                query = "funny cat",
                candidates = any(),
                limit = 10,
            )
        }
    }

    @Test
    fun `search passes correct limit to engine`() = runTest {
        val limitSlot = slot<Int>()
        val embeddingData = createEmbeddingData(memeId = 1L, floats = floatArrayOf(1f, 2f, 3f))
        coEvery { semanticSearchEngine.isReady() } returns true
        coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns listOf(embeddingData)
        coEvery {
            semanticSearchEngine.findSimilarMultiVector(any(), any(), capture(limitSlot), any())
        } returns emptyList()

        strategy.search("test", 42)

        assertThat(limitSlot.captured).isEqualTo(42)
    }

    // endregion

    // region Embedding decoding

    @Test
    fun `correctly decodes float32 little-endian embeddings`() = runTest {
        val originalFloats = floatArrayOf(1.5f, -2.5f, 3.14f)
        val embeddingData = createEmbeddingData(memeId = 1L, floats = originalFloats)
        val candidatesSlot = slot<List<MemeWithEmbeddings>>()
        coEvery { semanticSearchEngine.isReady() } returns true
        coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns listOf(embeddingData)
        coEvery {
            semanticSearchEngine.findSimilarMultiVector(any(), capture(candidatesSlot), any(), any())
        } returns emptyList()

        strategy.search("test", 10)

        val decoded = candidatesSlot.captured[0].embeddings["content"]!!
        assertThat(decoded.size).isEqualTo(3)
        assertThat(decoded[0]).isWithin(0.0001f).of(1.5f)
        assertThat(decoded[1]).isWithin(0.0001f).of(-2.5f)
        assertThat(decoded[2]).isWithin(0.0001f).of(3.14f)
    }

    @Test
    fun `skips embeddings with invalid dimensions`() = runTest {
        // A single float (size 1) is < 2, so it should be skipped
        val singleFloat = createEmbeddingData(
            memeId = 1L,
            floats = floatArrayOf(1.0f),
            embeddingType = "content",
        )
        val candidatesSlot = slot<List<MemeWithEmbeddings>>()
        coEvery { semanticSearchEngine.isReady() } returns true
        coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns listOf(singleFloat)
        coEvery {
            semanticSearchEngine.findSimilarMultiVector(any(), capture(candidatesSlot), any(), any())
        } returns emptyList()

        strategy.search("test", 10)

        val embeddings = candidatesSlot.captured[0].embeddings
        assertThat(embeddings).isEmpty()
    }

    @Test
    fun `handles null embedding type by filtering out row`() = runTest {
        // The buildCandidates method filters rows where embeddingType is null
        // in the second filter (line 78), so the row is skipped for embedding map
        val data = MemeWithEmbeddingData(
            memeId = 1L,
            filePath = "/test/meme1.jpg",
            fileName = "meme1.jpg",
            title = null,
            description = null,
            textContent = null,
            emojiTagsJson = "[]",
            embedding = encodeFloats(floatArrayOf(1f, 2f, 3f)),
            embeddingType = null,
            dimension = 3,
            modelVersion = "test:1.0",
        )
        val candidatesSlot = slot<List<MemeWithEmbeddings>>()
        coEvery { semanticSearchEngine.isReady() } returns true
        coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns listOf(data)
        coEvery {
            semanticSearchEngine.findSimilarMultiVector(any(), capture(candidatesSlot), any(), any())
        } returns emptyList()

        strategy.search("test", 10)

        // Row has embeddingType == null, so it's filtered out by the second filter
        // (which requires embeddingType != null). The candidate meme is still built
        // but has empty embeddings map.
        val embeddings = candidatesSlot.captured[0].embeddings
        assertThat(embeddings).isEmpty()
    }

    @Test
    fun `groups multiple embeddings per meme`() = runTest {
        val contentData = createEmbeddingData(
            memeId = 1L,
            floats = floatArrayOf(1f, 2f, 3f),
            embeddingType = "content",
        )
        val intentData = MemeWithEmbeddingData(
            memeId = 1L,
            filePath = "/test/meme1.jpg",
            fileName = "meme1.jpg",
            title = null,
            description = null,
            textContent = null,
            emojiTagsJson = "[]",
            embedding = encodeFloats(floatArrayOf(4f, 5f, 6f)),
            embeddingType = "intent",
            dimension = 3,
            modelVersion = "test:1.0",
        )
        val candidatesSlot = slot<List<MemeWithEmbeddings>>()
        coEvery { semanticSearchEngine.isReady() } returns true
        coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns listOf(contentData, intentData)
        coEvery {
            semanticSearchEngine.findSimilarMultiVector(any(), capture(candidatesSlot), any(), any())
        } returns emptyList()

        strategy.search("test", 10)

        val candidates = candidatesSlot.captured
        assertThat(candidates).hasSize(1)
        assertThat(candidates[0].embeddings).containsKey("content")
        assertThat(candidates[0].embeddings).containsKey("intent")
        assertThat(candidates[0].meme.id).isEqualTo(1L)
    }

    @Test
    fun `multiple memes produce separate candidates`() = runTest {
        val meme1 = createEmbeddingData(memeId = 1L, floats = floatArrayOf(1f, 2f, 3f))
        val meme2 = createEmbeddingData(memeId = 2L, floats = floatArrayOf(4f, 5f, 6f))
        val candidatesSlot = slot<List<MemeWithEmbeddings>>()
        coEvery { semanticSearchEngine.isReady() } returns true
        coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns listOf(meme1, meme2)
        coEvery {
            semanticSearchEngine.findSimilarMultiVector(any(), capture(candidatesSlot), any(), any())
        } returns emptyList()

        strategy.search("test", 10)

        assertThat(candidatesSlot.captured).hasSize(2)
        assertThat(candidatesSlot.captured.map { it.meme.id }).containsExactly(1L, 2L)
    }

    // endregion

    // region Error handling

    @Test
    fun `handles empty embedding bytes`() = runTest {
        // Empty byte array => 0 floats => size 0 < 2 => skipped
        val data = MemeWithEmbeddingData(
            memeId = 1L,
            filePath = "/test/meme1.jpg",
            fileName = "meme1.jpg",
            title = null,
            description = null,
            textContent = null,
            emojiTagsJson = "[]",
            embedding = ByteArray(0),
            embeddingType = "content",
            dimension = 0,
            modelVersion = "test:1.0",
        )
        val candidatesSlot = slot<List<MemeWithEmbeddings>>()
        coEvery { semanticSearchEngine.isReady() } returns true
        coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns listOf(data)
        coEvery {
            semanticSearchEngine.findSimilarMultiVector(any(), capture(candidatesSlot), any(), any())
        } returns emptyList()

        strategy.search("test", 10)

        assertThat(candidatesSlot.captured[0].embeddings).isEmpty()
    }

    @Test
    fun `null embedding rows are filtered before grouping`() = runTest {
        val nullEmbedding = MemeWithEmbeddingData(
            memeId = 1L,
            filePath = "/test/meme1.jpg",
            fileName = "meme1.jpg",
            title = null,
            description = null,
            textContent = null,
            emojiTagsJson = "[]",
            embedding = null,
            embeddingType = "content",
            dimension = null,
            modelVersion = null,
        )
        coEvery { semanticSearchEngine.isReady() } returns true
        coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns listOf(nullEmbedding)

        val results = strategy.search("test", 10)

        // Null embedding is filtered out in the first filter, so groupedByMeme is empty
        assertThat(results).isEmpty()
        coVerify(exactly = 0) {
            semanticSearchEngine.findSimilarMultiVector(any(), any(), any(), any())
        }
    }

    @Test
    fun `engine exception propagates to caller`() = runTest {
        val embeddingData = createEmbeddingData(memeId = 1L, floats = floatArrayOf(1f, 2f, 3f))
        coEvery { semanticSearchEngine.isReady() } returns true
        coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns listOf(embeddingData)
        coEvery {
            semanticSearchEngine.findSimilarMultiVector(any(), any(), any(), any())
        } throws RuntimeException("Model failed")

        var caughtException: Exception? = null
        try {
            strategy.search("test", 10)
        } catch (e: RuntimeException) {
            caughtException = e
        }

        assertThat(caughtException).isNotNull()
        assertThat(caughtException!!.message).isEqualTo("Model failed")
    }

    @Test
    fun `meme fields are correctly mapped from embedding data`() = runTest {
        val data = MemeWithEmbeddingData(
            memeId = 42L,
            filePath = "/storage/memes/cat.jpg",
            fileName = "cat.jpg",
            title = "Funny Cat",
            description = "A very funny cat meme",
            textContent = "when you see it",
            emojiTagsJson = """["😂","🐱"]""",
            embedding = encodeFloats(floatArrayOf(1f, 2f, 3f)),
            embeddingType = "content",
            dimension = 3,
            modelVersion = "test:1.0",
        )
        val candidatesSlot = slot<List<MemeWithEmbeddings>>()
        coEvery { semanticSearchEngine.isReady() } returns true
        coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns listOf(data)
        coEvery {
            semanticSearchEngine.findSimilarMultiVector(any(), capture(candidatesSlot), any(), any())
        } returns emptyList()

        strategy.search("test", 10)

        val meme = candidatesSlot.captured[0].meme
        assertThat(meme.id).isEqualTo(42L)
        assertThat(meme.filePath).isEqualTo("/storage/memes/cat.jpg")
        assertThat(meme.fileName).isEqualTo("cat.jpg")
        assertThat(meme.title).isEqualTo("Funny Cat")
        assertThat(meme.description).isEqualTo("A very funny cat meme")
        assertThat(meme.textContent).isEqualTo("when you see it")
        assertThat(meme.emojiTags).hasSize(2)
    }

    // endregion

    // region Helpers

    private fun createTestMeme(id: Long) = com.adsamcik.riposte.core.model.Meme(
        id = id,
        filePath = "/test/meme$id.jpg",
        fileName = "meme$id.jpg",
        mimeType = "image/jpeg",
        width = 0,
        height = 0,
        fileSizeBytes = 0,
        importedAt = 0,
        emojiTags = emptyList(),
    )

    private fun encodeFloats(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) buffer.putFloat(f)
        return buffer.array()
    }

    private fun createEmbeddingData(
        memeId: Long,
        floats: FloatArray,
        embeddingType: String = "content",
    ) = MemeWithEmbeddingData(
        memeId = memeId,
        filePath = "/test/meme$memeId.jpg",
        fileName = "meme$memeId.jpg",
        title = null,
        description = null,
        textContent = null,
        emojiTagsJson = "[]",
        embedding = encodeFloats(floats),
        embeddingType = embeddingType,
        dimension = floats.size,
        modelVersion = "test:1.0",
    )

    // endregion
}
