package com.adsamcik.riposte.core.search.data.repository

import app.cash.turbine.test
import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.database.dao.MemeSearchDao
import com.adsamcik.riposte.core.database.dao.MemeWithRank
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.adsamcik.riposte.core.database.entity.MemeWithEmbeddingData
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.ml.SemanticSearchEngine
import com.adsamcik.riposte.core.model.AppPreferences
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.search.data.SearchRepositoryImpl
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SearchRepositoryImplTest {
    private lateinit var memeDao: MemeDao
    private lateinit var memeSearchDao: MemeSearchDao
    private lateinit var memeEmbeddingDao: MemeEmbeddingDao
    private lateinit var semanticSearchEngine: SemanticSearchEngine
    private lateinit var emojiTagDao: EmojiTagDao
    private lateinit var preferencesDataStore: PreferencesDataStore

    private lateinit var repository: SearchRepositoryImpl

    private val testMemeEntities =
        listOf(
            createTestMemeEntity(1, "meme1.jpg", title = "Funny cat"),
            createTestMemeEntity(2, "meme2.jpg", description = "Dog meme"),
            createTestMemeEntity(3, "meme3.jpg", emojiTagsJson = "😂,😀"),
        )

    private val testMemeWithRanks =
        listOf(
            createTestMemeWithRank(1, "meme1.jpg", title = "Funny cat", rank = -10.0),
            createTestMemeWithRank(2, "meme2.jpg", description = "Dog meme", rank = -5.0),
            createTestMemeWithRank(3, "meme3.jpg", emojiTagsJson = "😂,😀", rank = -2.0),
        )

    private val recentSearches = listOf("funny", "cat", "dog")
    private val suggestions = listOf("funny meme", "funny cat")

    private val defaultPreferences =
        AppPreferences(
            darkMode = DarkMode.SYSTEM,
            dynamicColors = true,
            gridColumns = 2,
            showEmojiNames = false,
            enableSemanticSearch = true,
            autoExtractText = true,
            saveSearchHistory = true,
        )

    @Before
    fun setup() {
        memeDao = mockk()
        memeSearchDao = mockk()
        memeEmbeddingDao = mockk()
        semanticSearchEngine = mockk()
        emojiTagDao = mockk()
        preferencesDataStore = mockk()

        every { preferencesDataStore.appPreferences } returns flowOf(defaultPreferences)
        every { preferencesDataStore.recentSearches } returns flowOf(recentSearches)
        coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns emptyList()
        every { memeDao.getFavoriteMemes() } returns flowOf(emptyList())
        every { memeDao.getRecentlyViewedMemes(any()) } returns flowOf(emptyList())

        repository =
            SearchRepositoryImpl(
                memeDao = memeDao,
                memeSearchDao = memeSearchDao,
                memeEmbeddingDao = memeEmbeddingDao,
                emojiTagDao = emojiTagDao,
                semanticSearchEngine = semanticSearchEngine,
                preferencesDataStore = preferencesDataStore,
            )
    }

    // region searchMemes Tests

    @Test
    fun `searchMemes returns flow of search results`() =
        runTest {
            coEvery { memeSearchDao.searchMemesRanked(any()) } returns testMemeWithRanks

            repository.searchMemes("funny").test {
                val results = awaitItem()
                assertThat(results).hasSize(3)
                assertThat(results[0].relevanceScore).isGreaterThan(0f)
                awaitComplete()
            }
        }

    @Test
    fun `searchMemes returns empty list for blank query`() =
        runTest {
            repository.searchMemes("").test {
                val results = awaitItem()
                assertThat(results).isEmpty()
                awaitComplete()
            }
        }

    @Test
    fun `searchMemes returns empty list for whitespace query`() =
        runTest {
            repository.searchMemes("   ").test {
                val results = awaitItem()
                assertThat(results).isEmpty()
                awaitComplete()
            }
        }

    @Test
    fun `searchMemes calculates descending relevance scores`() =
        runTest {
            coEvery { memeSearchDao.searchMemesRanked(any()) } returns testMemeWithRanks

            repository.searchMemes("test").test {
                val results = awaitItem()
                assertThat(results[0].relevanceScore).isGreaterThan(results[1].relevanceScore)
                assertThat(results[1].relevanceScore).isGreaterThan(results[2].relevanceScore)
                awaitComplete()
            }
        }

    @Test
    fun `searchMemes determines TEXT match type for title matches`() =
        runTest {
            val rankedWithTitle = listOf(createTestMemeWithRank(1, "test.jpg", title = "funny cat", rank = -10.0))
            coEvery { memeSearchDao.searchMemesRanked(any()) } returns rankedWithTitle

            repository.searchMemes("funny").test {
                val results = awaitItem()
                assertThat(results[0].matchType).isEqualTo(MatchType.TEXT)
                awaitComplete()
            }
        }

    @Test
    fun `searchMemes determines EMOJI match type for emoji matches`() =
        runTest {
            val rankedWithEmoji = listOf(createTestMemeWithRank(1, "test.jpg", emojiTagsJson = "😂", rank = -10.0))
            coEvery { memeSearchDao.searchMemesRanked(any()) } returns rankedWithEmoji

            repository.searchMemes("😂").test {
                val results = awaitItem()
                assertThat(results[0].matchType).isEqualTo(MatchType.EMOJI)
                awaitComplete()
            }
        }

    // endregion

    // region searchByText Tests

    @Test
    fun `searchByText returns same results as searchMemes`() =
        runTest {
            coEvery { memeSearchDao.searchMemesRanked(any()) } returns testMemeWithRanks

            repository.searchByText("test").test {
                val results = awaitItem()
                assertThat(results).hasSize(3)
                awaitComplete()
            }
        }

    // endregion

    // region searchSemantic Tests

    @Test
    fun `searchSemantic returns empty list for blank query`() =
        runTest {
            val results = repository.searchSemantic("")
            assertThat(results).isEmpty()
        }

    @Test
    fun `searchSemantic returns empty list when no memes have embeddings`() =
        runTest {
            val results = repository.searchSemantic("test")
            assertThat(results).isEmpty()
        }

    @Test
    fun `searchSemantic uses multi-vector semantic search engine`() =
        runTest {
            val embedding = createTestEmbedding(128)
            val testEmbeddingData = testMemeEntities.map { createMemeWithEmbeddingData(it, embedding) }
            coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns testEmbeddingData

            val semanticResults =
                testMemeEntities.mapIndexed { index, entity ->
                    SearchResult(
                        meme = entity.toDomainMeme(),
                        relevanceScore = 1.0f - (index * 0.1f),
                        matchType = MatchType.SEMANTIC,
                    )
                }
            coEvery {
                semanticSearchEngine.findSimilarMultiVector(
                    query = "test",
                    candidates = any(),
                    limit = 20,
                )
            } returns semanticResults

            val results = repository.searchSemantic("test", 20)

            assertThat(results).hasSize(3)
            coVerify { semanticSearchEngine.findSimilarMultiVector("test", any(), 20) }
        }

    // endregion

    // region searchHybrid Tests

    @Test
    fun `searchHybrid returns empty list for blank query`() =
        runTest {
            val results = repository.searchHybrid("")
            assertThat(results).isEmpty()
        }

    @Test
    fun `searchHybrid combines FTS and semantic results`() =
        runTest {
            val rankedEntities = testMemeWithRanks.take(2)
            coEvery { memeSearchDao.searchMemesRanked(any()) } returns rankedEntities

            val embedding = createTestEmbedding(128)
            val testEmbeddingData = testMemeEntities.map { createMemeWithEmbeddingData(it, embedding) }
            coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns testEmbeddingData

            val semanticResults =
                listOf(
                    SearchResult(
                        meme = testMemeEntities[2].toDomainMeme(),
                        relevanceScore = 0.9f,
                        matchType = MatchType.SEMANTIC,
                    ),
                )
            coEvery {
                semanticSearchEngine.findSimilarMultiVector(any(), any(), any())
            } returns semanticResults

            val results = repository.searchHybrid("test", 20)

            assertThat(results).hasSize(3)
        }

    @Test
    fun `searchHybrid skips semantic search when disabled in preferences`() =
        runTest {
            val disabledPrefs = defaultPreferences.copy(enableSemanticSearch = false)
            every { preferencesDataStore.appPreferences } returns flowOf(disabledPrefs)

            repository =
                SearchRepositoryImpl(
                    memeDao = memeDao,
                    memeSearchDao = memeSearchDao,
                    memeEmbeddingDao = memeEmbeddingDao,
                    emojiTagDao = emojiTagDao,
                    semanticSearchEngine = semanticSearchEngine,
                    preferencesDataStore = preferencesDataStore,
                )

            coEvery { memeSearchDao.searchMemesRanked(any()) } returns testMemeWithRanks

            val results = repository.searchHybrid("test", 20)

            assertThat(results).hasSize(3)
            coVerify(exactly = 0) { semanticSearchEngine.findSimilarMultiVector(any(), any(), any()) }
        }

    @Test
    fun `searchHybrid respects limit parameter`() =
        runTest {
            val manyRankedEntities =
                (1..30).map { createTestMemeWithRank(it.toLong(), "meme$it.jpg", rank = -30.0 + it) }
            coEvery { memeSearchDao.searchMemesRanked(any()) } returns manyRankedEntities

            val disabledPrefs = defaultPreferences.copy(enableSemanticSearch = false)
            every { preferencesDataStore.appPreferences } returns flowOf(disabledPrefs)

            repository =
                SearchRepositoryImpl(
                    memeDao = memeDao,
                    memeSearchDao = memeSearchDao,
                    memeEmbeddingDao = memeEmbeddingDao,
                    emojiTagDao = emojiTagDao,
                    semanticSearchEngine = semanticSearchEngine,
                    preferencesDataStore = preferencesDataStore,
                )

            val results = repository.searchHybrid("test", 10)

            assertThat(results).hasSize(10)
        }

    @Test
    fun `searchHybrid merges duplicate results with HYBRID match type`() =
        runTest {
            val singleRankedEntity = listOf(createTestMemeWithRank(1, "test.jpg", rank = -10.0))
            coEvery { memeSearchDao.searchMemesRanked(any()) } returns singleRankedEntity

            val embedding = createTestEmbedding(128)
            val singleEntity = listOf(createTestMemeEntity(1, "test.jpg"))
            val testEmbeddingData = singleEntity.map { createMemeWithEmbeddingData(it, embedding) }
            coEvery { memeEmbeddingDao.getMemesWithEmbeddings() } returns testEmbeddingData

            val semanticResult =
                listOf(
                    SearchResult(
                        meme = singleEntity[0].toDomainMeme(),
                        relevanceScore = 0.8f,
                        matchType = MatchType.SEMANTIC,
                    ),
                )
            coEvery { semanticSearchEngine.findSimilarMultiVector(any(), any(), any()) } returns semanticResult

            val results = repository.searchHybrid("test", 20)

            assertThat(results).hasSize(1)
            assertThat(results[0].matchType).isEqualTo(MatchType.HYBRID)
        }

    // endregion

    // region searchByEmoji Tests

    @Test
    fun `searchByEmoji returns flow of emoji search results`() =
        runTest {
            val emojiEntities = listOf(createTestMemeEntity(1, "emoji.jpg", emojiTagsJson = "😂"))
            every { memeSearchDao.searchByEmoji(any()) } returns flowOf(emojiEntities)

            repository.searchByEmoji("😂").test {
                val results = awaitItem()
                assertThat(results).hasSize(1)
                assertThat(results[0].matchType).isEqualTo(MatchType.EMOJI)
                awaitComplete()
            }
        }

    // endregion

    // region getSearchSuggestions Tests

    @Test
    fun `getSearchSuggestions returns empty list for blank prefix`() =
        runTest {
            val result = repository.getSearchSuggestions("")
            assertThat(result).isEmpty()
        }

    @Test
    fun `getSearchSuggestions returns suggestions from dao`() =
        runTest {
            coEvery { memeSearchDao.getSearchSuggestions("fun") } returns suggestions

            val result = repository.getSearchSuggestions("fun")

            assertThat(result).isEqualTo(suggestions)
            coVerify { memeSearchDao.getSearchSuggestions("fun") }
        }

    // endregion

    // region getRecentSearches Tests

    @Test
    fun `getRecentSearches returns flow from preferences datastore`() =
        runTest {
            repository.getRecentSearches().test {
                val searches = awaitItem()
                assertThat(searches).isEqualTo(recentSearches)
                awaitComplete()
            }

            verify { preferencesDataStore.recentSearches }
        }

    // endregion

    // region addRecentSearch Tests

    @Test
    fun `addRecentSearch does nothing for blank query`() =
        runTest {
            repository.addRecentSearch("")

            coVerify(exactly = 0) { preferencesDataStore.addRecentSearch(any()) }
        }

    @Test
    fun `addRecentSearch trims and adds to datastore`() =
        runTest {
            coEvery { preferencesDataStore.addRecentSearch("test") } just Runs

            repository.addRecentSearch("  test  ")

            coVerify { preferencesDataStore.addRecentSearch("test") }
        }

    // endregion

    // region clearRecentSearches Tests

    @Test
    fun `clearRecentSearches clears datastore`() =
        runTest {
            coEvery { preferencesDataStore.clearRecentSearches() } just Runs

            repository.clearRecentSearches()

            coVerify { preferencesDataStore.clearRecentSearches() }
        }

    // endregion

    // region getFavoriteMemes Tests

    @Test
    fun `getFavoriteMemes returns flow of favorite memes as search results`() =
        runTest {
            val favoriteEntities =
                listOf(
                    createTestMemeEntity(1, "fav1.jpg", isFavorite = true),
                    createTestMemeEntity(2, "fav2.jpg", isFavorite = true),
                )
            every { memeDao.getFavoriteMemes() } returns flowOf(favoriteEntities)

            repository.getFavoriteMemes().test {
                val results = awaitItem()
                assertThat(results).hasSize(2)
                assertThat(results[0].relevanceScore).isGreaterThan(0f)
                awaitComplete()
            }

            verify { memeDao.getFavoriteMemes() }
        }

    // endregion

    // region getRecentMemes Tests

    @Test
    fun `getRecentMemes returns flow of recently viewed memes as search results`() =
        runTest {
            val recentEntities =
                listOf(
                    createTestMemeEntity(3, "recent1.jpg"),
                    createTestMemeEntity(4, "recent2.jpg"),
                )
            every { memeDao.getRecentlyViewedMemes(any()) } returns flowOf(recentEntities)

            repository.getRecentMemes().test {
                val results = awaitItem()
                assertThat(results).hasSize(2)
                assertThat(results[0].relevanceScore).isGreaterThan(0f)
                awaitComplete()
            }

            verify { memeDao.getRecentlyViewedMemes(any()) }
        }

    // endregion

    // region Favorite Prioritization Tests

    @Test
    fun `searchMemes prioritizes favorited memes above threshold`() =
        runTest {
            // BM25 ranks: bestRank=-10.0, worstRank=-2.0, range=8.0
            // id=1 (rank -10.0): score = 8/8 = 1.0
            // id=2 (rank -9.0): score = 7/8 = 0.875 (above 0.5 threshold, will be boosted)
            // id=3 (rank -2.0): score = 0/8 → coerced to 0.1
            val rankedEntities =
                listOf(
                    createTestMemeWithRank(1, "meme1.jpg", title = "test normal", rank = -10.0),
                    createTestMemeWithRank(2, "meme2.jpg", title = "test favorite", isFavorite = true, rank = -9.0),
                    createTestMemeWithRank(3, "meme3.jpg", title = "test also normal", rank = -2.0),
                )
            coEvery { memeSearchDao.searchMemesRanked(any()) } returns rankedEntities

            repository.searchMemes("test").test {
                val results = awaitItem()
                assertThat(results).hasSize(3)
                // Favorited meme (id=2) should appear first
                assertThat(results[0].meme.id).isEqualTo(2)
                assertThat(results[0].meme.isFavorite).isTrue()
                awaitComplete()
            }
        }

    @Test
    fun `searchMemes prioritizes all favorites since FTS scores are above threshold`() =
        runTest {
            // With BM25, all equal ranks normalize to 1.0, which meets
            // the FAVORITE_BOOST_THRESHOLD and should be prioritized
            val rankedEntities =
                (1..60).map { i ->
                    createTestMemeWithRank(
                        id = i.toLong(),
                        fileName = "meme$i.jpg",
                        title = "test content",
                        isFavorite = i == 60,
                        rank = -5.0,
                    )
                }
            coEvery { memeSearchDao.searchMemesRanked(any()) } returns rankedEntities

            repository.searchMemes("test").test {
                val results = awaitItem()
                assertThat(results[0].meme.id).isEqualTo(60)
                assertThat(results[0].meme.isFavorite).isTrue()
                awaitComplete()
            }
        }

    @Test
    fun `searchHybrid does not prioritize favorites with low relevance score`() =
        runTest {
            // Give most items a good BM25 rank (-10.0) and the favorite (item 60)
            // a much worse rank (-0.01). With bestRank=-10.0 and worstRank=-0.01,
            // the favorite's normalized score = (-0.01 - (-0.01)) / (-0.01 - (-10.0)) = 0.0
            // → coerced to 0.1. After FTS_WEIGHT: 0.1 * 0.6 = 0.06, below 0.5 threshold.
            val rankedEntities =
                (1..60).map { i ->
                    createTestMemeWithRank(
                        id = i.toLong(),
                        fileName = "meme$i.jpg",
                        title = "test content",
                        isFavorite = i == 60,
                        rank = if (i == 60) -0.01 else -10.0,
                    )
                }
            coEvery { memeSearchDao.searchMemesRanked(any()) } returns rankedEntities

            val disabledPrefs = defaultPreferences.copy(enableSemanticSearch = false)
            every { preferencesDataStore.appPreferences } returns flowOf(disabledPrefs)

            repository =
                SearchRepositoryImpl(
                    memeDao = memeDao,
                    memeSearchDao = memeSearchDao,
                    memeEmbeddingDao = memeEmbeddingDao,
                    emojiTagDao = emojiTagDao,
                    semanticSearchEngine = semanticSearchEngine,
                    preferencesDataStore = preferencesDataStore,
                )

            val results = repository.searchHybrid("test", 100)

            // The favorite at the end has weighted score 0.1 * 0.6 = 0.06
            // which is below FAVORITE_BOOST_THRESHOLD, so it should NOT be boosted
            assertThat(results[0].meme.id).isNotEqualTo(60)
        }

    @Test
    fun `searchByEmoji prioritizes favorited memes above threshold`() =
        runTest {
            val entities =
                listOf(
                    createTestMemeEntity(1, "meme1.jpg", emojiTagsJson = "😂"),
                    createTestMemeEntity(2, "meme2.jpg", emojiTagsJson = "😂", isFavorite = true),
                    createTestMemeEntity(3, "meme3.jpg", emojiTagsJson = "😂"),
                )
            every { memeSearchDao.searchByEmoji(any()) } returns flowOf(entities)

            repository.searchByEmoji("😂").test {
                val results = awaitItem()
                assertThat(results).hasSize(3)
                assertThat(results[0].meme.id).isEqualTo(2)
                assertThat(results[0].meme.isFavorite).isTrue()
                awaitComplete()
            }
        }

    @Test
    fun `searchHybrid prioritizes favorited memes above threshold`() =
        runTest {
            // Equal BM25 ranks → both normalize to 1.0
            // After FTS_WEIGHT: 1.0 * 0.6 = 0.6, above 0.5 threshold
            val rankedEntities =
                listOf(
                    createTestMemeWithRank(1, "normal.jpg", title = "test", rank = -10.0),
                    createTestMemeWithRank(2, "favorite.jpg", title = "test", isFavorite = true, rank = -10.0),
                )
            coEvery { memeSearchDao.searchMemesRanked(any()) } returns rankedEntities

            val disabledPrefs = defaultPreferences.copy(enableSemanticSearch = false)
            every { preferencesDataStore.appPreferences } returns flowOf(disabledPrefs)

            repository =
                SearchRepositoryImpl(
                    memeDao = memeDao,
                    memeSearchDao = memeSearchDao,
                    memeEmbeddingDao = memeEmbeddingDao,
                    emojiTagDao = emojiTagDao,
                    semanticSearchEngine = semanticSearchEngine,
                    preferencesDataStore = preferencesDataStore,
                )

            val results = repository.searchHybrid("test", 20)

            assertThat(results[0].meme.id).isEqualTo(2)
            assertThat(results[0].meme.isFavorite).isTrue()
        }

    @Test
    fun `favorite prioritization preserves relative order within favorites and non-favorites`() =
        runTest {
            // BM25 ranks: bestRank=-10.0, worstRank=-2.0, range=8.0
            // id=1 (rank -10.0): score = 8/8 = 1.0 (non-favorite)
            // id=2 (rank -9.0): score = 7/8 = 0.875 (favorite, above 0.5 threshold)
            // id=3 (rank -4.0): score = 2/8 = 0.25 (non-favorite)
            // id=4 (rank -7.0): score = 5/8 = 0.625 (favorite, above 0.5 threshold)
            val rankedEntities =
                listOf(
                    createTestMemeWithRank(1, "meme1.jpg", title = "test first", rank = -10.0),
                    createTestMemeWithRank(2, "meme2.jpg", title = "test fav2", isFavorite = true, rank = -9.0),
                    createTestMemeWithRank(3, "meme3.jpg", title = "test third", rank = -4.0),
                    createTestMemeWithRank(4, "meme4.jpg", title = "test fav1", isFavorite = true, rank = -7.0),
                )
            coEvery { memeSearchDao.searchMemesRanked(any()) } returns rankedEntities

            repository.searchMemes("test").test {
                val results = awaitItem()
                assertThat(results).hasSize(4)
                // Favorites first (in their original relevance order)
                assertThat(results[0].meme.id).isEqualTo(2)
                assertThat(results[1].meme.id).isEqualTo(4)
                // Then non-favorites
                assertThat(results[2].meme.id).isEqualTo(1)
                assertThat(results[3].meme.id).isEqualTo(3)
                awaitComplete()
            }
        }

    // endregion

    // region Helper Functions

    private fun createTestMemeEntity(
        id: Long,
        fileName: String,
        title: String? = null,
        description: String? = null,
        emojiTagsJson: String = "",
        textContent: String? = null,
        embedding: ByteArray? = null,
        isFavorite: Boolean = false,
    ): MemeEntity {
        return MemeEntity(
            id = id,
            filePath = "/test/path/$fileName",
            fileName = fileName,
            mimeType = "image/jpeg",
            width = 1920,
            height = 1080,
            fileSizeBytes = 1024L,
            importedAt = System.currentTimeMillis(),
            title = title,
            description = description,
            emojiTagsJson = emojiTagsJson,
            textContent = textContent,
            embedding = embedding,
            isFavorite = isFavorite,
        )
    }

    private fun createTestMemeWithRank(
        id: Long,
        fileName: String,
        title: String? = null,
        description: String? = null,
        emojiTagsJson: String = "",
        textContent: String? = null,
        embedding: ByteArray? = null,
        isFavorite: Boolean = false,
        rank: Double = -5.0,
    ): MemeWithRank {
        return MemeWithRank(
            id = id,
            filePath = "/test/path/$fileName",
            fileName = fileName,
            mimeType = "image/jpeg",
            width = 1920,
            height = 1080,
            fileSizeBytes = 1024L,
            importedAt = System.currentTimeMillis(),
            title = title,
            description = description,
            emojiTagsJson = emojiTagsJson,
            textContent = textContent,
            embedding = embedding,
            isFavorite = isFavorite,
            rank = rank,
        )
    }

    private fun MemeEntity.toDomainMeme(): Meme {
        return Meme(
            id = id,
            filePath = filePath,
            fileName = fileName,
            mimeType = mimeType,
            width = width,
            height = height,
            fileSizeBytes = fileSizeBytes,
            importedAt = importedAt,
            title = title,
            description = description,
            emojiTags =
                emojiTagsJson.split(",")
                    .filter { it.isNotEmpty() }
                    .map { com.adsamcik.riposte.core.model.EmojiTag.fromEmoji(it.trim()) },
            textContent = textContent,
            isFavorite = isFavorite,
            createdAt = createdAt,
            useCount = useCount,
        )
    }

    private fun createTestEmbedding(size: Int): ByteArray {
        val floats = FloatArray(size) { it.toFloat() / size }
        val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun createMemeWithEmbeddingData(
        entity: MemeEntity,
        embedding: ByteArray,
        embeddingType: String = "content",
    ): MemeWithEmbeddingData {
        return MemeWithEmbeddingData(
            memeId = entity.id,
            filePath = entity.filePath,
            fileName = entity.fileName,
            title = entity.title,
            description = entity.description,
            textContent = entity.textContent,
            emojiTagsJson = entity.emojiTagsJson,
            embedding = embedding,
            embeddingType = embeddingType,
            dimension = embedding.size / 4,
            modelVersion = "test:1.0.0",
        )
    }

    // endregion
}
