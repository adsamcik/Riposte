package com.adsamcik.riposte.core.search.data

import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.model.SearchStrategy
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SearchOrchestratorTest {

    @Test
    fun `search returns empty for blank query`() = runTest {
        val orchestrator = SearchOrchestrator(
            strategies = setOf(fakeStrategy("fts", priority = 100, available = true)),
        )

        val results = orchestrator.search("   ")

        assertThat(results).isEmpty()
    }

    @Test
    fun `search returns empty when no strategies available`() = runTest {
        val orchestrator = SearchOrchestrator(
            strategies = setOf(fakeStrategy("fts", priority = 100, available = false)),
        )

        val results = orchestrator.search("funny")

        assertThat(results).isEmpty()
    }

    @Test
    fun `search delegates to single available strategy`() = runTest {
        val meme = createTestMeme(1)
        val strategy = fakeStrategy(
            name = "fts",
            priority = 100,
            available = true,
            results = listOf(
                SearchResult(meme = meme, relevanceScore = 0.9f, matchType = MatchType.TEXT),
            ),
        )
        val orchestrator = SearchOrchestrator(strategies = setOf(strategy))

        val results = orchestrator.search("funny")

        assertThat(results).hasSize(1)
        assertThat(results[0].meme.id).isEqualTo(1L)
    }

    @Test
    fun `search skips unavailable strategies`() = runTest {
        val meme = createTestMeme(1)
        val available = fakeStrategy(
            name = "fts",
            priority = 100,
            available = true,
            results = listOf(
                SearchResult(meme = meme, relevanceScore = 0.9f, matchType = MatchType.TEXT),
            ),
        )
        val unavailable = fakeStrategy(
            name = "semantic",
            priority = 200,
            available = false,
            results = listOf(
                SearchResult(
                    meme = createTestMeme(2),
                    relevanceScore = 0.8f,
                    matchType = MatchType.SEMANTIC,
                ),
            ),
        )
        val orchestrator = SearchOrchestrator(strategies = setOf(available, unavailable))

        val results = orchestrator.search("funny")

        assertThat(results).hasSize(1)
        assertThat(results[0].meme.id).isEqualTo(1L)
    }

    @Test
    fun `search fuses results from multiple strategies via RRF`() = runTest {
        val meme1 = createTestMeme(1)
        val meme2 = createTestMeme(2)
        val fts = fakeStrategy(
            name = "fts",
            priority = 100,
            available = true,
            results = listOf(
                SearchResult(meme = meme1, relevanceScore = 0.9f, matchType = MatchType.TEXT),
            ),
        )
        val semantic = fakeStrategy(
            name = "semantic",
            priority = 200,
            available = true,
            results = listOf(
                SearchResult(meme = meme2, relevanceScore = 0.8f, matchType = MatchType.SEMANTIC),
            ),
        )
        val orchestrator = SearchOrchestrator(strategies = setOf(fts, semantic))

        val results = orchestrator.search("funny")

        assertThat(results).hasSize(2)
        // Meme2 from higher-priority semantic strategy should rank first
        assertThat(results[0].meme.id).isEqualTo(2L)
        assertThat(results[1].meme.id).isEqualTo(1L)
    }

    @Test
    fun `search handles strategy failure gracefully`() = runTest {
        val meme = createTestMeme(1)
        val failing = fakeStrategy(
            name = "semantic",
            priority = 200,
            available = true,
            throwOnSearch = true,
        )
        val working = fakeStrategy(
            name = "fts",
            priority = 100,
            available = true,
            results = listOf(
                SearchResult(meme = meme, relevanceScore = 0.9f, matchType = MatchType.TEXT),
            ),
        )
        val orchestrator = SearchOrchestrator(strategies = setOf(failing, working))

        val results = orchestrator.search("funny")

        assertThat(results).hasSize(1)
        assertThat(results[0].meme.id).isEqualTo(1L)
    }

    @Test
    fun `RRF fusion merges duplicate memes with HYBRID match type`() = runTest {
        val meme = createTestMeme(1)
        val fts = fakeStrategy(
            name = "fts",
            priority = 100,
            available = true,
            results = listOf(
                SearchResult(meme = meme, relevanceScore = 0.9f, matchType = MatchType.TEXT),
            ),
        )
        val semantic = fakeStrategy(
            name = "semantic",
            priority = 200,
            available = true,
            results = listOf(
                SearchResult(meme = meme, relevanceScore = 0.8f, matchType = MatchType.SEMANTIC),
            ),
        )
        val orchestrator = SearchOrchestrator(strategies = setOf(fts, semantic))

        val results = orchestrator.search("funny")

        assertThat(results).hasSize(1)
        assertThat(results[0].meme.id).isEqualTo(1L)
        assertThat(results[0].matchType).isEqualTo(MatchType.HYBRID)
    }

    @Test
    fun `higher priority strategy gets more weight in fusion`() = runTest {
        val meme1 = createTestMeme(1)
        val meme2 = createTestMeme(2)
        val lowPriority = fakeStrategy(
            name = "low",
            priority = 50,
            available = true,
            results = listOf(
                SearchResult(meme = meme1, relevanceScore = 1.0f, matchType = MatchType.TEXT),
            ),
        )
        val highPriority = fakeStrategy(
            name = "high",
            priority = 200,
            available = true,
            results = listOf(
                SearchResult(meme = meme2, relevanceScore = 0.5f, matchType = MatchType.SEMANTIC),
            ),
        )
        val orchestrator = SearchOrchestrator(strategies = setOf(lowPriority, highPriority))

        val results = orchestrator.search("funny")

        assertThat(results).hasSize(2)
        // Higher priority strategy result should rank first despite lower relevanceScore
        assertThat(results[0].meme.id).isEqualTo(2L)
        assertThat(results[0].relevanceScore).isGreaterThan(results[1].relevanceScore)
    }

    // region Helpers

    private fun createTestMeme(id: Long): Meme = Meme(
        id = id,
        filePath = "/test/path/meme$id.jpg",
        fileName = "meme$id.jpg",
        mimeType = "image/jpeg",
        width = 0,
        height = 0,
        fileSizeBytes = 0,
        importedAt = 0,
        emojiTags = emptyList(),
    )

    private fun fakeStrategy(
        name: String,
        priority: Int,
        available: Boolean,
        results: List<SearchResult> = emptyList(),
        throwOnSearch: Boolean = false,
    ): SearchStrategy = object : SearchStrategy {
        override val name = name
        override val priority = priority
        override fun isAvailable() = available
        override suspend fun search(query: String, limit: Int): List<SearchResult> {
            if (throwOnSearch) throw RuntimeException("Strategy $name failed")
            return results
        }
    }

    // endregion
}
