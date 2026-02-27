package com.adsamcik.riposte.core.search.data

import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.model.SearchStrategy
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Integration tests for [SearchOrchestrator] with realistic multi-strategy fusion scenarios.
 */
class SearchPipelineIntegrationTest {

    // ==================== Dual Strategy Fusion ====================

    @Test
    fun `dual strategy fusion with overlapping meme marks it HYBRID`() = runTest {
        val memeA = testMeme(1L)
        val memeB = testMeme(2L)
        val memeC = testMeme(3L)
        val memeD = testMeme(4L)
        val memeE = testMeme(5L)

        val fts = fakeStrategy(
            name = "fts",
            priority = 100,
            available = true,
            results = listOf(
                SearchResult(meme = memeA, relevanceScore = 0.9f, matchType = MatchType.TEXT),
                SearchResult(meme = memeB, relevanceScore = 0.8f, matchType = MatchType.TEXT),
                SearchResult(meme = memeC, relevanceScore = 0.7f, matchType = MatchType.TEXT),
            ),
        )
        val semantic = fakeStrategy(
            name = "semantic",
            priority = 200,
            available = true,
            results = listOf(
                SearchResult(meme = memeC, relevanceScore = 0.85f, matchType = MatchType.SEMANTIC),
                SearchResult(meme = memeD, relevanceScore = 0.75f, matchType = MatchType.SEMANTIC),
                SearchResult(meme = memeE, relevanceScore = 0.65f, matchType = MatchType.SEMANTIC),
            ),
        )
        val orchestrator = SearchOrchestrator(strategies = setOf(fts, semantic))

        val results = orchestrator.search("funny cat")

        // Meme C appears in both → HYBRID
        val memeC_result = results.find { it.meme.id == 3L }
        assertThat(memeC_result).isNotNull()
        assertThat(memeC_result!!.matchType).isEqualTo(MatchType.HYBRID)

        // Meme C should have the highest score (contributions from both strategies)
        assertThat(results[0].meme.id).isEqualTo(3L)
    }

    @Test
    fun `semantic strategy with higher priority dominates RRF scores`() = runTest {
        val ftsOnlyMeme = testMeme(1L)
        val semanticOnlyMeme = testMeme(2L)

        val fts = fakeStrategy(
            name = "fts",
            priority = 100,
            available = true,
            results = listOf(
                SearchResult(meme = ftsOnlyMeme, relevanceScore = 1.0f, matchType = MatchType.TEXT),
            ),
        )
        val semantic = fakeStrategy(
            name = "semantic",
            priority = 200,
            available = true,
            results = listOf(
                SearchResult(
                    meme = semanticOnlyMeme,
                    relevanceScore = 0.5f,
                    matchType = MatchType.SEMANTIC,
                ),
            ),
        )
        val orchestrator = SearchOrchestrator(strategies = setOf(fts, semantic))

        val results = orchestrator.search("query")

        // Both at rank 1. FTS weight=1.0, Semantic weight=2.0
        // Semantic meme should rank higher due to 2x weight
        assertThat(results).hasSize(2)
        assertThat(results[0].meme.id).isEqualTo(2L)
        assertThat(results[0].relevanceScore).isGreaterThan(results[1].relevanceScore)
    }

    // ==================== FTS-only Fallback ====================

    @Test
    fun `FTS-only fallback when semantic returns empty`() = runTest {
        val meme1 = testMeme(1L)
        val meme2 = testMeme(2L)

        val fts = fakeStrategy(
            name = "fts",
            priority = 100,
            available = true,
            results = listOf(
                SearchResult(meme = meme1, relevanceScore = 0.9f, matchType = MatchType.TEXT),
                SearchResult(meme = meme2, relevanceScore = 0.8f, matchType = MatchType.TEXT),
            ),
        )
        val semantic = fakeStrategy(
            name = "semantic",
            priority = 200,
            available = true,
            results = emptyList(),
        )
        val orchestrator = SearchOrchestrator(strategies = setOf(fts, semantic))

        val results = orchestrator.search("search term")

        assertThat(results).hasSize(2)
        assertThat(results.map { it.meme.id }).containsExactly(1L, 2L).inOrder()
        // No HYBRID since semantic contributed nothing
        results.forEach { assertThat(it.matchType).isNotEqualTo(MatchType.HYBRID) }
    }

    // ==================== Large Result Sets ====================

    @Test
    fun `large result sets from two strategies fused with limit`() = runTest {
        val ftsMemes = (1L..20L).map { id ->
            SearchResult(
                meme = testMeme(id),
                relevanceScore = 1.0f - id * 0.01f,
                matchType = MatchType.TEXT,
            )
        }
        val semanticMemes = (21L..40L).map { id ->
            SearchResult(
                meme = testMeme(id),
                relevanceScore = 1.0f - (id - 20) * 0.01f,
                matchType = MatchType.SEMANTIC,
            )
        }

        val fts = fakeStrategy("fts", 100, true, ftsMemes)
        val semantic = fakeStrategy("semantic", 200, true, semanticMemes)
        val orchestrator = SearchOrchestrator(strategies = setOf(fts, semantic))

        val results = orchestrator.search("big query", limit = 10)

        assertThat(results).hasSize(10)
        // All results should be sorted by descending RRF score
        for (i in 0 until results.size - 1) {
            assertThat(results[i].relevanceScore)
                .isAtLeast(results[i + 1].relevanceScore)
        }
    }

    // ==================== Identical Memes From Both ====================

    @Test
    fun `identical meme from both strategies gets highest combined score`() = runTest {
        val sharedMeme = testMeme(1L)
        val ftsOnly = testMeme(2L)
        val semanticOnly = testMeme(3L)

        val fts = fakeStrategy(
            "fts",
            100,
            true,
            listOf(
                SearchResult(meme = sharedMeme, relevanceScore = 0.9f, matchType = MatchType.TEXT),
                SearchResult(meme = ftsOnly, relevanceScore = 0.85f, matchType = MatchType.TEXT),
            ),
        )
        val semantic = fakeStrategy(
            "semantic",
            200,
            true,
            listOf(
                SearchResult(
                    meme = sharedMeme,
                    relevanceScore = 0.9f,
                    matchType = MatchType.SEMANTIC,
                ),
                SearchResult(
                    meme = semanticOnly,
                    relevanceScore = 0.85f,
                    matchType = MatchType.SEMANTIC,
                ),
            ),
        )
        val orchestrator = SearchOrchestrator(strategies = setOf(fts, semantic))

        val results = orchestrator.search("test")

        // Shared meme should be first with highest score
        assertThat(results[0].meme.id).isEqualTo(1L)
        assertThat(results[0].matchType).isEqualTo(MatchType.HYBRID)
        assertThat(results[0].relevanceScore).isGreaterThan(results[1].relevanceScore)
    }

    // ==================== Strategy Exception ====================

    @Test
    fun `one strategy throws but other still returns results`() = runTest {
        val meme = testMeme(1L)
        val failing = fakeStrategy("semantic", 200, true, throwOnSearch = true)
        val working = fakeStrategy(
            "fts",
            100,
            true,
            listOf(
                SearchResult(meme = meme, relevanceScore = 0.9f, matchType = MatchType.TEXT),
            ),
        )
        val orchestrator = SearchOrchestrator(strategies = setOf(failing, working))

        val results = orchestrator.search("query")

        assertThat(results).hasSize(1)
        assertThat(results[0].meme.id).isEqualTo(1L)
    }

    // ==================== Empty Query ====================

    @Test
    fun `empty query returns empty regardless of strategies`() = runTest {
        val strategy = fakeStrategy(
            "fts",
            100,
            true,
            listOf(
                SearchResult(
                    meme = testMeme(1L),
                    relevanceScore = 0.9f,
                    matchType = MatchType.TEXT,
                ),
            ),
        )
        val orchestrator = SearchOrchestrator(strategies = setOf(strategy))

        assertThat(orchestrator.search("")).isEmpty()
        assertThat(orchestrator.search("   ")).isEmpty()
        assertThat(orchestrator.search("\t\n")).isEmpty()
    }

    // ==================== Single Result Per Strategy ====================

    @Test
    fun `single result per strategy verifies RRF math`() = runTest {
        val meme1 = testMeme(1L)
        val meme2 = testMeme(2L)

        val fts = fakeStrategy(
            "fts",
            100,
            true,
            listOf(
                SearchResult(meme = meme1, relevanceScore = 1.0f, matchType = MatchType.TEXT),
            ),
        )
        val semantic = fakeStrategy(
            "semantic",
            200,
            true,
            listOf(
                SearchResult(
                    meme = meme2,
                    relevanceScore = 1.0f,
                    matchType = MatchType.SEMANTIC,
                ),
            ),
        )
        val orchestrator = SearchOrchestrator(strategies = setOf(fts, semantic))

        val results = orchestrator.search("query")

        assertThat(results).hasSize(2)

        // RRF: weight / (k + rank + 1), k=20
        // FTS meme1: (100/100) / (20 + 0 + 1) = 1.0/21 ≈ 0.04762
        // Semantic meme2: (200/100) / (20 + 0 + 1) = 2.0/21 ≈ 0.09524
        val expectedFts = 1.0f / 21f
        val expectedSemantic = 2.0f / 21f

        assertThat(results[0].meme.id).isEqualTo(2L)
        assertThat(results[0].relevanceScore).isWithin(0.0001f).of(expectedSemantic)
        assertThat(results[1].meme.id).isEqualTo(1L)
        assertThat(results[1].relevanceScore).isWithin(0.0001f).of(expectedFts)
    }

    // ==================== Three Strategies ====================

    @Test
    fun `three strategies fuse with correct priority weighting`() = runTest {
        val meme1 = testMeme(1L)
        val meme2 = testMeme(2L)
        val meme3 = testMeme(3L)

        val fts = fakeStrategy(
            "fts",
            100,
            true,
            listOf(
                SearchResult(meme = meme1, relevanceScore = 0.9f, matchType = MatchType.TEXT),
            ),
        )
        val emoji = fakeStrategy(
            "emoji",
            150,
            true,
            listOf(
                SearchResult(meme = meme2, relevanceScore = 0.8f, matchType = MatchType.EMOJI),
            ),
        )
        val semantic = fakeStrategy(
            "semantic",
            200,
            true,
            listOf(
                SearchResult(
                    meme = meme3,
                    relevanceScore = 0.7f,
                    matchType = MatchType.SEMANTIC,
                ),
            ),
        )
        val orchestrator = SearchOrchestrator(strategies = setOf(fts, emoji, semantic))

        val results = orchestrator.search("funny")

        assertThat(results).hasSize(3)
        // Ordered by RRF weight: semantic(2.0) > emoji(1.5) > fts(1.0)
        assertThat(results[0].meme.id).isEqualTo(3L)
        assertThat(results[1].meme.id).isEqualTo(2L)
        assertThat(results[2].meme.id).isEqualTo(1L)
    }

    // ==================== All Unavailable ====================

    @Test
    fun `all strategies unavailable returns empty`() = runTest {
        val strategies = setOf(
            fakeStrategy("fts", 100, available = false),
            fakeStrategy("semantic", 200, available = false),
            fakeStrategy("emoji", 150, available = false),
        )
        val orchestrator = SearchOrchestrator(strategies = strategies)

        val results = orchestrator.search("query")

        assertThat(results).isEmpty()
    }

    @Test
    fun `mixed available and unavailable strategies only runs available`() = runTest {
        val meme = testMeme(1L)
        val strategies = setOf(
            fakeStrategy("fts", 100, available = true, results = listOf(
                SearchResult(meme = meme, relevanceScore = 0.9f, matchType = MatchType.TEXT),
            )),
            fakeStrategy("semantic", 200, available = false),
        )
        val orchestrator = SearchOrchestrator(strategies = strategies)

        val results = orchestrator.search("query")

        assertThat(results).hasSize(1)
        assertThat(results[0].meme.id).isEqualTo(1L)
    }

    // region Helpers

    private fun testMeme(id: Long): Meme = Meme(
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
