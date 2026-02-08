package com.adsamcik.riposte.core.ml

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for ML components working together.
 *
 * Tests verify:
 * - Text extraction → Embedding generation pipeline
 * - Semantic search with real embeddings
 * - End-to-end ML workflow
 */
@RunWith(AndroidJUnit4::class)
class MlIntegrationTest {

    private lateinit var context: Context
    private lateinit var textRecognizer: MlKitTextRecognizer
    private lateinit var embeddingGenerator: EmbeddingGenerator
    private lateinit var semanticSearchEngine: SemanticSearchEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        textRecognizer = MlKitTextRecognizer()
        embeddingGenerator = SimpleEmbeddingGenerator(context)
        semanticSearchEngine = DefaultSemanticSearchEngine(embeddingGenerator)
    }

    // ============ Text Extraction Tests ============

    @Test
    fun textRecognizer_extractsTextFromBitmap() = runTest {
        // Create a simple test bitmap (blank for basic test)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        
        val result = textRecognizer.recognizeText(bitmap)
        
        // Blank image should return empty text, but not throw
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun textRecognizer_handlesLargeBitmap() = runTest {
        // Create a large bitmap
        val bitmap = Bitmap.createBitmap(2000, 2000, Bitmap.Config.ARGB_8888)
        
        val result = textRecognizer.recognizeText(bitmap)
        
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun textRecognizer_handlesSmallBitmap() = runTest {
        // Create a very small bitmap
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        
        val result = textRecognizer.recognizeText(bitmap)
        
        assertThat(result.isSuccess).isTrue()
    }

    // ============ Embedding Generation Tests ============

    @Test
    fun embeddingGenerator_generatesEmbeddingForText() = runTest {
        val text = "A funny cat meme"
        
        val embedding = embeddingGenerator.generateEmbedding(text)
        
        assertThat(embedding).isNotNull()
        assertThat(embedding.size).isGreaterThan(0)
    }

    @Test
    fun embeddingGenerator_generatesConsistentEmbeddings() = runTest {
        val text = "Test text for embedding"
        
        val embedding1 = embeddingGenerator.generateEmbedding(text)
        val embedding2 = embeddingGenerator.generateEmbedding(text)
        
        assertThat(embedding1).isEqualTo(embedding2)
    }

    @Test
    fun embeddingGenerator_differentTextsDifferentEmbeddings() = runTest {
        val text1 = "Happy celebration party"
        val text2 = "Sad crying moment"
        
        val embedding1 = embeddingGenerator.generateEmbedding(text1)
        val embedding2 = embeddingGenerator.generateEmbedding(text2)
        
        assertThat(embedding1).isNotEqualTo(embedding2)
    }

    @Test
    fun embeddingGenerator_handlesEmptyText() = runTest {
        val embedding = embeddingGenerator.generateEmbedding("")
        
        assertThat(embedding).isNotNull()
    }

    @Test
    fun embeddingGenerator_handlesLongText() = runTest {
        val longText = "This is a very long text ".repeat(100)
        
        val embedding = embeddingGenerator.generateEmbedding(longText)
        
        assertThat(embedding).isNotNull()
        assertThat(embedding.size).isGreaterThan(0)
    }

    // ============ Semantic Search Tests ============

    @Test
    fun semanticSearch_findsSimilarContent() = runTest {
        // Set up index with test content
        val testContent = mapOf(
            1L to "Funny cat meme with hilarious expression",
            2L to "Sad dog looking for treats",
            3L to "Happy celebration party time",
            4L to "Funny kitten playing with yarn"
        )
        
        testContent.forEach { (id, text) ->
            semanticSearchEngine.indexMeme(id, text)
        }
        
        // Search for similar content
        val results = semanticSearchEngine.search("cat", limit = 10)
        
        assertThat(results).isNotEmpty()
        // Cat-related memes should rank higher
        val topIds = results.take(2).map { it.memeId }
        assertThat(topIds).containsAnyOf(1L, 4L)
    }

    @Test
    fun semanticSearch_returnsRelevanceScores() = runTest {
        val testContent = mapOf(
            1L to "Mountain landscape",
            2L to "Beach sunset",
            3L to "City skyline"
        )
        
        testContent.forEach { (id, text) ->
            semanticSearchEngine.indexMeme(id, text)
        }
        
        val results = semanticSearchEngine.search("beach", limit = 10)
        
        assertThat(results).isNotEmpty()
        // All results should have relevance scores between 0 and 1
        results.forEach { result ->
            assertThat(result.score).isAtLeast(0.0f)
            assertThat(result.score).isAtMost(1.0f)
        }
    }

    @Test
    fun semanticSearch_respectsLimit() = runTest {
        // Add many items
        (1L..20L).forEach { id ->
            semanticSearchEngine.indexMeme(id, "Test content $id")
        }
        
        val results = semanticSearchEngine.search("test", limit = 5)
        
        assertThat(results.size).isAtMost(5)
    }

    @Test
    fun semanticSearch_emptyQueryReturnsEmpty() = runTest {
        semanticSearchEngine.indexMeme(1L, "Test content")
        
        val results = semanticSearchEngine.search("", limit = 10)
        
        assertThat(results).isEmpty()
    }

    @Test
    fun semanticSearch_removesFromIndex() = runTest {
        semanticSearchEngine.indexMeme(1L, "Cat meme")
        semanticSearchEngine.indexMeme(2L, "Dog meme")
        
        // Remove one
        semanticSearchEngine.removeFromIndex(1L)
        
        val results = semanticSearchEngine.search("cat", limit = 10)
        
        // Cat meme should not be in results
        assertThat(results.map { it.memeId }).doesNotContain(1L)
    }

    // ============ Pipeline Integration Tests ============

    @Test
    fun fullPipeline_extractsAndIndexesContent() = runTest {
        // Create bitmap
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        
        // Extract text (will be empty for blank bitmap)
        val textResult = textRecognizer.recognizeText(bitmap)
        assertThat(textResult.isSuccess).isTrue()
        
        // Generate embedding for extracted text (or fallback text)
        val text = textResult.getOrNull() ?: "Default text"
        val embedding = embeddingGenerator.generateEmbedding(text)
        assertThat(embedding).isNotNull()
        
        // Index content
        semanticSearchEngine.indexMeme(1L, text)
        
        // Search should work
        val results = semanticSearchEngine.search("default", limit = 10)
        assertThat(results).isNotNull()
    }

    @Test
    fun fullPipeline_handlesMultipleMemes() = runTest {
        val memeTexts = listOf(
            1L to "Laughing cat",
            2L to "Crying dog",
            3L to "Dancing bird",
            4L to "Sleeping cat",
            5L to "Happy dog"
        )
        
        // Index all memes
        memeTexts.forEach { (id, text) ->
            val embedding = embeddingGenerator.generateEmbedding(text)
            assertThat(embedding).isNotNull()
            semanticSearchEngine.indexMeme(id, text)
        }
        
        // Search for cats
        val catResults = semanticSearchEngine.search("cat", limit = 10)
        val catIds = catResults.map { it.memeId }
        assertThat(catIds).containsAnyOf(1L, 4L)
        
        // Search for dogs
        val dogResults = semanticSearchEngine.search("dog", limit = 10)
        val dogIds = dogResults.map { it.memeId }
        assertThat(dogIds).containsAnyOf(2L, 5L)
    }

    // ============ Performance Tests ============

    @Test
    fun performance_indexingManyMemes() = runTest {
        val startTime = System.currentTimeMillis()
        
        // Index 100 memes
        (1L..100L).forEach { id ->
            semanticSearchEngine.indexMeme(id, "Test meme content $id with various words")
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // Should complete in reasonable time (under 10 seconds)
        assertThat(duration).isLessThan(10000)
    }

    @Test
    fun performance_searchWithManyIndexedItems() = runTest {
        // Index 100 memes first
        (1L..100L).forEach { id ->
            semanticSearchEngine.indexMeme(id, "Test meme content $id with various words")
        }
        
        val startTime = System.currentTimeMillis()
        
        // Perform 10 searches
        repeat(10) {
            semanticSearchEngine.search("test content", limit = 10)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // Each search should be fast (total under 5 seconds)
        assertThat(duration).isLessThan(5000)
    }
}
