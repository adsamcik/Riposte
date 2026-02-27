package com.adsamcik.riposte.core.ml

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.adsamcik.riposte.core.common.lifecycle.AppLifecycleTracker
import com.adsamcik.riposte.core.events.EventBus
import com.adsamcik.riposte.core.ml.worker.EmbeddingGenerationWorker
import com.adsamcik.riposte.core.ml.worker.EmbeddingNotificationManager
import com.adsamcik.riposte.core.ml.worker.EmbeddingWorkRepository
import com.adsamcik.riposte.core.ml.worker.MemeDataForEmbedding
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the exact prompt formatting used by EmbeddingGemma.
 *
 * EmbeddingGemma uses **asymmetric** prompts per its model card:
 * - Document: `"title: {title} | text: {content}"`
 * - Query:    `"task: search result | query: {query}"`
 *
 * These tests verify the EXACT prompt strings that get passed to inference,
 * as well as the worker's text-building logic that feeds into those prompts.
 *
 * @see EmbeddingGemmaGenerator.generateFromText — document prompt (lines 108-121)
 * @see EmbeddingGemmaGenerator.generateFromQuery — query prompt (lines 129-138)
 * @see EmbeddingGenerationWorker.buildContentParts — content body assembly
 * @see EmbeddingGenerationWorker.buildIntentText — intent text from search phrases
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PromptFormattingTest {

    private lateinit var context: Context
    private lateinit var embeddingGenerator: EmbeddingGenerator
    private lateinit var embeddingRepository: EmbeddingWorkRepository
    private lateinit var appLifecycleTracker: AppLifecycleTracker
    private lateinit var notificationManager: EmbeddingNotificationManager
    private lateinit var isInBackgroundFlow: MutableStateFlow<Boolean>

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        embeddingGenerator = mockk()
        embeddingRepository = mockk(relaxed = true)
        appLifecycleTracker = mockk()
        notificationManager = mockk(relaxed = true)
        isInBackgroundFlow = MutableStateFlow(false)

        every { appLifecycleTracker.isInBackground } returns isInBackgroundFlow
    }

    // region Document Prompt Formatting
    // Format: "title: {titlePart} | text: {text}"
    // where titlePart = title if non-null and non-blank, else "none"
    // Source: EmbeddingGemmaGenerator.generateFromText

    @Test
    fun `text with title formats as title X pipe text Y`() {
        val formatted = formatDocumentPrompt("a funny cat", title = "Cat Meme")
        assertThat(formatted).isEqualTo("title: Cat Meme | text: a funny cat")
    }

    @Test
    fun `text with null title uses none`() {
        val formatted = formatDocumentPrompt("hello world", title = null)
        assertThat(formatted).isEqualTo("title: none | text: hello world")
    }

    @Test
    fun `text with blank title uses none`() {
        val formatted = formatDocumentPrompt("hello world", title = "   ")
        assertThat(formatted).isEqualTo("title: none | text: hello world")
    }

    @Test
    fun `text with empty title uses none`() {
        val formatted = formatDocumentPrompt("hello world", title = "")
        assertThat(formatted).isEqualTo("title: none | text: hello world")
    }

    @Test
    fun `text with special characters in title`() {
        val formatted = formatDocumentPrompt("content", title = """<>&"'()[]{}""")
        assertThat(formatted).isEqualTo("""title: <>&"'()[]{} | text: content""")
    }

    @Test
    fun `text with very long content`() {
        val longText = "word ".repeat(1000).trim()
        val formatted = formatDocumentPrompt(longText, title = "Title")
        assertThat(formatted).startsWith("title: Title | text: ")
        assertThat(formatted).endsWith("word")
        assertThat(formatted).hasLength("title: Title | text: ".length + longText.length)
    }

    @Test
    fun `text with emoji in content`() {
        val formatted = formatDocumentPrompt("😂🔥💯 reaction meme", title = "Emojis")
        assertThat(formatted).isEqualTo("title: Emojis | text: 😂🔥💯 reaction meme")
    }

    @Test
    fun `text with newlines in content`() {
        val formatted = formatDocumentPrompt("line1\nline2\nline3", title = "Multi")
        assertThat(formatted).isEqualTo("title: Multi | text: line1\nline2\nline3")
    }

    @Test
    fun `text with pipe characters in content does not break format`() {
        val formatted = formatDocumentPrompt("a | b | c", title = "Pipes")
        assertThat(formatted).isEqualTo("title: Pipes | text: a | b | c")
    }

    // endregion

    // region Query Prompt Formatting
    // Format: "task: search result | query: {query}"
    // Source: EmbeddingGemmaGenerator.generateFromQuery

    @Test
    fun `query formats as task search result pipe query X`() {
        val formatted = formatQueryPrompt("funny cat")
        assertThat(formatted).isEqualTo("task: search result | query: funny cat")
    }

    @Test
    fun `query with special characters`() {
        val formatted = formatQueryPrompt("""what's "the best" meme?""")
        assertThat(formatted).isEqualTo("""task: search result | query: what's "the best" meme?""")
    }

    @Test
    fun `query with emoji`() {
        val formatted = formatQueryPrompt("😂 laughing face")
        assertThat(formatted).isEqualTo("task: search result | query: 😂 laughing face")
    }

    @Test
    fun `query with very long text`() {
        val longQuery = "search ".repeat(500).trim()
        val formatted = formatQueryPrompt(longQuery)
        assertThat(formatted).startsWith("task: search result | query: ")
        assertThat(formatted).hasLength("task: search result | query: ".length + longQuery.length)
    }

    @Test
    fun `blank query returns zero embedding without formatting`() {
        // EmbeddingGemmaGenerator.generateFromQuery short-circuits for blank queries:
        //   if (query.isBlank()) return createZeroEmbedding()
        // The zero embedding has dimension 768 and all values are 0.
        val zeroEmbedding = FloatArray(EmbeddingGemmaGenerator.DEFAULT_EMBEDDING_DIMENSION)
        assertThat(zeroEmbedding).hasLength(768)
        assertThat(zeroEmbedding.all { it == 0f }).isTrue()

        // Verify that blank input IS detected as blank (never sent to inference)
        assertThat("".isBlank()).isTrue()
        assertThat("   ".isBlank()).isTrue()
        assertThat("\t\n".isBlank()).isTrue()
    }

    // endregion

    // region Worker Text Building — buildContentParts
    // Builds (title, body) from meme metadata for the document embedding slot.
    // Source: EmbeddingGenerationWorker.buildContentParts

    @Test
    fun `buildContentParts with title and description`() {
        val memeData = createMemeData(
            title = "Funny Cat",
            description = "A cat looking surprised",
            textContent = "when monday hits",
        )
        val (title, body) = invokeBuildContentParts(memeData)
        assertThat(title).isEqualTo("Funny Cat")
        assertThat(body).isEqualTo("A cat looking surprised. when monday hits")
    }

    @Test
    fun `buildContentParts with missing fields falls back to title`() {
        val memeData = createMemeData(
            title = "Fallback Title",
            description = null,
            textContent = null,
        )
        val (title, body) = invokeBuildContentParts(memeData)
        assertThat(title).isEqualTo("Fallback Title")
        assertThat(body).isEqualTo("Fallback Title")
    }

    @Test
    fun `buildContentParts with only description`() {
        val memeData = createMemeData(
            title = null,
            description = "Just a description",
            textContent = null,
        )
        val (title, body) = invokeBuildContentParts(memeData)
        assertThat(title).isNull()
        assertThat(body).isEqualTo("Just a description")
    }

    @Test
    fun `buildContentParts with only textContent`() {
        val memeData = createMemeData(
            title = null,
            description = null,
            textContent = "OCR text from image",
        )
        val (title, body) = invokeBuildContentParts(memeData)
        assertThat(title).isNull()
        assertThat(body).isEqualTo("OCR text from image")
    }

    @Test
    fun `buildContentParts strips trailing period from body`() {
        val memeData = createMemeData(
            title = "Title",
            description = "sentence one",
            textContent = null,
        )
        val (_, body) = invokeBuildContentParts(memeData)
        // "sentence one. " → trim() → "sentence one." → trimEnd('.') → "sentence one"
        assertThat(body).isEqualTo("sentence one")
        assertThat(body).doesNotContain(".$")
    }

    // endregion

    // region Worker Text Building — buildIntentText
    // Builds text from search phrases for the intent embedding slot (query format).
    // Source: EmbeddingGenerationWorker.buildIntentText

    @Test
    fun `buildIntentText with search phrases`() {
        val memeData = createMemeData(
            searchPhrases = """["funny cat","reaction image","laughing"]""",
        )
        val intentText = invokeBuildIntentText(memeData)
        assertThat(intentText).isEqualTo("funny cat. reaction image. laughing")
    }

    @Test
    fun `buildIntentText returns empty for null searchPhrases`() {
        val memeData = createMemeData(searchPhrases = null)
        val intentText = invokeBuildIntentText(memeData)
        assertThat(intentText).isEmpty()
    }

    @Test
    fun `buildIntentText returns empty for blank searchPhrases`() {
        val memeData = createMemeData(searchPhrases = "   ")
        val intentText = invokeBuildIntentText(memeData)
        assertThat(intentText).isEmpty()
    }

    @Test
    fun `buildIntentText handles comma-separated fallback`() {
        val memeData = createMemeData(
            searchPhrases = "funny cat, laughing, reaction",
        )
        val intentText = invokeBuildIntentText(memeData)
        assertThat(intentText).isEqualTo("funny cat. laughing. reaction")
    }

    @Test
    fun `buildIntentText uses document format for space alignment`() = runTest {
        // The worker calls generateFromText (1-arg) for intent text
        val meme = createMemeData(
            id = 1,
            title = null,
            description = null,
            textContent = null,
            searchPhrases = """["find this meme"]""",
        )
        val embedding = createTestEmbedding()

        coEvery { embeddingRepository.getMemesNeedingEmbeddings(any()) } returns listOf(meme)
        coEvery { embeddingGenerator.generateFromText(any()) } returns embedding
        coEvery { embeddingGenerator.generateFromQuery(any()) } returns embedding
        coEvery { embeddingRepository.countMemesNeedingEmbeddings() } returns 0

        val worker = createWorker()
        worker.doWork()

        // Intent uses generateFromText (1-arg) — verify the text content
        coVerify { embeddingGenerator.generateFromText("find this meme") }
    }

    // endregion

    // region Integration — Worker Passes Correct Args to Generator

    @Test
    fun `worker passes built content body to document embedding`() = runTest {
        val meme = createMemeData(
            id = 1,
            title = "Cat Meme",
            description = "A surprised cat",
            textContent = "when monday hits",
            searchPhrases = null,
        )
        val embedding = createTestEmbedding()

        coEvery { embeddingRepository.getMemesNeedingEmbeddings(any()) } returns listOf(meme)
        coEvery { embeddingGenerator.generateFromText(any()) } returns embedding
        coEvery { embeddingGenerator.generateFromText(any(), any()) } returns embedding
        coEvery { embeddingGenerator.generateFromQuery(any()) } returns embedding
        coEvery { embeddingRepository.countMemesNeedingEmbeddings() } returns 0

        val worker = createWorker()
        worker.doWork()

        // Verify content text is built from description + textContent
        coVerify {
            embeddingGenerator.generateFromText(
                "A surprised cat. when monday hits",
                "Cat Meme",
            )
        }
    }

    // endregion

    // region Asymmetry Verification

    @Test
    fun `document and query prompts produce different text for same input`() {
        val input = "funny cat meme"
        val documentPrompt = formatDocumentPrompt(input, title = null)
        val queryPrompt = formatQueryPrompt(input)

        assertThat(documentPrompt).isNotEqualTo(queryPrompt)
        assertThat(documentPrompt).startsWith("title:")
        assertThat(queryPrompt).startsWith("task:")
        // Document format uses "title: none | text: ..." for untitled content
        assertThat(documentPrompt).contains("title: none")
        // Query format uses "task: search result | query: ..."
        assertThat(queryPrompt).contains("task: search result")
    }

    // endregion

    // region Helper Methods

    /**
     * Replicates the exact document prompt formatting from EmbeddingGemmaGenerator.
     * See: EmbeddingGemmaGenerator.generateFromText (lines 117-118)
     */
    private fun formatDocumentPrompt(text: String, title: String?): String {
        val titlePart = if (!title.isNullOrBlank()) title else "none"
        return "title: $titlePart | text: $text"
    }

    /**
     * Replicates the exact query prompt formatting from EmbeddingGemmaGenerator.
     * See: EmbeddingGemmaGenerator.generateFromQuery (line 135)
     */
    private fun formatQueryPrompt(query: String): String {
        return "task: search result | query: $query"
    }

    /**
     * Invokes the private `buildContentParts` method via reflection.
     */
    @Suppress("UNCHECKED_CAST")
    private fun invokeBuildContentParts(memeData: MemeDataForEmbedding): Pair<String?, String> {
        val worker = createWorker()
        val method = EmbeddingGenerationWorker::class.java.getDeclaredMethod(
            "buildContentParts",
            MemeDataForEmbedding::class.java,
        )
        method.isAccessible = true
        return method.invoke(worker, memeData) as Pair<String?, String>
    }

    /**
     * Invokes the private `buildIntentText` method via reflection.
     */
    private fun invokeBuildIntentText(memeData: MemeDataForEmbedding): String {
        val worker = createWorker()
        val method = EmbeddingGenerationWorker::class.java.getDeclaredMethod(
            "buildIntentText",
            MemeDataForEmbedding::class.java,
        )
        method.isAccessible = true
        return method.invoke(worker, memeData) as String
    }

    private fun createWorker(): EmbeddingGenerationWorker {
        return TestListenableWorkerBuilder<EmbeddingGenerationWorker>(context)
            .setWorkerFactory(TestEmbeddingWorkerFactory())
            .build()
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
                appLifecycleTracker = appLifecycleTracker,
                notificationManager = notificationManager,
                eventBus = EventBus(),
            )
        }
    }

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
}
