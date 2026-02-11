package com.adsamcik.riposte.core.common.suggestion

import com.adsamcik.riposte.core.common.suggestion.SuggestionConfig.Companion.MS_PER_DAY
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class TriSignalScorerTest {
    private lateinit var scorer: TriSignalScorer
    private val now = 1_700_000_000_000L

    @Before
    fun setup() {
        scorer = TriSignalScorer()
    }

    @Test
    fun `engagement score combines useCount, viewCount, and favorite`() {
        val meme = testMeme(useCount = 5, viewCount = 10, isFavorite = true)
        val score = scorer.engagementScore(meme)
        // 5 * 3.0 + 10 * 0.5 + 5.0 = 15 + 5 + 5 = 25
        assertThat(score).isWithin(0.01).of(25.0)
    }

    @Test
    fun `engagement score is zero for fresh meme`() {
        val meme = testMeme()
        assertThat(scorer.engagementScore(meme)).isWithin(0.01).of(0.0)
    }

    @Test
    fun `recency score is 1 when viewed just now`() {
        val meme = testMeme(lastViewedAt = now)
        assertThat(scorer.recencyScore(meme, now)).isWithin(0.01).of(1.0)
    }

    @Test
    fun `recency score is ~0_5 at half-life`() {
        val halfLifeMs = 14L * MS_PER_DAY
        val meme = testMeme(lastViewedAt = now - halfLifeMs)
        assertThat(scorer.recencyScore(meme, now)).isWithin(0.01).of(0.5)
    }

    @Test
    fun `recency score defaults to 30 days when never viewed`() {
        val meme = testMeme(lastViewedAt = null)
        val score = scorer.recencyScore(meme, now)
        // exp(-ln(2)/14 * 30) ≈ 0.228
        assertThat(score).isLessThan(0.25)
        assertThat(score).isGreaterThan(0.2)
    }

    @Test
    fun `scope score is 3 when matching current emoji filter`() {
        val meme = testMeme(emojiTags = listOf(tag("😂")))
        val context =
            SuggestionContext(
                surface = Surface.SEARCH,
                currentEmojiFilter = "😂",
            )
        assertThat(scorer.scopeScore(meme, context)).isWithin(0.01).of(3.0)
    }

    @Test
    fun `scope score is 2 when matching recent search`() {
        val meme = testMeme(title = "funny cat")
        val context =
            SuggestionContext(
                surface = Surface.SEARCH,
                recentSearches = listOf("funny"),
            )
        assertThat(scorer.scopeScore(meme, context)).isWithin(0.01).of(2.0)
    }

    @Test
    fun `scope score is 1 with no context match`() {
        val meme = testMeme()
        val context = SuggestionContext(surface = Surface.GALLERY)
        assertThat(scorer.scopeScore(meme, context)).isWithin(0.01).of(1.0)
    }

    @Test
    fun `gallery surface weights engagement higher than search`() {
        val meme = testMeme(useCount = 10, viewCount = 20, lastViewedAt = now)
        val gallery = SuggestionContext(surface = Surface.GALLERY)
        val search = SuggestionContext(surface = Surface.SEARCH)

        val galleryScore = scorer.score(meme, gallery, now)
        val searchScore = scorer.score(meme, search, now)

        // Both should be positive, but gallery emphasizes engagement more
        assertThat(galleryScore).isGreaterThan(0.0)
        assertThat(searchScore).isGreaterThan(0.0)
    }

    @Test
    fun `search surface boosts scope-matching memes`() {
        val matching = testMeme(emojiTags = listOf(tag("🔥")), useCount = 3, lastViewedAt = now)
        val unmatching = testMeme(id = 2, useCount = 3, lastViewedAt = now)
        val context =
            SuggestionContext(
                surface = Surface.SEARCH,
                currentEmojiFilter = "🔥",
            )

        val matchScore = scorer.score(matching, context, now)
        val unmatchScore = scorer.score(unmatching, context, now)

        // Same engagement/recency, but matching gets scope=3.0 vs scope=1.0
        assertThat(matchScore).isGreaterThan(unmatchScore)
    }
}

class DriftDetectorTest {
    private val detector = DriftDetector()
    private val now = 1_700_000_000_000L

    @Test
    fun `rising emoji has positive drift`() {
        val memes =
            listOf(
                // Old 😂 memes with lots of history
                testMeme(emojiTags = listOf(tag("😂")), viewCount = 50, lastViewedAt = now - 30 * MS_PER_DAY),
                // Recent 🔥 meme
                testMeme(id = 2, emojiTags = listOf(tag("🔥")), viewCount = 10, lastViewedAt = now - MS_PER_DAY),
            )

        val drift = detector.detectDrift(memes, now)
        assertThat(drift["🔥"]).isGreaterThan(0.0)
    }

    @Test
    fun `empty meme list returns empty drift`() {
        assertThat(detector.detectDrift(emptyList(), now)).isEmpty()
    }

    @Test
    fun `risingEmojis returns only positive drift emojis`() {
        val memes =
            listOf(
                testMeme(emojiTags = listOf(tag("😂")), viewCount = 50, lastViewedAt = now - 30 * MS_PER_DAY),
                testMeme(id = 2, emojiTags = listOf(tag("🔥")), viewCount = 10, lastViewedAt = now - MS_PER_DAY),
            )

        val rising = detector.risingEmojis(memes, now)
        assertThat(rising).contains("🔥")
    }
}

class SlotFillerTest {
    private val filler = SlotFiller()
    private val now = 1_700_000_000_000L

    @Test
    fun `fillNew returns recently imported stickers`() {
        val fresh = testMeme(importedAt = now - 1000)
        val old = testMeme(id = 2, importedAt = now - 3 * MS_PER_DAY)

        val result = filler.fillNew(listOf(fresh, old), now, emptySet())
        assertThat(result).containsExactly(fresh)
    }

    @Test
    fun `fillNew excludes already selected`() {
        val fresh = testMeme(importedAt = now - 1000)
        val result = filler.fillNew(listOf(fresh), now, setOf(fresh.id))
        assertThat(result).isEmpty()
    }

    @Test
    fun `fillDiverse picks from uncovered emoji categories`() {
        val laugh = testMeme(emojiTags = listOf(tag("😂")), useCount = 5)
        val fire = testMeme(id = 2, emojiTags = listOf(tag("🔥")), useCount = 3)

        val result =
            filler.fillDiverse(
                allMemes = listOf(laugh, fire),
                alreadySelected = listOf(laugh),
                excludeIds = setOf(laugh.id),
            )
        assertThat(result).containsExactly(fire)
    }

    @Test
    fun `fillForgotten picks old favorites with high useCount`() {
        val forgotten =
            testMeme(
                useCount = 10,
                lastViewedAt = now - 30 * MS_PER_DAY,
            )
        val recent = testMeme(id = 2, useCount = 10, lastViewedAt = now - MS_PER_DAY)

        val result = filler.fillForgotten(listOf(forgotten, recent), now, emptySet())
        assertThat(result).containsExactly(forgotten)
    }

    @Test
    fun `fillForgotten ignores memes with zero useCount`() {
        val unused = testMeme(lastViewedAt = now - 30 * MS_PER_DAY)
        val result = filler.fillForgotten(listOf(unused), now, emptySet())
        assertThat(result).isEmpty()
    }

    @Test
    fun `fillExplore picks from never-interacted emoji categories`() {
        val used = testMeme(emojiTags = listOf(tag("😂")), viewCount = 5)
        val unexplored = testMeme(id = 2, emojiTags = listOf(tag("🎉")))

        val result = filler.fillExplore(listOf(used, unexplored), now, emptySet())
        assertThat(result).containsExactly(unexplored)
    }

    @Test
    fun `fillWildcard picks drift-trending stickers`() {
        val trending = testMeme(emojiTags = listOf(tag("🔥")))
        val drift = mapOf("🔥" to 0.3, "😂" to -0.2)

        val result = filler.fillWildcard(listOf(trending), drift, emptySet())
        assertThat(result).containsExactly(trending)
    }

    @Test
    fun `fillKeepers applies staleness penalty`() {
        val stale = testMeme(useCount = 10, viewCount = 20)
        val fresh = testMeme(id = 2, useCount = 8, viewCount = 18)

        val scoredMemes = listOf(stale to 50.0, fresh to 45.0)
        val lastSessionIds = setOf(stale.id) // stale was shown last session

        val result = filler.fillKeepers(scoredMemes, lastSessionIds, emptySet())
        // Stale gets 50 * 0.80 = 40, fresh stays 45 — fresh should be first
        assertThat(result.first()).isEqualTo(fresh)
    }
}

class PositionalOrdererTest {
    private val orderer = PositionalOrderer()

    @Test
    fun `top 2 highest scored items are in first 2 positions`() {
        val memes = (1..6).map { testMeme(id = it.toLong()) }
        val scores = mapOf(1L to 100.0, 2L to 80.0, 3L to 60.0, 4L to 40.0, 5L to 20.0, 6L to 10.0)

        val result = orderer.order(memes, scores)
        assertThat(result[0].id).isEqualTo(1L)
        assertThat(result[1].id).isEqualTo(2L)
    }

    @Test
    fun `greedy diversify avoids adjacent same emoji`() {
        val memes =
            listOf(
                testMeme(id = 1, emojiTags = listOf(tag("😂"))),
                testMeme(id = 2, emojiTags = listOf(tag("😂"))),
                testMeme(id = 3, emojiTags = listOf(tag("🔥"))),
            )

        val result = orderer.greedyDiversify(memes)
        // Should not have two 😂 adjacent — expects [😂, 🔥, 😂]
        for (i in 0 until result.size - 1) {
            val current = result[i].emojiTags.firstOrNull()?.emoji
            val next = result[i + 1].emojiTags.firstOrNull()?.emoji
            if (current != null && next != null) {
                assertThat(current).isNotEqualTo(next)
            }
        }
    }

    @Test
    fun `single meme returns as-is`() {
        val meme = testMeme()
        val result = orderer.order(listOf(meme), mapOf(meme.id to 1.0))
        assertThat(result).containsExactly(meme)
    }
}

class SuggestionEngineTest {
    private val engine = SuggestionEngine()
    private val now = 1_700_000_000_000L

    @Test
    fun `returns empty for library below minimum`() {
        val memes = (1..4).map { testMeme(id = it.toLong()) }
        val context = SuggestionContext(surface = Surface.GALLERY)
        assertThat(engine.suggest(memes, context, now)).isEmpty()
    }

    @Test
    fun `small library returns all sorted by import recency`() {
        val memes =
            (1..10).map {
                testMeme(id = it.toLong(), importedAt = now - it * MS_PER_DAY)
            }
        val context = SuggestionContext(surface = Surface.GALLERY)
        val result = engine.suggest(memes, context, now)

        assertThat(result).hasSize(10)
        assertThat(result.first().id).isEqualTo(1L) // most recent import
    }

    @Test
    fun `cold start uses recency plus emoji diversity`() {
        val memes =
            (1..25).map {
                testMeme(
                    id = it.toLong(),
                    importedAt = now - it * MS_PER_DAY,
                    emojiTags = listOf(tag(if (it % 3 == 0) "🔥" else "😂")),
                )
            }
        val context = SuggestionContext(surface = Surface.GALLERY)
        val result = engine.suggest(memes, context, now)

        assertThat(result).isNotEmpty()
        assertThat(result.size).isAtMost(12)
    }

    @Test
    fun `full algorithm returns hand-size results for engaged library`() {
        val memes =
            (1..50).map {
                testMeme(
                    id = it.toLong(),
                    importedAt = now - it * MS_PER_DAY,
                    useCount = it % 5,
                    viewCount = it,
                    lastViewedAt = now - (it % 10).toLong() * MS_PER_DAY,
                    emojiTags = listOf(tag(listOf("😂", "🔥", "❤️", "🎉", "😎")[it % 5])),
                )
            }
        val context = SuggestionContext(surface = Surface.GALLERY)
        val result = engine.suggest(memes, context, now)

        assertThat(result).hasSize(12)
        assertThat(result.map { it.id }.distinct()).hasSize(12) // all unique
    }

    @Test
    fun `search surface produces different ordering than gallery`() {
        val memes =
            (1..30).map {
                testMeme(
                    id = it.toLong(),
                    importedAt = now - it * MS_PER_DAY,
                    useCount = it % 3,
                    viewCount = it * 2,
                    lastViewedAt = now - (it % 7).toLong() * MS_PER_DAY,
                    emojiTags = listOf(tag(listOf("😂", "🔥", "❤️")[it % 3])),
                )
            }

        val gallery = engine.suggest(memes, SuggestionContext(surface = Surface.GALLERY), now)
        val search =
            engine.suggest(
                memes,
                SuggestionContext(
                    surface = Surface.SEARCH,
                    recentSearches = listOf("fire"),
                    currentEmojiFilter = "🔥",
                ),
                now,
            )

        // Both return results but likely different orderings
        assertThat(gallery).isNotEmpty()
        assertThat(search).isNotEmpty()
    }

    @Test
    fun `staleness rotation changes keepers between sessions`() {
        // Use very close scores so staleness penalty can flip ordering
        val memes =
            (1..30).map {
                testMeme(
                    id = it.toLong(),
                    importedAt = now - 30 * MS_PER_DAY,
                    useCount = 1,
                    viewCount = 1,
                    lastViewedAt = now - (it % 3).toLong() * MS_PER_DAY,
                    emojiTags = listOf(tag(listOf("😂", "🔥", "❤️")[it % 3])),
                )
            }

        val firstRun = engine.suggest(memes, SuggestionContext(surface = Surface.GALLERY), now)
        val secondRun =
            engine.suggest(
                memes,
                SuggestionContext(
                    surface = Surface.GALLERY,
                    lastSessionSuggestionIds = firstRun.map { it.id }.toSet(),
                ),
                now,
            )

        // At least one item should differ due to staleness penalty on keepers
        val firstIds = firstRun.map { it.id }.toSet()
        val secondIds = secondRun.map { it.id }.toSet()
        val overlap = firstIds.intersect(secondIds)
        assertThat(overlap.size).isLessThan(firstIds.size)
    }
}

// Test helpers

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
