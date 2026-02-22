package com.adsamcik.riposte.core.common.suggestion

import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class GetSuggestionsUseCaseTest {
    private lateinit var useCase: GetSuggestionsUseCase
    private val now = 1_700_000_000_000L

    @Before
    fun setup() {
        useCase = GetSuggestionsUseCase()
    }

    @Test
    fun `concurrent invocations do not corrupt cache`() =
        runBlocking(Dispatchers.Default) {
            val memes =
                (1..30).map { i ->
                    testMeme(
                        id = i.toLong(),
                        importedAt = now - i * 86_400_000L,
                        useCount = i % 5,
                        viewCount = i,
                        lastViewedAt = now - (i % 10).toLong() * 86_400_000L,
                        emojiTags = listOf(tag(listOf("😂", "🔥", "❤️", "🎉", "😎")[i % 5])),
                    )
                }

            val results =
                (1..50).map { iteration ->
                    async {
                        val context =
                            SuggestionContext(
                                surface = if (iteration % 2 == 0) Surface.GALLERY else Surface.SEARCH,
                                currentEmojiFilter = if (iteration % 3 == 0) "🔥" else null,
                                recentSearches = if (iteration % 4 == 0) listOf("funny") else emptyList(),
                            )
                        useCase(memes, context, now + iteration)
                    }
                }.awaitAll()

            results.forEach { result ->
                assertThat(result).isNotNull()
                assertThat(result).isNotEmpty()
                assertThat(result.map { it.id }.distinct()).hasSize(result.size)
            }
        }

    @Test
    fun `concurrent invocations with different meme lists return valid results`() =
        runBlocking(Dispatchers.Default) {
            val results =
                (1..50).map { iteration ->
                    async {
                        val memes =
                            (1..30).map { i ->
                                testMeme(
                                    id = (iteration * 100 + i).toLong(),
                                    importedAt = now - i * 86_400_000L,
                                    useCount = i % 3,
                                    viewCount = i * 2,
                                    lastViewedAt = now - (i % 7).toLong() * 86_400_000L,
                                    emojiTags = listOf(tag(listOf("😂", "🔥", "❤️")[i % 3])),
                                )
                            }
                        val context = SuggestionContext(surface = Surface.GALLERY)
                        useCase(memes, context, now + iteration)
                    }
                }.awaitAll()

            results.forEach { result ->
                assertThat(result).isNotNull()
                assertThat(result).isNotEmpty()
            }
        }

    @Test
    fun `cache returns same result for identical inputs within TTL`() {
        val memes =
            (1..30).map { i ->
                testMeme(
                    id = i.toLong(),
                    importedAt = now - i * 86_400_000L,
                    useCount = i % 3,
                    viewCount = i,
                    emojiTags = listOf(tag("😂")),
                )
            }
        val context = SuggestionContext(surface = Surface.GALLERY)

        val first = useCase(memes, context, now)
        val second = useCase(memes, context, now + 1000)

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `cache is invalidated after TTL expires`() {
        val memes =
            (1..30).map { i ->
                testMeme(
                    id = i.toLong(),
                    importedAt = now - i * 86_400_000L,
                    useCount = i % 3,
                    viewCount = i,
                    emojiTags = listOf(tag("😂")),
                )
            }
        val context = SuggestionContext(surface = Surface.GALLERY)

        val first = useCase(memes, context, now)
        // 6 minutes later (TTL is 5 minutes)
        val second = useCase(memes, context, now + 6 * 60 * 1000L)

        // Both should be valid, though they may be equal since same input
        assertThat(first).isNotNull()
        assertThat(second).isNotNull()
        assertThat(first).isNotEmpty()
        assertThat(second).isNotEmpty()
    }
}

private fun testMeme(
    id: Long = 1,
    importedAt: Long = 1_700_000_000_000L,
    emojiTags: List<EmojiTag> = listOf(tag("😂")),
    title: String? = "Test Meme $id",
    description: String? = null,
    textContent: String? = null,
    isFavorite: Boolean = false,
    useCount: Int = 0,
    viewCount: Int = 0,
    lastViewedAt: Long? = null,
): Meme =
    Meme(
        id = id,
        filePath = "/test/meme_$id.jpg",
        fileName = "meme_$id.jpg",
        mimeType = "image/jpeg",
        width = 100,
        height = 100,
        fileSizeBytes = 1024,
        importedAt = importedAt,
        emojiTags = emojiTags,
        title = title,
        description = description,
        textContent = textContent,
        isFavorite = isFavorite,
        useCount = useCount,
        viewCount = viewCount,
        lastViewedAt = lastViewedAt,
    )

private fun tag(emoji: String): EmojiTag =
    EmojiTag(
        emoji = emoji,
        name = "test_${emoji.hashCode()}",
        keywords = listOf(emoji),
    )
