package com.adsamcik.riposte.feature.settings.domain.model

import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class MemeEntityMergerTest {

    private lateinit var merger: MemeEntityMerger

    @Before
    fun setup() {
        merger = MemeEntityMerger()
    }

    @Test
    fun `higher resolution meme wins as keeper`() {
        val highRes = testMeme(id = 1, width = 1920, height = 1080)
        val lowRes = testMeme(id = 2, width = 640, height = 480)

        val result = merger.merge(highRes, lowRes)

        assertThat(result.winnerId).isEqualTo(1)
        assertThat(result.loserId).isEqualTo(2)
    }

    @Test
    fun `larger file size breaks resolution tie`() {
        val largeFile = testMeme(id = 1, width = 100, height = 100, fileSizeBytes = 5000)
        val smallFile = testMeme(id = 2, width = 100, height = 100, fileSizeBytes = 1000)

        val result = merger.merge(largeFile, smallFile)

        assertThat(result.winnerId).isEqualTo(1)
        assertThat(result.loserId).isEqualTo(2)
    }

    @Test
    fun `emoji tags are unioned without duplicates`() {
        val meme1 = testMeme(id = 1, emojiTagsJson = """["😂","🔥"]""")
        val meme2 = testMeme(id = 2, emojiTagsJson = """["🔥","💯"]""")

        val result = merger.merge(meme1, meme2)

        assertThat(result.emojiTagsJson).contains("😂")
        assertThat(result.emojiTagsJson).contains("🔥")
        assertThat(result.emojiTagsJson).contains("💯")
        // Verify no duplicates by counting occurrences of 🔥
        val fireCount = result.emojiTagsJson.split("🔥").size - 1
        assertThat(fireCount).isEqualTo(1)
    }

    @Test
    fun `longer title wins`() {
        val meme1 = testMeme(id = 1, title = "Short")
        val meme2 = testMeme(id = 2, title = "A much longer title here")

        val result = merger.merge(meme1, meme2)

        assertThat(result.title).isEqualTo("A much longer title here")
    }

    @Test
    fun `longer description wins`() {
        val meme1 = testMeme(id = 1, description = "Brief")
        val meme2 = testMeme(id = 2, description = "A longer and more detailed description")

        val result = merger.merge(meme1, meme2)

        assertThat(result.description).isEqualTo("A longer and more detailed description")
    }

    @Test
    fun `use counts are summed`() {
        val meme1 = testMeme(id = 1, useCount = 5)
        val meme2 = testMeme(id = 2, useCount = 3)

        val result = merger.merge(meme1, meme2)

        assertThat(result.useCount).isEqualTo(8)
    }

    @Test
    fun `view counts are summed`() {
        val meme1 = testMeme(id = 1, viewCount = 10)
        val meme2 = testMeme(id = 2, viewCount = 7)

        val result = merger.merge(meme1, meme2)

        assertThat(result.viewCount).isEqualTo(17)
    }

    @Test
    fun `favorite if either is favorited`() {
        val fav = testMeme(id = 1, isFavorite = true)
        val notFav = testMeme(id = 2, isFavorite = false)

        val result = merger.merge(fav, notFav)
        assertThat(result.isFavorite).isTrue()

        val result2 = merger.merge(notFav, fav)
        assertThat(result2.isFavorite).isTrue()
    }

    @Test
    fun `null title uses non-null from other`() {
        val meme1 = testMeme(id = 1, title = null)
        val meme2 = testMeme(id = 2, title = "Has a title")

        val result = merger.merge(meme1, meme2)

        assertThat(result.title).isEqualTo("Has a title")
    }

    @Test
    fun `search phrases are unioned`() {
        val meme1 = testMeme(id = 1, searchPhrasesJson = """["funny cat"]""")
        val meme2 = testMeme(id = 2, searchPhrasesJson = """["funny cat","sad dog"]""")

        val result = merger.merge(meme1, meme2)

        assertThat(result.searchPhrasesJson).isNotNull()
        assertThat(result.searchPhrasesJson!!).contains("funny cat")
        assertThat(result.searchPhrasesJson).contains("sad dog")
        // Verify no duplicates
        val catCount = result.searchPhrasesJson!!.split("funny cat").size - 1
        assertThat(catCount).isEqualTo(1)
    }

    @Test
    fun `empty emoji tags produce valid JSON`() {
        val meme1 = testMeme(id = 1, emojiTagsJson = "[]")
        val meme2 = testMeme(id = 2, emojiTagsJson = "[]")

        val result = merger.merge(meme1, meme2)

        assertThat(result.emojiTagsJson).isEqualTo("[]")
    }

    // region Helpers

    @Suppress("LongParameterList")
    private fun testMeme(
        id: Long = 1,
        width: Int = 100,
        height: Int = 100,
        fileSizeBytes: Long = 1000,
        emojiTagsJson: String = """["😂"]""",
        title: String? = "Title",
        description: String? = null,
        useCount: Int = 0,
        viewCount: Int = 0,
        isFavorite: Boolean = false,
        searchPhrasesJson: String? = null,
    ) = MemeEntity(
        id = id,
        filePath = "/path/$id.jpg",
        fileName = "meme$id.jpg",
        mimeType = "image/jpeg",
        width = width,
        height = height,
        fileSizeBytes = fileSizeBytes,
        importedAt = System.currentTimeMillis(),
        emojiTagsJson = emojiTagsJson,
        title = title,
        description = description,
        useCount = useCount,
        viewCount = viewCount,
        isFavorite = isFavorite,
        searchPhrasesJson = searchPhrasesJson,
    )

    // endregion
}
