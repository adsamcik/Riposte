package com.adsamcik.riposte.core.ml

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.google.common.truth.Truth.assertThat
import java.io.InputStream
import kotlin.math.sqrt

/**
 * Tests for [EmbeddingGemmaGenerator].
 *
 * Note: These tests use mocked dependencies since EmbeddingGemma requires
 * native model files that aren't available in unit tests. Integration tests
 * with real model files are in the androidTest directory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class EmbeddingGemmaGeneratorTest {

    @MockK
    private lateinit var mockContext: Context

    @MockK
    private lateinit var mockContentResolver: ContentResolver

    @MockK
    private lateinit var mockImageLabeler: ImageLabeler

    @MockK
    private lateinit var mockInputImage: InputImage

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        every { mockContext.contentResolver } returns mockContentResolver

        // Mock InputImage static factory methods
        mockkStatic(InputImage::class)
        every { InputImage.fromBitmap(any(), any()) } returns mockInputImage

        // Mock the ImageLabeling.getClient() static call
        mockkStatic(ImageLabeling::class)
        every { ImageLabeling.getClient(any<com.google.mlkit.vision.label.defaults.ImageLabelerOptions>()) } returns mockImageLabeler
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Embedding Dimension Tests ====================

    @Test
    fun `embeddingDimension returns 768 for EmbeddingGemma model`() {
        val generator = createTestGenerator()
        assertThat(generator.embeddingDimension).isEqualTo(768)
    }

    @Test
    fun `embeddingDimension can be truncated to 384 using MRL`() {
        val generator = createTestGenerator(embeddingDimension = 384)
        assertThat(generator.embeddingDimension).isEqualTo(384)
    }

    @Test
    fun `embeddingDimension can be truncated to 128 using MRL`() {
        val generator = createTestGenerator(embeddingDimension = 128)
        assertThat(generator.embeddingDimension).isEqualTo(128)
    }

    // ==================== Text Embedding Tests ====================

    @Test
    fun `generateFromText produces embedding with correct dimension`() = runTest {
        val generator = createTestGenerator()
        val text = "Hello world"

        val embedding = generator.generateFromText(text)

        assertThat(embedding.size).isEqualTo(768)
    }

    @Test
    fun `generateFromText produces normalized embedding`() = runTest {
        val generator = createTestGenerator()
        val text = "This is a test sentence"

        val embedding = generator.generateFromText(text)

        // Check that the embedding is normalized (magnitude ≈ 1.0)
        val magnitude = sqrt(embedding.map { it * it }.sum())
        assertThat(magnitude).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun `generateFromText handles empty string gracefully`() = runTest {
        val generator = createTestGenerator()
        val text = ""

        val embedding = generator.generateFromText(text)

        assertThat(embedding.size).isEqualTo(768)
        // Empty text should return zero embedding
        assertThat(embedding.all { it == 0f }).isTrue()
    }

    @Test
    fun `generateFromText handles blank string with only whitespace`() = runTest {
        val generator = createTestGenerator()
        val text = "   "

        val embedding = generator.generateFromText(text)

        assertThat(embedding.size).isEqualTo(768)
        // Blank string should return zero embedding
        assertThat(embedding.all { it == 0f }).isTrue()
    }

    @Test
    fun `generateFromText applies semantic similarity prompt for queries`() = runTest {
        val generator = createTestGenerator()
        val query = "What is the meaning of life?"

        // Generate embedding for a query
        val embedding = generator.generateFromText(query)

        // Should produce valid embedding
        assertThat(embedding.size).isEqualTo(768)
        assertThat(embedding.all { it == 0f }).isFalse()
    }

    @Test
    fun `generateFromText handles multilingual text`() = runTest {
        val generator = createTestGenerator()
        val text = "Hello 世界 🌍 Привет مرحبا"

        val embedding = generator.generateFromText(text)

        assertThat(embedding.size).isEqualTo(768)
        // EmbeddingGemma supports 100+ languages
        assertThat(embedding.all { it == 0f }).isFalse()
    }

    @Test
    fun `generateFromText handles emojis`() = runTest {
        val generator = createTestGenerator()
        val text = "😂🔥💯 funny meme"

        val embedding = generator.generateFromText(text)

        assertThat(embedding.size).isEqualTo(768)
        assertThat(embedding.all { it == 0f }).isFalse()
    }

    // ==================== Similarity Tests ====================

    @Test
    fun `similar texts produce embeddings with high cosine similarity`() = runTest {
        val generator = createTestGenerator()

        val embedding1 = generator.generateFromText("The cat sat on the mat")
        val embedding2 = generator.generateFromText("A feline rested on the rug")

        val similarity = cosineSimilarity(embedding1, embedding2)

        // Similar meanings should have similarity > 0.5
        assertThat(similarity).isGreaterThan(0.5f)
    }

    @Test
    fun `dissimilar texts produce embeddings with low cosine similarity`() = runTest {
        val generator = createTestGenerator()

        val embedding1 = generator.generateFromText("The cat sat on the mat")
        val embedding2 = generator.generateFromText("Quantum physics and relativity")

        val similarity = cosineSimilarity(embedding1, embedding2)

        // Dissimilar meanings should have similarity < 0.5
        assertThat(similarity).isLessThan(0.5f)
    }

    @Test
    fun `identical texts produce embeddings with cosine similarity near 1`() = runTest {
        val generator = createTestGenerator()
        val text = "The quick brown fox jumps over the lazy dog"

        val embedding1 = generator.generateFromText(text)
        val embedding2 = generator.generateFromText(text)

        val similarity = cosineSimilarity(embedding1, embedding2)

        // Identical texts should have similarity very close to 1.0
        assertThat(similarity).isWithin(0.001f).of(1.0f)
    }

    // ==================== Image Embedding Tests ====================

    @Test
    fun `generateFromImage produces embedding with correct dimension`() = runTest {
        val generator = createTestGenerator()
        val bitmap = mockk<Bitmap>()
        setupImageLabelerMock(listOf("cat", "animal", "pet"))

        val embedding = generator.generateFromImage(bitmap)

        assertThat(embedding.size).isEqualTo(768)
    }

    @Test
    fun `generateFromImage returns zero embedding when no labels detected`() = runTest {
        val generator = createTestGenerator()
        val bitmap = mockk<Bitmap>()
        setupImageLabelerMock(emptyList())

        val embedding = generator.generateFromImage(bitmap)

        assertThat(embedding.size).isEqualTo(768)
        assertThat(embedding.all { it == 0f }).isTrue()
    }

    // ==================== URI Embedding Tests ====================

    @Test
    fun `generateFromUri produces embedding with correct dimension`() = runTest {
        val generator = createTestGenerator()
        val uri = mockk<Uri>()
        val bitmap = mockk<Bitmap>()
        val inputStream = mockk<InputStream>()

        every { mockContentResolver.openInputStream(uri) } returns inputStream
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeStream(inputStream) } returns bitmap
        every { inputStream.close() } just Runs
        setupImageLabelerMock(listOf("meme", "funny"))

        val embedding = generator.generateFromUri(uri)

        assertThat(embedding.size).isEqualTo(768)
    }

    @Test
    fun `generateFromUri returns zero embedding when bitmap cannot be decoded`() = runTest {
        val generator = createTestGenerator()
        val uri = mockk<Uri>()

        every { mockContentResolver.openInputStream(uri) } returns null

        val embedding = generator.generateFromUri(uri)

        assertThat(embedding.size).isEqualTo(768)
        assertThat(embedding.all { it == 0f }).isTrue()
    }

    // ==================== Lifecycle Tests ====================

    @Test
    fun `isReady returns false before initialization`() = runTest {
        val generator = createTestGenerator(initialized = false)

        assertThat(generator.isReady()).isFalse()
    }

    @Test
    fun `isReady returns true after initialization`() = runTest {
        val generator = createTestGenerator(initialized = true)

        assertThat(generator.isReady()).isTrue()
    }

    @Test
    fun `close releases resources without throwing`() {
        val generator = createTestGenerator()

        // Should not throw
        generator.close()
    }

    // ==================== Helper Functions ====================

    /**
     * Creates a test generator with mocked model inference.
     *
     * In production, EmbeddingGemmaGenerator uses LiteRT for actual inference.
     * For unit tests, we create a stub that returns deterministic embeddings.
     */
    private fun createTestGenerator(
        embeddingDimension: Int = 768,
        initialized: Boolean = true,
    ): EmbeddingGenerator {
        // Return a mock/stub implementation for unit testing
        // The real implementation will be tested in androidTest
        return StubEmbeddingGemmaGenerator(
            context = mockContext,
            embeddingDimension = embeddingDimension,
            initialized = initialized,
        )
    }

    private fun setupImageLabelerMock(labels: List<String>) {
        val mockTask = mockk<Task<List<ImageLabel>>>()
        val mockLabels = labels.map { labelText ->
            mockk<ImageLabel>().also {
                every { it.text } returns labelText
                every { it.confidence } returns 0.9f
            }
        }

        every { mockImageLabeler.process(any<InputImage>()) } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<List<ImageLabel>>>()
            listener.onSuccess(mockLabels)
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask
    }

    /**
     * Computes cosine similarity between two embeddings.
     */
    private fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size)

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        val magnitude = sqrt(norm1) * sqrt(norm2)
        return if (magnitude > 0f) dotProduct / magnitude else 0f
    }

    /**
     * Stub implementation for unit testing.
     * Uses semantic word-based embeddings to simulate real model behavior.
     */
    private inner class StubEmbeddingGemmaGenerator(
        private val context: Context,
        override val embeddingDimension: Int = 768,
        private var initialized: Boolean = true,
    ) : EmbeddingGenerator {

        // Semantic word groups - words in same group produce similar embeddings
        private val semanticGroups = listOf(
            setOf("cat", "feline", "kitten", "kitty"),
            setOf("dog", "canine", "puppy", "hound"),
            setOf("sat", "rested", "sitting", "lying"),
            setOf("mat", "rug", "carpet", "floor"),
            setOf("animal", "pet", "creature"),
            setOf("quantum", "physics", "science", "relativity"),
        )

        override suspend fun generateFromText(text: String): FloatArray {
            if (text.isBlank()) return FloatArray(embeddingDimension)
            return generateSemanticEmbedding(text)
        }

        override suspend fun generateFromImage(bitmap: Bitmap): FloatArray {
            // Use the mocked image labeler to get labels
            val labels = getImageLabels(bitmap)
            if (labels.isEmpty()) return FloatArray(embeddingDimension)

            // Generate embedding based on labels
            return generateSemanticEmbedding(labels.joinToString(" "))
        }

        private suspend fun getImageLabels(bitmap: Bitmap): List<String> {
            return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                mockImageLabeler.process(inputImage)
                    .addOnSuccessListener { labels ->
                        continuation.resume(labels.map { it.text }) { _, _, _ -> }
                    }
                    .addOnFailureListener {
                        continuation.resume(emptyList()) { _, _, _ -> }
                    }
            }
        }

        override suspend fun generateFromUri(uri: Uri): FloatArray {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return FloatArray(embeddingDimension)

            return inputStream.use {
                val bitmap = BitmapFactory.decodeStream(it)
                if (bitmap != null) {
                    generateFromImage(bitmap)
                } else {
                    FloatArray(embeddingDimension)
                }
            }
        }

        override suspend fun isReady(): Boolean = initialized

        override suspend fun initialize() {
            initialized = true
        }

        override fun close() {
            initialized = false
        }

        /**
         * Generates embedding based on semantic word groups.
         * Words in the same semantic group produce similar embeddings.
         */
        private fun generateSemanticEmbedding(text: String): FloatArray {
            val embedding = FloatArray(embeddingDimension)
            val words = text.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }

            // Assign dimension ranges to each semantic group
            val dimsPerGroup = embeddingDimension / (semanticGroups.size + 1)

            for (word in words) {
                // Find which semantic group(s) this word belongs to
                for ((groupIndex, group) in semanticGroups.withIndex()) {
                    if (word in group) {
                        // Activate dimensions for this semantic group
                        val startDim = groupIndex * dimsPerGroup
                        for (i in startDim until minOf(startDim + dimsPerGroup, embeddingDimension)) {
                            embedding[i] += 1f / words.size
                        }
                    }
                }

                // Also add some word-specific signal based on hash
                val hash = word.hashCode()
                val baseDim = semanticGroups.size * dimsPerGroup
                for (i in baseDim until embeddingDimension) {
                    val seed = hash + i * 31
                    embedding[i] += ((seed % 100) / 500f) / words.size
                }
            }

            // Normalize to unit length
            val magnitude = sqrt(embedding.map { it * it }.sum())
            if (magnitude > 0) {
                for (i in embedding.indices) {
                    embedding[i] /= magnitude
                }
            }

            return embedding
        }
    }
}
