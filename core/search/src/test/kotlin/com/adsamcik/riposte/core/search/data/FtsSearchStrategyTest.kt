package com.adsamcik.riposte.core.search.data

import com.adsamcik.riposte.core.database.dao.MemeSearchDao
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FtsSearchStrategyTest {

    private lateinit var memeSearchDao: MemeSearchDao
    private lateinit var strategy: FtsSearchStrategy

    @Before
    fun setup() {
        memeSearchDao = mockk()
        strategy = FtsSearchStrategy(memeSearchDao)
    }

    @Test
    fun `isAvailable always returns true`() {
        assertThat(strategy.isAvailable()).isTrue()
    }

    @Test
    fun `search returns empty for blank query`() = runTest {
        val results = strategy.search("   ", limit = 20)
        assertThat(results).isEmpty()
    }

    @Test
    fun `search returns results from FTS DAO`() = runTest {
        val entity = createTestEntity(id = 1, title = "funny cat")
        every { memeSearchDao.searchMemes(any()) } returns flowOf(listOf(entity))

        val results = strategy.search("funny", limit = 20)

        assertThat(results).hasSize(1)
        assertThat(results[0].meme.id).isEqualTo(1L)
    }

    @Test
    fun `search applies field scoring with title bonus`() = runTest {
        val titleMatch = createTestEntity(id = 1, title = "funny cat")
        val descriptionMatch = createTestEntity(id = 2, description = "funny dog")
        every { memeSearchDao.searchMemes(any()) } returns
            flowOf(listOf(titleMatch, descriptionMatch))

        val results = strategy.search("funny", limit = 20)

        assertThat(results).hasSize(2)
        // Title match should score higher than description-only match
        assertThat(results[0].meme.id).isEqualTo(1L)
        assertThat(results[0].relevanceScore).isGreaterThan(results[1].relevanceScore)
    }

    @Test
    fun `search respects limit parameter`() = runTest {
        val entities = (1L..5L).map { createTestEntity(id = it, title = "meme $it") }
        every { memeSearchDao.searchMemes(any()) } returns flowOf(entities)

        val results = strategy.search("meme", limit = 3)

        assertThat(results).hasSize(3)
    }

    @Test
    fun `name is fts`() {
        assertThat(strategy.name).isEqualTo("fts")
    }

    @Test
    fun `priority is 100`() {
        assertThat(strategy.priority).isEqualTo(100)
    }

    private fun createTestEntity(
        id: Long = 1,
        title: String? = null,
        description: String? = null,
        emojiTagsJson: String = "[]",
    ): MemeEntity = MemeEntity(
        id = id,
        filePath = "/test/path/meme$id.jpg",
        fileName = "meme$id.jpg",
        mimeType = "image/jpeg",
        width = 0,
        height = 0,
        fileSizeBytes = 0,
        importedAt = 0,
        emojiTagsJson = emojiTagsJson,
        title = title,
        description = description,
    )
}
