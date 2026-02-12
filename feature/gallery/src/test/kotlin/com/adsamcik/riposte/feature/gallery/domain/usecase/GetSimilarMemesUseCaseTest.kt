package com.adsamcik.riposte.feature.gallery.domain.usecase

import com.adsamcik.riposte.core.ml.EmbeddingManager
import com.adsamcik.riposte.core.ml.SemanticSearchEngine
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.testing.MainDispatcherRule
import com.adsamcik.riposte.feature.gallery.domain.model.MemeEmbeddingData
import com.adsamcik.riposte.feature.gallery.domain.repository.GalleryRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(ExperimentalCoroutinesApi::class)
class GetSimilarMemesUseCaseTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var embeddingManager: EmbeddingManager
    private lateinit var galleryRepository: GalleryRepository
    private lateinit var semanticSearchEngine: SemanticSearchEngine
    private lateinit var useCase: GetSimilarMemesUseCase

    @Before
    fun setup() {
        embeddingManager = mockk()
        galleryRepository = mockk()
        semanticSearchEngine = mockk()
        useCase =
            GetSimilarMemesUseCase(
                embeddingManager = embeddingManager,
                galleryRepository = galleryRepository,
                semanticSearchEngine = semanticSearchEngine,
                defaultDispatcher = mainDispatcherRule.testDispatcher,
            )
    }

    // region Test Data Helpers

    private fun encodeEmbedding(embedding: FloatArray): ByteArray {
        val buffer =
            ByteBuffer.allocate(embedding.size * 4)
                .order(ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun createMeme(
        id: Long,
        fileName: String = "meme_$id.jpg",
    ) = Meme(
        id = id,
        filePath = "/test/$fileName",
        fileName = fileName,
        mimeType = "image/jpeg",
        width = 1080,
        height = 1080,
        fileSizeBytes = 1024L,
        importedAt = System.currentTimeMillis(),
        emojiTags = emptyList(),
    )

    private fun createEmbeddingData(
        memeId: Long,
        embedding: FloatArray? = FloatArray(128) { it.toFloat() / 128f },
    ) = MemeEmbeddingData(
        memeId = memeId,
        embedding = embedding?.let { encodeEmbedding(it) },
    )

    // endregion

    // region Found Tests

    @Test
    fun `returns Found with similar memes when embeddings match`() =
        runTest {
            val currentEmbedding = FloatArray(128) { 1.0f }
            val candidateEmbedding = FloatArray(128) { 0.9f }

            coEvery { embeddingManager.getEmbedding(1L) } returns currentEmbedding
            coEvery { galleryRepository.getEmbeddingsExcluding(1L) } returns
                listOf(
                    createEmbeddingData(2L, candidateEmbedding),
                    createEmbeddingData(3L, candidateEmbedding),
                )
            coEvery { semanticSearchEngine.cosineSimilarity(any(), any()) } returns 0.85f
            coEvery { galleryRepository.getMemeById(2L) } returns createMeme(2L)
            coEvery { galleryRepository.getMemeById(3L) } returns createMeme(3L)

            val result = useCase(1L)

            assertThat(result).isInstanceOf(SimilarMemesStatus.Found::class.java)
            val found = result as SimilarMemesStatus.Found
            assertThat(found.memes).hasSize(2)
        }

    @Test
    fun `returns Found with memes sorted by similarity score`() =
        runTest {
            val currentEmbedding = FloatArray(128) { 1.0f }
            val highSimilarity = FloatArray(128) { 0.95f }
            val lowSimilarity = FloatArray(128) { 0.5f }

            coEvery { embeddingManager.getEmbedding(1L) } returns currentEmbedding
            coEvery { galleryRepository.getEmbeddingsExcluding(1L) } returns
                listOf(
                    createEmbeddingData(2L, lowSimilarity),
                    createEmbeddingData(3L, highSimilarity),
                )
            coEvery { semanticSearchEngine.cosineSimilarity(currentEmbedding, any()) } returnsMany
                listOf(0.5f, 0.95f)
            coEvery { galleryRepository.getMemeById(3L) } returns createMeme(3L)
            coEvery { galleryRepository.getMemeById(2L) } returns createMeme(2L)

            val result = useCase(1L)

            assertThat(result).isInstanceOf(SimilarMemesStatus.Found::class.java)
            val found = result as SimilarMemesStatus.Found
            assertThat(found.memes).hasSize(2)
            // Higher score meme (id=3) should come first
            assertThat(found.memes[0].id).isEqualTo(3L)
            assertThat(found.memes[1].id).isEqualTo(2L)
        }

    @Test
    fun `returns Found respecting limit parameter`() =
        runTest {
            val currentEmbedding = FloatArray(128) { 1.0f }

            coEvery { embeddingManager.getEmbedding(1L) } returns currentEmbedding
            coEvery { galleryRepository.getEmbeddingsExcluding(1L) } returns
                (2L..10L).map { createEmbeddingData(it) }
            coEvery { semanticSearchEngine.cosineSimilarity(any(), any()) } returns 0.8f
            coEvery { galleryRepository.getMemeById(any()) } answers {
                createMeme(firstArg())
            }

            val result = useCase(1L, limit = 3)

            assertThat(result).isInstanceOf(SimilarMemesStatus.Found::class.java)
            val found = result as SimilarMemesStatus.Found
            assertThat(found.memes).hasSize(3)
        }

    // endregion

    // region NoEmbeddingForMeme Tests

    @Test
    fun `returns NoEmbeddingForMeme when current meme has no embedding`() =
        runTest {
            coEvery { embeddingManager.getEmbedding(1L) } returns null

            val result = useCase(1L)

            assertThat(result).isEqualTo(SimilarMemesStatus.NoEmbeddingForMeme)
        }

    // endregion

    // region NoCandidates Tests

    @Test
    fun `returns NoCandidates when no other embeddings exist`() =
        runTest {
            val currentEmbedding = FloatArray(128) { 1.0f }

            coEvery { embeddingManager.getEmbedding(1L) } returns currentEmbedding
            coEvery { galleryRepository.getEmbeddingsExcluding(1L) } returns emptyList()

            val result = useCase(1L)

            assertThat(result).isEqualTo(SimilarMemesStatus.NoCandidates)
        }

    // endregion

    // region NoSimilarFound Tests

    @Test
    fun `returns NoSimilarFound when all scores below threshold`() =
        runTest {
            val currentEmbedding = FloatArray(128) { 1.0f }

            coEvery { embeddingManager.getEmbedding(1L) } returns currentEmbedding
            coEvery { galleryRepository.getEmbeddingsExcluding(1L) } returns
                listOf(
                    createEmbeddingData(2L),
                    createEmbeddingData(3L),
                )
            // All scores below the 0.3f threshold
            coEvery { semanticSearchEngine.cosineSimilarity(any(), any()) } returns 0.1f

            val result = useCase(1L)

            assertThat(result).isEqualTo(SimilarMemesStatus.NoSimilarFound)
        }

    @Test
    fun `returns NoSimilarFound when candidates have null embeddings`() =
        runTest {
            val currentEmbedding = FloatArray(128) { 1.0f }

            coEvery { embeddingManager.getEmbedding(1L) } returns currentEmbedding
            coEvery { galleryRepository.getEmbeddingsExcluding(1L) } returns
                listOf(createEmbeddingData(2L, embedding = null))

            val result = useCase(1L)

            assertThat(result).isEqualTo(SimilarMemesStatus.NoSimilarFound)
        }

    @Test
    fun `returns NoSimilarFound when dimension mismatch`() =
        runTest {
            val currentEmbedding = FloatArray(128) { 1.0f }
            val differentDimEmbedding = FloatArray(256) { 1.0f }

            coEvery { embeddingManager.getEmbedding(1L) } returns currentEmbedding
            coEvery { galleryRepository.getEmbeddingsExcluding(1L) } returns
                listOf(createEmbeddingData(2L, differentDimEmbedding))

            val result = useCase(1L)

            assertThat(result).isEqualTo(SimilarMemesStatus.NoSimilarFound)
        }

    // endregion

    // region Error Tests

    @Test
    fun `returns Error when embeddingManager throws exception`() =
        runTest {
            coEvery { embeddingManager.getEmbedding(1L) } throws RuntimeException("DB error")

            val result = useCase(1L)

            assertThat(result).isInstanceOf(SimilarMemesStatus.Error::class.java)
            val error = result as SimilarMemesStatus.Error
            assertThat(error.message).isEqualTo("DB error")
        }

    @Test
    fun `returns Error when galleryRepository throws exception`() =
        runTest {
            val currentEmbedding = FloatArray(128) { 1.0f }
            coEvery { embeddingManager.getEmbedding(1L) } returns currentEmbedding
            coEvery { galleryRepository.getEmbeddingsExcluding(1L) } throws RuntimeException("Query failed")

            val result = useCase(1L)

            assertThat(result).isInstanceOf(SimilarMemesStatus.Error::class.java)
            val error = result as SimilarMemesStatus.Error
            assertThat(error.message).isEqualTo("Query failed")
        }

    // endregion

    // region Edge Cases

    @Test
    fun `handles getMemeById returning null gracefully`() =
        runTest {
            val currentEmbedding = FloatArray(128) { 1.0f }

            coEvery { embeddingManager.getEmbedding(1L) } returns currentEmbedding
            coEvery { galleryRepository.getEmbeddingsExcluding(1L) } returns
                listOf(
                    createEmbeddingData(2L),
                    createEmbeddingData(3L),
                )
            coEvery { semanticSearchEngine.cosineSimilarity(any(), any()) } returns 0.8f
            coEvery { galleryRepository.getMemeById(2L) } returns null // deleted meme
            coEvery { galleryRepository.getMemeById(3L) } returns createMeme(3L)

            val result = useCase(1L)

            assertThat(result).isInstanceOf(SimilarMemesStatus.Found::class.java)
            val found = result as SimilarMemesStatus.Found
            assertThat(found.memes).hasSize(1)
            assertThat(found.memes[0].id).isEqualTo(3L)
        }

    // endregion
}
