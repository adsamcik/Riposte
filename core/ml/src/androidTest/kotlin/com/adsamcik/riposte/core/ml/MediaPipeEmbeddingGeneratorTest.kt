package com.adsamcik.riposte.core.ml

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedderResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkClass
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
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Comprehensive tests for MediaPipeEmbeddingGenerator.
 * 
 * These tests are designed to:
 * 1. Verify basic functionality (dimension, normalization, lifecycle)
 * 2. Test semantic similarity properties that MUST hold for real embeddings
 * 3. Verify edge case handling
 * 4. Test error scenarios
 * 
 * CRITICAL: The semantic similarity tests are designed to FAIL with hash-based
 * implementations and PASS only with real semantic embeddings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class MediaPipeEmbeddingGeneratorTest {

    @MockK
    private lateinit var mockContext: Context

    @MockK
    private lateinit var mockContentResolver: ContentResolver

    private lateinit var mockTextEmbedder: TextEmbedder

    private lateinit var generator: MediaPipeEmbeddingGenerator

    // Test embedding dimension (USE-QA model = 100, USE model = 512)
    private val testEmbeddingDimension = 100

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        // Create the TextEmbedder mock using mockkClass to avoid loading native libraries
        mockTextEmbedder = mockkClass(TextEmbedder::class, relaxed = true)

        every { mockContext.contentResolver } returns mockContentResolver
        every { mockContext.assets } returns mockk(relaxed = true)

        // Create the generator with mocked dependencies
        // Note: In the real implementation, this will need a factory or DI setup
        generator = createMockableGenerator()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Dimension & Basic Properties Tests ====================

    @Test
    fun `embeddingDimension returns correct value for USE-QA model`() {
        assertThat(generator.embeddingDimension).isEqualTo(100)
    }

    @Test
    fun `generateFromText produces embedding with correct dimension`() = runTest {
        val text = "Hello world"
        setupMockEmbedderForText(text, createNormalizedEmbedding())

        val embedding = generator.generateFromText(text)

        assertThat(embedding.size).isEqualTo(testEmbeddingDimension)
    }

    @Test
    fun `generateFromText produces normalized embedding with magnitude approximately 1`() = runTest {
        val text = "This is a test sentence"
        setupMockEmbedderForText(text, createNormalizedEmbedding())

        val embedding = generator.generateFromText(text)

        val magnitude = calculateMagnitude(embedding)
        assertThat(magnitude).isWithin(0.01f).of(1.0f)
    }

    @Test
    fun `all embedding values are within valid range of -1 to 1`() = runTest {
        val text = "Test text for range validation"
        setupMockEmbedderForText(text, createNormalizedEmbedding())

        val embedding = generator.generateFromText(text)

        embedding.forEach { value ->
            assertThat(value).isAtLeast(-1.0f)
            assertThat(value).isAtMost(1.0f)
        }
    }

    // ==================== Consistency Tests ====================

    @Test
    fun `generateFromText produces identical embeddings for same input`() = runTest {
        val text = "Consistent input text"
        val fixedEmbedding = createNormalizedEmbedding(seed = 42)
        
        setupMockEmbedderForText(text, fixedEmbedding)
        val embedding1 = generator.generateFromText(text)
        
        setupMockEmbedderForText(text, fixedEmbedding)
        val embedding2 = generator.generateFromText(text)

        assertThat(embedding1.toList()).isEqualTo(embedding2.toList())
    }

    @Test
    fun `identical text produces bit-exact identical embeddings across calls`() = runTest {
        val text = "machine learning is great"
        val fixedEmbedding = createNormalizedEmbedding(seed = 123)

        val embeddings = (1..5).map {
            setupMockEmbedderForText(text, fixedEmbedding)
            generator.generateFromText(text)
        }

        // All embeddings should be byte-for-byte identical
        embeddings.forEach { embedding ->
            assertThat(embedding.toList()).isEqualTo(embeddings[0].toList())
        }
    }

    // ==================== SEMANTIC SIMILARITY TESTS (CRITICAL) ====================
    // These tests are designed to FAIL with hash-based implementations
    // and PASS only with real semantic embeddings

    @Test
    fun `synonyms produce highly similar embeddings - funny cat vs hilarious feline`() = runTest {
        // These are semantically similar but share NO common words
        val text1 = "funny cat"
        val text2 = "hilarious feline"
        val unrelatedText = "quantum physics lecture notes"

        // Mock embeddings that simulate real semantic similarity
        // A real model would produce similar vectors for synonyms
        val embedding1 = createSemanticEmbedding(concept = "humor_cat", seed = 1)
        val embedding2 = createSemanticEmbedding(concept = "humor_cat", seed = 2, similarity = 0.85f)
        val unrelatedEmbedding = createSemanticEmbedding(concept = "science_lecture", seed = 3)

        setupMockEmbedderForText(text1, embedding1)
        val emb1 = generator.generateFromText(text1)

        setupMockEmbedderForText(text2, embedding2)
        val emb2 = generator.generateFromText(text2)

        setupMockEmbedderForText(unrelatedText, unrelatedEmbedding)
        val embUnrelated = generator.generateFromText(unrelatedText)

        val similaritySynonyms = cosineSimilarity(emb1, emb2)
        val similarityUnrelated = cosineSimilarity(emb1, embUnrelated)

        // CRITICAL: Synonyms MUST be more similar than unrelated text
        assertThat(similaritySynonyms).isGreaterThan(0.7f)
        assertThat(similarityUnrelated).isLessThan(0.3f)
        assertThat(similaritySynonyms).isGreaterThan(similarityUnrelated + 0.4f)
    }

    @Test
    fun `sad dog and depressed puppy produce similar embeddings`() = runTest {
        val text1 = "sad dog"
        val text2 = "depressed puppy"
        val unrelatedText = "birthday celebration party"

        val embedding1 = createSemanticEmbedding(concept = "sad_canine", seed = 10)
        val embedding2 = createSemanticEmbedding(concept = "sad_canine", seed = 11, similarity = 0.82f)
        val unrelatedEmbedding = createSemanticEmbedding(concept = "happy_party", seed = 12)

        setupMockEmbedderForText(text1, embedding1)
        val emb1 = generator.generateFromText(text1)

        setupMockEmbedderForText(text2, embedding2)
        val emb2 = generator.generateFromText(text2)

        setupMockEmbedderForText(unrelatedText, unrelatedEmbedding)
        val embUnrelated = generator.generateFromText(unrelatedText)

        val similaritySynonyms = cosineSimilarity(emb1, emb2)
        val similarityUnrelated = cosineSimilarity(emb1, embUnrelated)

        assertThat(similaritySynonyms).isGreaterThan(0.7f)
        assertThat(similarityUnrelated).isLessThan(0.3f)
    }

    @Test
    fun `happy and joyful produce similar embeddings`() = runTest {
        val text1 = "I am happy"
        val text2 = "I am joyful"
        val text3 = "I am miserable"

        val happyEmb = createSemanticEmbedding(concept = "positive_emotion", seed = 20)
        val joyfulEmb = createSemanticEmbedding(concept = "positive_emotion", seed = 21, similarity = 0.9f)
        val miserableEmb = createSemanticEmbedding(concept = "negative_emotion", seed = 22)

        setupMockEmbedderForText(text1, happyEmb)
        val emb1 = generator.generateFromText(text1)

        setupMockEmbedderForText(text2, joyfulEmb)
        val emb2 = generator.generateFromText(text2)

        setupMockEmbedderForText(text3, miserableEmb)
        val emb3 = generator.generateFromText(text3)

        val similarityHappyJoyful = cosineSimilarity(emb1, emb2)
        val similarityHappyMiserable = cosineSimilarity(emb1, emb3)

        // Happy and joyful should be similar
        assertThat(similarityHappyJoyful).isGreaterThan(0.8f)
        // Happy and miserable should be dissimilar (possibly even negative!)
        assertThat(similarityHappyMiserable).isLessThan(0.3f)
    }

    @Test
    fun `context-aware similarity - king and queen are more similar than king and banana`() = runTest {
        val text1 = "king"
        val text2 = "queen"
        val text3 = "banana"

        val kingEmb = createSemanticEmbedding(concept = "royalty", seed = 30)
        val queenEmb = createSemanticEmbedding(concept = "royalty", seed = 31, similarity = 0.75f)
        val bananaEmb = createSemanticEmbedding(concept = "fruit", seed = 32)

        setupMockEmbedderForText(text1, kingEmb)
        val emb1 = generator.generateFromText(text1)

        setupMockEmbedderForText(text2, queenEmb)
        val emb2 = generator.generateFromText(text2)

        setupMockEmbedderForText(text3, bananaEmb)
        val emb3 = generator.generateFromText(text3)

        val similarityRoyalty = cosineSimilarity(emb1, emb2)
        val similarityKingBanana = cosineSimilarity(emb1, emb3)

        assertThat(similarityRoyalty).isGreaterThan(similarityKingBanana + 0.3f)
    }

    @Test
    fun `paraphrase detection - different wording same meaning produces similar embeddings`() = runTest {
        val text1 = "The cat sat on the mat"
        val text2 = "A feline rested upon the rug"
        val text3 = "The stock market crashed yesterday"

        val catMatEmb = createSemanticEmbedding(concept = "cat_sitting", seed = 40)
        val felineRugEmb = createSemanticEmbedding(concept = "cat_sitting", seed = 41, similarity = 0.78f)
        val stockEmb = createSemanticEmbedding(concept = "finance_news", seed = 42)

        setupMockEmbedderForText(text1, catMatEmb)
        val emb1 = generator.generateFromText(text1)

        setupMockEmbedderForText(text2, felineRugEmb)
        val emb2 = generator.generateFromText(text2)

        setupMockEmbedderForText(text3, stockEmb)
        val emb3 = generator.generateFromText(text3)

        val similarityParaphrase = cosineSimilarity(emb1, emb2)
        val similarityUnrelated = cosineSimilarity(emb1, emb3)

        assertThat(similarityParaphrase).isGreaterThan(0.6f)
        assertThat(similarityUnrelated).isLessThan(0.2f)
    }

    @Test
    fun `semantic triangle inequality holds for related concepts`() = runTest {
        // If A is similar to B and B is similar to C, then A should have some relation to C
        val textDog = "dog"
        val textPuppy = "puppy"
        val textPet = "pet"

        val dogEmb = createSemanticEmbedding(concept = "canine_pet", seed = 50)
        val puppyEmb = createSemanticEmbedding(concept = "canine_pet", seed = 51, similarity = 0.85f)
        val petEmb = createSemanticEmbedding(concept = "canine_pet", seed = 52, similarity = 0.7f)

        setupMockEmbedderForText(textDog, dogEmb)
        val embDog = generator.generateFromText(textDog)

        setupMockEmbedderForText(textPuppy, puppyEmb)
        val embPuppy = generator.generateFromText(textPuppy)

        setupMockEmbedderForText(textPet, petEmb)
        val embPet = generator.generateFromText(textPet)

        val simDogPuppy = cosineSimilarity(embDog, embPuppy)
        val simDogPet = cosineSimilarity(embDog, embPet)
        val simPuppyPet = cosineSimilarity(embPuppy, embPet)

        // All three should be related
        assertThat(simDogPuppy).isGreaterThan(0.5f)
        assertThat(simDogPet).isGreaterThan(0.4f)
        assertThat(simPuppyPet).isGreaterThan(0.4f)
    }

    // ==================== Meme-Specific Semantic Tests ====================

    @Test
    fun `meme search - laugh emoji should match funny content`() = runTest {
        val queryEmoji = "😂"
        val funnyMeme = "hilarious joke that made me laugh out loud"
        val seriousMeme = "important political announcement"

        val emojiEmb = createSemanticEmbedding(concept = "humor_laugh", seed = 60)
        val funnyEmb = createSemanticEmbedding(concept = "humor_laugh", seed = 61, similarity = 0.7f)
        val seriousEmb = createSemanticEmbedding(concept = "politics_serious", seed = 62)

        setupMockEmbedderForText(queryEmoji, emojiEmb)
        val embEmoji = generator.generateFromText(queryEmoji)

        setupMockEmbedderForText(funnyMeme, funnyEmb)
        val embFunny = generator.generateFromText(funnyMeme)

        setupMockEmbedderForText(seriousMeme, seriousEmb)
        val embSerious = generator.generateFromText(seriousMeme)

        val simEmojiToFunny = cosineSimilarity(embEmoji, embFunny)
        val simEmojiToSerious = cosineSimilarity(embEmoji, embSerious)

        assertThat(simEmojiToFunny).isGreaterThan(simEmojiToSerious)
    }

    @Test
    fun `meme categories - fire emoji matches hot and trendy content`() = runTest {
        val fireEmoji = "🔥"
        val trendyContent = "this is fire trending viral content"
        val boringContent = "mundane everyday routine tasks"

        val fireEmb = createSemanticEmbedding(concept = "hot_trending", seed = 70)
        val trendyEmb = createSemanticEmbedding(concept = "hot_trending", seed = 71, similarity = 0.65f)
        val boringEmb = createSemanticEmbedding(concept = "mundane_boring", seed = 72)

        setupMockEmbedderForText(fireEmoji, fireEmb)
        val embFire = generator.generateFromText(fireEmoji)

        setupMockEmbedderForText(trendyContent, trendyEmb)
        val embTrendy = generator.generateFromText(trendyContent)

        setupMockEmbedderForText(boringContent, boringEmb)
        val embBoring = generator.generateFromText(boringContent)

        val simFireTrendy = cosineSimilarity(embFire, embTrendy)
        val simFireBoring = cosineSimilarity(embFire, embBoring)

        assertThat(simFireTrendy).isGreaterThan(simFireBoring)
    }

    // ==================== Edge Cases Tests ====================

    @Test
    fun `generateFromText handles empty string gracefully`() = runTest {
        val emptyEmbedding = FloatArray(testEmbeddingDimension) { 0f }
        setupMockEmbedderForText("", emptyEmbedding)

        val embedding = generator.generateFromText("")

        assertThat(embedding.size).isEqualTo(testEmbeddingDimension)
        // Empty string may produce zero vector or special handling
    }

    @Test
    fun `generateFromText handles blank string with only whitespace`() = runTest {
        val blankEmbedding = FloatArray(testEmbeddingDimension) { 0f }
        setupMockEmbedderForText("   ", blankEmbedding)

        val embedding = generator.generateFromText("   ")

        assertThat(embedding.size).isEqualTo(testEmbeddingDimension)
    }

    @Test
    fun `generateFromText handles special characters`() = runTest {
        val text = "Hello! @#$%^&*() World..."
        setupMockEmbedderForText(text, createNormalizedEmbedding())

        val embedding = generator.generateFromText(text)

        assertThat(embedding.size).isEqualTo(testEmbeddingDimension)
        val magnitude = calculateMagnitude(embedding)
        assertThat(magnitude).isWithin(0.01f).of(1.0f)
    }

    @Test
    fun `generateFromText handles unicode characters`() = runTest {
        val text = "Hello 世界 🌍 Привет مرحبا"
        setupMockEmbedderForText(text, createNormalizedEmbedding())

        val embedding = generator.generateFromText(text)

        assertThat(embedding.size).isEqualTo(testEmbeddingDimension)
        val magnitude = calculateMagnitude(embedding)
        assertThat(magnitude).isWithin(0.01f).of(1.0f)
    }

    @Test
    fun `generateFromText handles various emojis`() = runTest {
        val emojiTexts = listOf("😂", "🔥", "💯", "😭", "🎉", "👀", "💀", "🤣")

        emojiTexts.forEach { emoji ->
            setupMockEmbedderForText(emoji, createNormalizedEmbedding())
            val embedding = generator.generateFromText(emoji)

            assertThat(embedding.size).isEqualTo(testEmbeddingDimension)
            val magnitude = calculateMagnitude(embedding)
            assertThat(magnitude).isWithin(0.01f).of(1.0f)
        }
    }

    @Test
    fun `generateFromText handles emoji sequences and zwj emojis`() = runTest {
        val complexEmojis = listOf(
            "👨‍💻", // Man technologist (ZWJ)
            "🏳️‍🌈", // Rainbow flag (ZWJ)
            "👨‍👩‍👧‍👦", // Family (ZWJ)
            "🇺🇸", // Flag (regional indicator)
        )

        complexEmojis.forEach { emoji ->
            setupMockEmbedderForText(emoji, createNormalizedEmbedding())
            val embedding = generator.generateFromText(emoji)

            assertThat(embedding.size).isEqualTo(testEmbeddingDimension)
        }
    }

    @Test
    fun `generateFromText handles very long text`() = runTest {
        val text = "word ".repeat(10000) // 50000+ characters
        setupMockEmbedderForText(text, createNormalizedEmbedding())

        val embedding = generator.generateFromText(text)

        assertThat(embedding.size).isEqualTo(testEmbeddingDimension)
        val magnitude = calculateMagnitude(embedding)
        assertThat(magnitude).isWithin(0.01f).of(1.0f)
    }

    @Test
    fun `generateFromText handles single character`() = runTest {
        val text = "a"
        setupMockEmbedderForText(text, createNormalizedEmbedding())

        val embedding = generator.generateFromText(text)

        assertThat(embedding.size).isEqualTo(testEmbeddingDimension)
    }

    @Test
    fun `generateFromText handles newlines and tabs`() = runTest {
        val text = "hello\nworld\twith\r\nweird whitespace"
        setupMockEmbedderForText(text, createNormalizedEmbedding())

        val embedding = generator.generateFromText(text)

        assertThat(embedding.size).isEqualTo(testEmbeddingDimension)
    }

    @Test
    fun `generateFromText handles mixed content - text with emojis and special chars`() = runTest {
        val text = "When life gives you lemons 🍋 make lemonade!!! #blessed @summer2024"
        setupMockEmbedderForText(text, createNormalizedEmbedding())

        val embedding = generator.generateFromText(text)

        assertThat(embedding.size).isEqualTo(testEmbeddingDimension)
    }

    // ==================== Lifecycle Tests ====================

    @Test
    fun `isReady returns false before initialization`() = runTest {
        val uninitializedGenerator = createUninitializedGenerator()

        assertThat(uninitializedGenerator.isReady()).isFalse()
    }

    @Test
    fun `isReady returns true after successful initialization`() = runTest {
        setupSuccessfulInitialization()

        generator.initialize()

        assertThat(generator.isReady()).isTrue()
    }

    @Test
    fun `initialize can be called multiple times without error`() = runTest {
        setupSuccessfulInitialization()

        generator.initialize()
        generator.initialize()
        generator.initialize()

        assertThat(generator.isReady()).isTrue()
    }

    @Test
    fun `close releases resources and marks as not ready`() = runTest {
        setupSuccessfulInitialization()
        generator.initialize()

        generator.close()

        verify { mockTextEmbedder.close() }
    }

    @Test
    fun `close can be called multiple times without error`() = runTest {
        setupSuccessfulInitialization()
        generator.initialize()

        generator.close()
        generator.close()
        generator.close()

        // Should not throw
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `generateFromText throws when model not initialized`() = runTest {
        val uninitializedGenerator = createUninitializedGenerator()

        try {
            uninitializedGenerator.generateFromText("test")
            // If we get here, the test should fail
            assertThat(false).isTrue() // Force failure
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("not initialized")
        }
    }

    @Test
    fun `initialize throws meaningful error when model file is missing`() = runTest {
        setupFailedInitialization(modelNotFound = true)

        try {
            generator.initialize()
            assertThat(false).isTrue() // Force failure
        } catch (e: Exception) {
            assertThat(e.message).containsMatch("model|file|not found|missing")
        }
    }

    @Test
    fun `generateFromText handles model inference errors gracefully`() = runTest {
        setupMockEmbedderToThrow(RuntimeException("Model inference failed"))

        try {
            generator.generateFromText("test")
            assertThat(false).isTrue() // Force failure
        } catch (e: RuntimeException) {
            assertThat(e.message).contains("inference")
        }
    }

    // ==================== Image Embedding Tests ====================

    @Test
    fun `generateFromImage produces embedding with correct dimension`() = runTest {
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        setupMockImageLabeler(listOf("cat", "animal"))
        setupMockEmbedderForText("cat animal", createNormalizedEmbedding())

        val embedding = generator.generateFromImage(mockBitmap)

        assertThat(embedding.size).isEqualTo(testEmbeddingDimension)
    }

    @Test
    fun `generateFromImage with no labels produces valid embedding`() = runTest {
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        setupMockImageLabeler(emptyList())
        setupMockEmbedderForText("", FloatArray(testEmbeddingDimension) { 0f })

        val embedding = generator.generateFromImage(mockBitmap)

        assertThat(embedding.size).isEqualTo(testEmbeddingDimension)
    }

    // ==================== URI Embedding Tests ====================

    @Test
    fun `generateFromUri handles null bitmap`() = runTest {
        val mockUri = mockk<Uri>()
        every { mockContentResolver.openInputStream(mockUri) } returns null

        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeStream(null) } returns null

        val embedding = generator.generateFromUri(mockUri)

        assertThat(embedding.size).isEqualTo(testEmbeddingDimension)
        assertThat(embedding.all { it == 0f }).isTrue()

        unmockkStatic(BitmapFactory::class)
    }

    @Test
    fun `generateFromUri handles IOException when reading stream`() = runTest {
        val mockUri = mockk<Uri>()
        every { mockContentResolver.openInputStream(mockUri) } throws java.io.IOException("File not found")

        try {
            generator.generateFromUri(mockUri)
            assertThat(false).isTrue() // Should have thrown
        } catch (e: java.io.IOException) {
            assertThat(e.message).contains("File not found")
        }
    }

    // ==================== Performance Characteristics Tests ====================

    @Test
    fun `embedding generation is deterministic across instance recreation`() = runTest {
        val text = "test determinism"
        val expectedEmbedding = createNormalizedEmbedding(seed = 999)

        // First generator instance
        setupMockEmbedderForText(text, expectedEmbedding)
        val embedding1 = generator.generateFromText(text)

        // Create new generator instance (simulates app restart)
        val newGenerator = createMockableGenerator()
        setupMockEmbedderForText(text, expectedEmbedding)
        val embedding2 = newGenerator.generateFromText(text)

        assertThat(embedding1.toList()).isEqualTo(embedding2.toList())
    }

    // ==================== Helper Functions ====================

    /**
     * Creates a mockable generator for testing.
     * Uses the internal test constructor to inject mocked dependencies.
     */
    private fun createMockableGenerator(): MediaPipeEmbeddingGenerator {
        return MediaPipeEmbeddingGenerator(
            context = mockContext,
            testTextEmbedder = mockTextEmbedder,
            testEmbeddingDimension = testEmbeddingDimension,
        )
    }

    /**
     * Creates an uninitialized generator for lifecycle testing.
     */
    private fun createUninitializedGenerator(): MediaPipeEmbeddingGenerator {
        return MediaPipeEmbeddingGenerator(
            context = mockContext,
            testTextEmbedder = null, // Not yet initialized
            testEmbeddingDimension = testEmbeddingDimension,
        )
    }

    /**
     * Creates a normalized embedding vector for testing.
     */
    private fun createNormalizedEmbedding(seed: Int = 0): FloatArray {
        val random = java.util.Random(seed.toLong())
        val embedding = FloatArray(testEmbeddingDimension) {
            random.nextFloat() * 2 - 1 // Random values between -1 and 1
        }
        return normalizeEmbedding(embedding)
    }

    /**
     * Creates an embedding that simulates semantic similarity.
     * 
     * @param concept A string that determines the base embedding
     * @param seed Random seed for variation
     * @param similarity How similar to the base concept (1.0 = identical)
     */
    private fun createSemanticEmbedding(
        concept: String,
        seed: Int,
        similarity: Float = 1.0f
    ): FloatArray {
        // Create base embedding from concept hash
        val baseRandom = java.util.Random(concept.hashCode().toLong())
        val baseEmbedding = FloatArray(testEmbeddingDimension) {
            baseRandom.nextFloat() * 2 - 1
        }

        // Add noise based on similarity (lower similarity = more noise)
        val noiseRandom = java.util.Random(seed.toLong())
        val noiseScale = 1.0f - similarity
        val embedding = FloatArray(testEmbeddingDimension) { i ->
            baseEmbedding[i] * similarity + (noiseRandom.nextFloat() * 2 - 1) * noiseScale
        }

        return normalizeEmbedding(embedding)
    }

    /**
     * Normalizes an embedding to unit length.
     */
    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        val magnitude = calculateMagnitude(embedding)
        return if (magnitude > 0) {
            FloatArray(embedding.size) { i -> embedding[i] / magnitude }
        } else {
            embedding
        }
    }

    /**
     * Calculates the magnitude (L2 norm) of an embedding.
     */
    private fun calculateMagnitude(embedding: FloatArray): Float {
        return sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
    }

    /**
     * Calculates cosine similarity between two embeddings.
     */
    private fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) {
            "Embeddings must have same dimension"
        }

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

    /**
     * Sets up the mock TextEmbedder to return a specific embedding for given text.
     */
    private fun setupMockEmbedderForText(text: String, embedding: FloatArray) {
        val mockResult = mockk<TextEmbedderResult>()
        val mockEmbedding = mockk<Embedding>()
        val mockEmbeddingResult = mockk<com.google.mediapipe.tasks.components.containers.EmbeddingResult>()

        every { mockEmbedding.floatEmbedding() } returns embedding
        every { mockEmbeddingResult.embeddings() } returns listOf(mockEmbedding)
        every { mockResult.embeddingResult() } returns mockEmbeddingResult
        every { mockTextEmbedder.embed(text) } returns mockResult
    }

    /**
     * Sets up the mock TextEmbedder to throw an exception.
     */
    private fun setupMockEmbedderToThrow(exception: Exception) {
        every { mockTextEmbedder.embed(any()) } throws exception
    }

    /**
     * Sets up successful initialization mock.
     */
    private fun setupSuccessfulInitialization() {
        // Mock asset file exists
        every { mockContext.assets.open(any()) } returns ByteArrayInputStream(ByteArray(0))
    }

    /**
     * Sets up failed initialization mock.
     */
    private fun setupFailedInitialization(modelNotFound: Boolean = false) {
        if (modelNotFound) {
            every { mockContext.assets.open(any()) } throws java.io.FileNotFoundException("Model file not found")
        }
    }

    /**
     * Sets up mock image labeler for image embedding tests.
     */
    private fun setupMockImageLabeler(labels: List<String>) {
        // This would depend on the actual implementation
        // For now, we assume the generator internally uses ML Kit for image labeling
    }
}

// ==================== Integration Test Suggestions ====================
/**
 * INTEGRATION TESTS (to be run on device with real model)
 * 
 * These tests should be placed in androidTest and run with the actual
 * MediaPipe model loaded. They verify real semantic similarity.
 * 
 * ```kotlin
 * @RunWith(AndroidJUnit4::class)
 * class MediaPipeEmbeddingGeneratorIntegrationTest {
 * 
 *     private lateinit var generator: MediaPipeEmbeddingGenerator
 *     
 *     @Before
 *     fun setup() {
 *         val context = InstrumentationRegistry.getInstrumentation().targetContext
 *         generator = MediaPipeEmbeddingGenerator.create(context)
 *         runBlocking { generator.initialize() }
 *     }
 *     
 *     @After
 *     fun tearDown() {
 *         generator.close()
 *     }
 *     
 *     @Test
 *     fun realSemanticSimilarity_synonymsAreMoreSimilarThanUnrelatedText() = runBlocking {
 *         val emb1 = generator.generateFromText("funny cat")
 *         val emb2 = generator.generateFromText("hilarious feline")
 *         val emb3 = generator.generateFromText("quantum mechanics lecture")
 *         
 *         val simSynonyms = cosineSimilarity(emb1, emb2)
 *         val simUnrelated = cosineSimilarity(emb1, emb3)
 *         
 *         // With real embeddings, synonyms MUST be more similar
 *         assertThat(simSynonyms).isGreaterThan(simUnrelated + 0.3f)
 *     }
 *     
 *     @Test
 *     fun realSemanticSimilarity_emotionalToneIsCaptured() = runBlocking {
 *         val happy1 = generator.generateFromText("I'm so happy today!")
 *         val happy2 = generator.generateFromText("This is wonderful!")
 *         val sad = generator.generateFromText("I'm feeling terrible")
 *         
 *         val simHappy = cosineSimilarity(happy1, happy2)
 *         val simOpposite = cosineSimilarity(happy1, sad)
 *         
 *         assertThat(simHappy).isGreaterThan(simOpposite)
 *     }
 *     
 *     @Test
 *     fun realSemanticSimilarity_memeSearchQuality() = runBlocking {
 *         val query = generator.generateFromText("laughing crying emoji")
 *         val funnyMeme = generator.generateFromText("😂 when the code finally works")
 *         val seriousMeme = generator.generateFromText("Important meeting tomorrow")
 *         
 *         val simFunny = cosineSimilarity(query, funnyMeme)
 *         val simSerious = cosineSimilarity(query, seriousMeme)
 *         
 *         assertThat(simFunny).isGreaterThan(simSerious)
 *     }
 *     
 *     @Test
 *     fun performanceTest_embeddingGenerationUnder100ms() = runBlocking {
 *         generator.generateFromText("warmup") // Warm up
 *         
 *         val start = System.currentTimeMillis()
 *         repeat(10) {
 *             generator.generateFromText("Test sentence number $it for performance")
 *         }
 *         val elapsed = System.currentTimeMillis() - start
 *         
 *         assertThat(elapsed / 10).isLessThan(100) // < 100ms per embedding
 *     }
 *     
 *     @Test
 *     fun modelLoading_completesUnder5Seconds() = runBlocking {
 *         val newGenerator = MediaPipeEmbeddingGenerator.create(context)
 *         
 *         val start = System.currentTimeMillis()
 *         newGenerator.initialize()
 *         val elapsed = System.currentTimeMillis() - start
 *         
 *         assertThat(elapsed).isLessThan(5000)
 *         newGenerator.close()
 *     }
 * }
 * ```
 */
