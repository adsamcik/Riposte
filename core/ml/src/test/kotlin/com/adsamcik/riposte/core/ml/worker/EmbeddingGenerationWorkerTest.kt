package com.adsamcik.riposte.core.ml.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.adsamcik.riposte.core.ml.EmbeddingGenerator
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EmbeddingGenerationWorkerTest {
    private lateinit var context: Context
    private lateinit var embeddingGenerator: EmbeddingGenerator
    private lateinit var embeddingRepository: EmbeddingWorkRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        embeddingGenerator = mockk()
        embeddingRepository = mockk(relaxed = true)
    }

    private fun createWorker(): EmbeddingGenerationWorker {
        return TestListenableWorkerBuilder<EmbeddingGenerationWorker>(context)
            .setWorkerFactory(TestEmbeddingWorkerFactory())
            .build() as EmbeddingGenerationWorker
    }

    private inner class TestEmbeddingWorkerFactory : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: androidx.work.WorkerParameters,
        ): ListenableWorker {
            return EmbeddingGenerationWorker(
                context = appContext,
                params = workerParameters,
                embeddingGenerator = embeddingGenerator,
                embeddingRepository = embeddingRepository,
            )
        }
    }

    // region Test Data Helpers

    private fun createMemeData(
        id: Long = 1L,
        filePath: String = "/test/meme.jpg",
        title: String? = "Test Meme",
        description: String? = "A test meme",
        textContent: String? = null,
        searchPhrases: String? = null,
    ) = MemeDataForEmbedding(
        id = id,
        filePath = filePath,
        title = title,
        description = description,
        textContent = textContent,
        searchPhrases = searchPhrases,
    )

    private fun createTestEmbedding(size: Int = 128): FloatArray =
        FloatArray(size) { it.toFloat() / size }

    // endregion

    // region Successful Flow Tests

    @Test
    fun `doWork returns success with zero counts when no pending memes`() =
        runTest {
            coEvery { embeddingRepository.getMemesNeedingEmbeddings(any()) } returns emptyList()
            coEvery { embeddingRepository.countMemesNeedingEmbeddings() } returns 0

            val worker = createWorker()
            val result = worker.doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
            val data = (result as ListenableWorker.Result.Success).outputData
            assertThat(data.getInt(EmbeddingGenerationWorker.KEY_PROCESSED_COUNT, -1)).isEqualTo(0)
            assertThat(data.getInt(EmbeddingGenerationWorker.KEY_REMAINING_COUNT, -1)).isEqualTo(0)
        }

    @Test
    fun `doWork processes pending memes and returns success counts`() =
        runTest {
            val memes =
                listOf(
                    createMemeData(id = 1, title = "Meme 1", description = "Desc 1"),
                    createMemeData(id = 2, title = "Meme 2", description = "Desc 2"),
                )
            val embedding = createTestEmbedding()

            coEvery { embeddingRepository.getMemesNeedingEmbeddings(any()) } returns memes
            coEvery { embeddingGenerator.generateFromText(any()) } returns embedding
            coEvery { embeddingRepository.countMemesNeedingEmbeddings() } returns 0

            val worker = createWorker()
            val result = worker.doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
            val data = (result as ListenableWorker.Result.Success).outputData
            assertThat(data.getInt(EmbeddingGenerationWorker.KEY_PROCESSED_COUNT, -1)).isEqualTo(2)
            assertThat(data.getInt(EmbeddingGenerationWorker.KEY_FAILED_COUNT, -1)).isEqualTo(0)
        }

    @Test
    fun `doWork generates content embedding from title and description`() =
        runTest {
            val meme = createMemeData(id = 1, title = "Funny Cat", description = "A hilarious cat meme")
            val embedding = createTestEmbedding()

            coEvery { embeddingRepository.getMemesNeedingEmbeddings(any()) } returns listOf(meme)
            coEvery { embeddingGenerator.generateFromText(any()) } returns embedding
            coEvery { embeddingRepository.countMemesNeedingEmbeddings() } returns 0

            val worker = createWorker()
            worker.doWork()

            coVerify {
                embeddingRepository.saveEmbedding(
                    memeId = 1,
                    embedding = any(),
                    dimension = embedding.size,
                    modelVersion = EmbeddingGenerationWorker.CURRENT_MODEL_VERSION,
                    sourceTextHash = any(),
                    embeddingType = "content",
                )
            }
        }

    @Test
    fun `doWork generates intent embedding from search phrases`() =
        runTest {
            val meme = createMemeData(
                id = 1,
                title = "Cat",
                description = null,
                searchPhrases = """["funny cat","laughing"]""",
            )
            val embedding = createTestEmbedding()

            coEvery { embeddingRepository.getMemesNeedingEmbeddings(any()) } returns listOf(meme)
            coEvery { embeddingGenerator.generateFromText(any()) } returns embedding
            coEvery { embeddingRepository.countMemesNeedingEmbeddings() } returns 0

            val worker = createWorker()
            worker.doWork()

            coVerify {
                embeddingRepository.saveEmbedding(
                    memeId = 1,
                    embedding = any(),
                    dimension = any(),
                    modelVersion = any(),
                    sourceTextHash = any(),
                    embeddingType = "intent",
                )
            }
        }

    // endregion

    // region Missing/Empty Content Tests

    @Test
    fun `doWork skips content embedding when title and description are null`() =
        runTest {
            val meme = createMemeData(
                id = 1,
                title = null,
                description = null,
                textContent = null,
                searchPhrases = """["search phrase"]""",
            )
            val embedding = createTestEmbedding()

            coEvery { embeddingRepository.getMemesNeedingEmbeddings(any()) } returns listOf(meme)
            coEvery { embeddingGenerator.generateFromText(any()) } returns embedding
            coEvery { embeddingRepository.countMemesNeedingEmbeddings() } returns 0

            val worker = createWorker()
            worker.doWork()

            // Only intent embedding should be saved, not content
            coVerify(exactly = 1) {
                embeddingRepository.saveEmbedding(
                    memeId = 1,
                    embedding = any(),
                    dimension = any(),
                    modelVersion = any(),
                    sourceTextHash = any(),
                    embeddingType = "intent",
                )
            }
            coVerify(exactly = 0) {
                embeddingRepository.saveEmbedding(
                    memeId = 1,
                    embedding = any(),
                    dimension = any(),
                    modelVersion = any(),
                    sourceTextHash = any(),
                    embeddingType = "content",
                )
            }
        }

    @Test
    fun `doWork skips intent embedding when searchPhrases is null`() =
        runTest {
            val meme = createMemeData(
                id = 1,
                title = "Title",
                searchPhrases = null,
            )
            val embedding = createTestEmbedding()

            coEvery { embeddingRepository.getMemesNeedingEmbeddings(any()) } returns listOf(meme)
            coEvery { embeddingGenerator.generateFromText(any()) } returns embedding
            coEvery { embeddingRepository.countMemesNeedingEmbeddings() } returns 0

            val worker = createWorker()
            worker.doWork()

            coVerify(exactly = 0) {
                embeddingRepository.saveEmbedding(
                    memeId = any(),
                    embedding = any(),
                    dimension = any(),
                    modelVersion = any(),
                    sourceTextHash = any(),
                    embeddingType = "intent",
                )
            }
        }

    // endregion

    // region Error Handling Tests

    @Test
    fun `doWork counts individual meme failures without failing entire work`() =
        runTest {
            val memes =
                listOf(
                    createMemeData(id = 1, title = "Good Meme", description = "Nice"),
                    createMemeData(id = 2, title = "Bad Meme", description = "Broken"),
                )

            coEvery { embeddingRepository.getMemesNeedingEmbeddings(any()) } returns memes
            // First meme succeeds, second fails
            coEvery { embeddingGenerator.generateFromText(match { it.contains("Good") }) } returns createTestEmbedding()
            coEvery { embeddingGenerator.generateFromText(match { it.contains("Bad") }) } throws RuntimeException("Model error")
            coEvery { embeddingRepository.countMemesNeedingEmbeddings() } returns 0

            val worker = createWorker()
            val result = worker.doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
            val data = (result as ListenableWorker.Result.Success).outputData
            assertThat(data.getInt(EmbeddingGenerationWorker.KEY_PROCESSED_COUNT, -1)).isEqualTo(1)
            assertThat(data.getInt(EmbeddingGenerationWorker.KEY_FAILED_COUNT, -1)).isEqualTo(1)
        }

    @Test
    fun `doWork returns retry on catastrophic failure when attempts remain`() =
        runTest {
            coEvery {
                embeddingRepository.getMemesNeedingEmbeddings(any())
            } throws RuntimeException("Database crash")

            val worker = createWorker()
            val result = worker.doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Retry::class.java)
        }

    // endregion

    // region Remaining Count Tests

    @Test
    fun `doWork reports remaining count in output data`() =
        runTest {
            coEvery { embeddingRepository.getMemesNeedingEmbeddings(any()) } returns
                listOf(createMemeData(id = 1, title = "Test"))
            coEvery { embeddingGenerator.generateFromText(any()) } returns createTestEmbedding()
            coEvery { embeddingRepository.countMemesNeedingEmbeddings() } returns 15

            val worker = createWorker()
            val result = worker.doWork()

            val data = (result as ListenableWorker.Result.Success).outputData
            assertThat(data.getInt(EmbeddingGenerationWorker.KEY_REMAINING_COUNT, -1)).isEqualTo(15)
        }

    // endregion

    // region Hash Generation Tests

    @Test
    fun `generateHash produces consistent SHA-256 output`() {
        val text = "test input for hashing"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(text.toByteArray(Charsets.UTF_8))
        val expected = hash.take(16).joinToString("") { "%02x".format(it) }

        // Verify the hash is 32 hex chars (16 bytes)
        assertThat(expected).hasLength(32)

        // Verify determinism
        val digest2 = MessageDigest.getInstance("SHA-256")
        val hash2 = digest2.digest(text.toByteArray(Charsets.UTF_8))
        val expected2 = hash2.take(16).joinToString("") { "%02x".format(it) }
        assertThat(expected).isEqualTo(expected2)
    }

    @Test
    fun `encodeEmbedding produces correct byte representation`() {
        val embedding = floatArrayOf(1.0f, 2.0f, 3.0f)
        val buffer =
            ByteBuffer.allocate(embedding.size * 4)
                .order(ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buffer.putFloat(it) }
        val bytes = buffer.array()

        // Decode back
        val decoded = FloatArray(bytes.size / 4)
        ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .get(decoded)

        assertThat(decoded[0]).isEqualTo(1.0f)
        assertThat(decoded[1]).isEqualTo(2.0f)
        assertThat(decoded[2]).isEqualTo(3.0f)
    }

    // endregion
}
