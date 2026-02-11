package com.adsamcik.riposte.core.ml

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.common.truth.Truth.assertThat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
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
@Config(manifest = Config.NONE, sdk = [28])
class SimpleEmbeddingGeneratorTest {
    @MockK
    private lateinit var mockContext: Context

    @MockK
    private lateinit var mockContentResolver: ContentResolver

    @MockK
    private lateinit var mockImageLabeler: ImageLabeler

    @MockK
    private lateinit var mockInputImage: InputImage

    private lateinit var generator: SimpleEmbeddingGenerator

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        every { mockContext.contentResolver } returns mockContentResolver

        // Mock InputImage static factory methods BEFORE mocking ImageLabeling
        mockkStatic(InputImage::class)
        every { InputImage.fromBitmap(any(), any()) } returns mockInputImage

        // Mock the ImageLabeling.getClient() static call
        mockkStatic(ImageLabeling::class)
        every {
            ImageLabeling.getClient(any<com.google.mlkit.vision.label.defaults.ImageLabelerOptions>())
        } returns mockImageLabeler

        generator = SimpleEmbeddingGenerator(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Basic Embedding Generation Tests ====================

    @Test
    fun `embeddingDimension returns correct value`() {
        assertThat(generator.embeddingDimension).isEqualTo(128)
    }

    @Test
    fun `generateFromText produces embedding with correct dimension`() =
        runTest {
            val text = "Hello world"

            val embedding = generator.generateFromText(text)

            assertThat(embedding.size).isEqualTo(128)
        }

    @Test
    fun `generateFromText produces normalized embedding`() =
        runTest {
            val text = "This is a test sentence"

            val embedding = generator.generateFromText(text)

            // Check that the embedding is normalized (magnitude ≈ 1.0)
            val magnitude = sqrt(embedding.map { it * it }.sum())
            assertThat(magnitude).isWithin(0.001f).of(1.0f)
        }

    // ==================== Consistency Tests ====================

    @Test
    fun `generateFromText produces identical embeddings for same input`() =
        runTest {
            val text = "Consistent input"

            val embedding1 = generator.generateFromText(text)
            val embedding2 = generator.generateFromText(text)

            assertThat(embedding1.toList()).isEqualTo(embedding2.toList())
        }

    @Test
    fun `generateFromText is case insensitive`() =
        runTest {
            val embedding1 = generator.generateFromText("Hello World")
            val embedding2 = generator.generateFromText("hello world")

            assertThat(embedding1.toList()).isEqualTo(embedding2.toList())
        }

    @Test
    fun `generateFromText trims whitespace`() =
        runTest {
            val embedding1 = generator.generateFromText("test")
            val embedding2 = generator.generateFromText("  test  ")

            assertThat(embedding1.toList()).isEqualTo(embedding2.toList())
        }

    @Test
    fun `generateFromText produces consistent results across multiple calls`() =
        runTest {
            val text = "machine learning is great"
            val embeddings = (1..5).map { generator.generateFromText(text) }

            // All embeddings should be identical
            embeddings.forEach { embedding ->
                assertThat(embedding.toList()).isEqualTo(embeddings[0].toList())
            }
        }

    // ==================== Edge Cases Tests ====================

    @Test
    fun `generateFromText handles empty string`() =
        runTest {
            val embedding = generator.generateFromText("")

            assertThat(embedding.size).isEqualTo(128)
            // Empty string should produce zero vector
            assertThat(embedding.all { it == 0f }).isTrue()
        }

    @Test
    fun `generateFromText handles blank string with only whitespace`() =
        runTest {
            val embedding = generator.generateFromText("   ")

            assertThat(embedding.size).isEqualTo(128)
            // Blank string should produce zero vector
            assertThat(embedding.all { it == 0f }).isTrue()
        }

    @Test
    fun `generateFromText handles special characters`() =
        runTest {
            val text = "Hello! @#$%^&*() World..."

            val embedding = generator.generateFromText(text)

            assertThat(embedding.size).isEqualTo(128)
            // Should still produce a valid normalized embedding
            val magnitude = sqrt(embedding.map { it * it }.sum())
            assertThat(magnitude).isWithin(0.001f).of(1.0f)
        }

    @Test
    fun `generateFromText handles unicode characters`() =
        runTest {
            val text = "Hello 世界 🌍 Привет"

            val embedding = generator.generateFromText(text)

            assertThat(embedding.size).isEqualTo(128)
            val magnitude = sqrt(embedding.map { it * it }.sum())
            assertThat(magnitude).isWithin(0.001f).of(1.0f)
        }

    @Test
    fun `generateFromText handles emojis`() =
        runTest {
            val text = "😂🔥💯"

            val embedding = generator.generateFromText(text)

            assertThat(embedding.size).isEqualTo(128)
            val magnitude = sqrt(embedding.map { it * it }.sum())
            assertThat(magnitude).isWithin(0.001f).of(1.0f)
        }

    @Test
    fun `generateFromText handles very long text`() =
        runTest {
            val text = "word ".repeat(1000)

            val embedding = generator.generateFromText(text)

            assertThat(embedding.size).isEqualTo(128)
            val magnitude = sqrt(embedding.map { it * it }.sum())
            assertThat(magnitude).isWithin(0.001f).of(1.0f)
        }

    @Test
    fun `generateFromText handles single word`() =
        runTest {
            val text = "test"

            val embedding = generator.generateFromText(text)

            assertThat(embedding.size).isEqualTo(128)
            val magnitude = sqrt(embedding.map { it * it }.sum())
            assertThat(magnitude).isWithin(0.001f).of(1.0f)
        }

    @Test
    fun `generateFromText handles newlines and tabs`() =
        runTest {
            val embedding1 = generator.generateFromText("hello world")
            val embedding2 = generator.generateFromText("hello\nworld")
            val embedding3 = generator.generateFromText("hello\tworld")

            // All should produce valid embeddings
            assertThat(embedding1.size).isEqualTo(128)
            assertThat(embedding2.size).isEqualTo(128)
            assertThat(embedding3.size).isEqualTo(128)
        }

    // ==================== Similarity Tests ====================

    @Test
    fun `similar texts produce similar embeddings`() =
        runTest {
            val embedding1 = generator.generateFromText("happy dog playing")
            val embedding2 = generator.generateFromText("joyful dog running")
            val embedding3 = generator.generateFromText("sad cat sleeping")

            val similarity12 = cosineSimilarity(embedding1, embedding2)
            val similarity13 = cosineSimilarity(embedding1, embedding3)

            // Texts with shared words should be more similar
            // (Both have "dog" so should be somewhat similar)
            assertThat(similarity12).isGreaterThan(similarity13)
        }

    @Test
    fun `different texts produce different embeddings`() =
        runTest {
            val embedding1 = generator.generateFromText("sunny day at beach")
            val embedding2 = generator.generateFromText("cold night in mountains")

            assertThat(embedding1.toList()).isNotEqualTo(embedding2.toList())
        }

    @Test
    fun `identical texts with repeated words produce same embedding`() =
        runTest {
            val embedding1 = generator.generateFromText("test test test")
            val embedding2 = generator.generateFromText("test test test")

            assertThat(embedding1.toList()).isEqualTo(embedding2.toList())
        }

    // ==================== Image Embedding Tests ====================

    @Test
    fun `generateFromImage produces embedding with correct dimension`() =
        runTest {
            val mockBitmap = mockk<Bitmap>(relaxed = true)
            val mockLabels =
                listOf(
                    createMockImageLabel("cat", 0.9f),
                    createMockImageLabel("animal", 0.8f),
                )

            setupMockImageLabeler(mockLabels)

            val embedding = generator.generateFromImage(mockBitmap)

            assertThat(embedding.size).isEqualTo(128)
        }

    @Test
    fun `generateFromImage with no labels produces zero embedding`() =
        runTest {
            val mockBitmap = mockk<Bitmap>(relaxed = true)

            setupMockImageLabeler(emptyList())

            val embedding = generator.generateFromImage(mockBitmap)

            assertThat(embedding.size).isEqualTo(128)
            assertThat(embedding.all { it == 0f }).isTrue()
        }

    @Test
    fun `generateFromImage normalizes label text to lowercase`() =
        runTest {
            val mockBitmap = mockk<Bitmap>(relaxed = true)
            val mockLabelsUpperCase =
                listOf(
                    createMockImageLabel("CAT", 0.9f),
                    createMockImageLabel("ANIMAL", 0.8f),
                )

            setupMockImageLabeler(mockLabelsUpperCase)
            val embeddingFromUpperCase = generator.generateFromImage(mockBitmap)

            // Reset and test with lowercase
            val mockLabelsLowerCase =
                listOf(
                    createMockImageLabel("cat", 0.9f),
                    createMockImageLabel("animal", 0.8f),
                )

            setupMockImageLabeler(mockLabelsLowerCase)
            val embeddingFromLowerCase = generator.generateFromImage(mockBitmap)

            // Both should produce the same embedding due to lowercase normalization
            assertThat(embeddingFromUpperCase.toList()).isEqualTo(embeddingFromLowerCase.toList())
        }

    // ==================== URI Embedding Tests ====================

    @Test
    fun `generateFromUri returns zero embedding for null bitmap`() =
        runTest {
            val mockUri = mockk<Uri>()

            every { mockContentResolver.openInputStream(mockUri) } returns null

            mockkStatic(BitmapFactory::class)
            every { BitmapFactory.decodeStream(null) } returns null

            val embedding = generator.generateFromUri(mockUri)

            assertThat(embedding.size).isEqualTo(128)
            assertThat(embedding.all { it == 0f }).isTrue()

            unmockkStatic(BitmapFactory::class)
        }

    // ==================== State Tests ====================

    @Test
    fun `isReady returns true`() =
        runTest {
            assertThat(generator.isReady()).isTrue()
        }

    @Test
    fun `initialize completes without error`() =
        runTest {
            // Should not throw
            generator.initialize()
        }

    @Test
    fun `close closes the image labeler`() {
        generator.close()

        verify { mockImageLabeler.close() }
    }

    // ==================== Helper Functions ====================

    private fun createMockImageLabel(
        text: String,
        confidence: Float,
    ): ImageLabel {
        return mockk<ImageLabel>().apply {
            every { this@apply.text } returns text
            every { this@apply.confidence } returns confidence
        }
    }

    private fun setupMockImageLabeler(labels: List<ImageLabel>) {
        val mockTask = mockk<Task<List<ImageLabel>>>(relaxed = true)

        every { mockImageLabeler.process(any<InputImage>()) } returns mockTask

        every { mockTask.addOnSuccessListener(any<OnSuccessListener<List<ImageLabel>>>()) } answers {
            val listener = firstArg<OnSuccessListener<List<ImageLabel>>>()
            listener.onSuccess(labels)
            mockTask
        }

        every { mockTask.addOnFailureListener(any<OnFailureListener>()) } returns mockTask
    }

    private fun cosineSimilarity(
        embedding1: FloatArray,
        embedding2: FloatArray,
    ): Float {
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0) dotProduct / denominator else 0f
    }
}
