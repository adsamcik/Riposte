package com.adsamcik.riposte.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MemeTest {

    private fun createTestMeme(
        id: Long = 1L,
        filePath: String = "/storage/memes/test.jpg",
        fileName: String = "test.jpg",
        mimeType: String = "image/jpeg",
        width: Int = 1920,
        height: Int = 1080,
        fileSizeBytes: Long = 1024L,
        importedAt: Long = 1234567890L,
        emojiTags: List<EmojiTag> = emptyList(),
        title: String? = null,
        description: String? = null,
        textContent: String? = null,
        isFavorite: Boolean = false
    ) = Meme(
        id = id,
        filePath = filePath,
        fileName = fileName,
        mimeType = mimeType,
        width = width,
        height = height,
        fileSizeBytes = fileSizeBytes,
        importedAt = importedAt,
        emojiTags = emojiTags,
        title = title,
        description = description,
        textContent = textContent,
        isFavorite = isFavorite
    )

    @Test
    fun `default id is zero`() {
        val meme = Meme(
            filePath = "/test.jpg",
            fileName = "test.jpg",
            mimeType = "image/jpeg",
            width = 100,
            height = 100,
            fileSizeBytes = 1024L,
            importedAt = 0L,
            emojiTags = emptyList()
        )
        
        assertThat(meme.id).isEqualTo(0L)
    }

    @Test
    fun `default optional fields are null or false`() {
        val meme = createTestMeme()
        
        assertThat(meme.title).isNull()
        assertThat(meme.description).isNull()
        assertThat(meme.textContent).isNull()
        assertThat(meme.isFavorite).isFalse()
    }

    @Test
    fun `meme stores all properties correctly`() {
        val tags = listOf(EmojiTag("😂", "face_with_tears_of_joy"))
        val meme = createTestMeme(
            id = 42L,
            filePath = "/storage/memes/funny.png",
            fileName = "funny.png",
            mimeType = "image/png",
            width = 800,
            height = 600,
            fileSizeBytes = 2048L,
            importedAt = 9876543210L,
            emojiTags = tags,
            title = "Funny Meme",
            description = "A hilarious meme",
            textContent = "LOL",
            isFavorite = true
        )

        assertThat(meme.id).isEqualTo(42L)
        assertThat(meme.filePath).isEqualTo("/storage/memes/funny.png")
        assertThat(meme.fileName).isEqualTo("funny.png")
        assertThat(meme.mimeType).isEqualTo("image/png")
        assertThat(meme.width).isEqualTo(800)
        assertThat(meme.height).isEqualTo(600)
        assertThat(meme.fileSizeBytes).isEqualTo(2048L)
        assertThat(meme.importedAt).isEqualTo(9876543210L)
        assertThat(meme.emojiTags).isEqualTo(tags)
        assertThat(meme.title).isEqualTo("Funny Meme")
        assertThat(meme.description).isEqualTo("A hilarious meme")
        assertThat(meme.textContent).isEqualTo("LOL")
        assertThat(meme.isFavorite).isTrue()
    }

    // Copy tests
    @Test
    fun `copy creates identical meme when no changes`() {
        val original = createTestMeme(
            title = "Original",
            description = "Original description"
        )
        val copied = original.copy()

        assertThat(copied).isEqualTo(original)
    }

    @Test
    fun `copy can change single property`() {
        val original = createTestMeme(isFavorite = false)
        val copied = original.copy(isFavorite = true)

        assertThat(copied.isFavorite).isTrue()
        assertThat(copied.id).isEqualTo(original.id)
        assertThat(copied.filePath).isEqualTo(original.filePath)
    }

    @Test
    fun `copy can change multiple properties`() {
        val original = createTestMeme()
        val copied = original.copy(
            title = "New Title",
            description = "New Description",
            isFavorite = true
        )

        assertThat(copied.title).isEqualTo("New Title")
        assertThat(copied.description).isEqualTo("New Description")
        assertThat(copied.isFavorite).isTrue()
        assertThat(copied.filePath).isEqualTo(original.filePath)
    }

    // Equality tests
    @Test
    fun `memes with same properties are equal`() {
        val meme1 = createTestMeme(id = 1L, title = "Test")
        val meme2 = createTestMeme(id = 1L, title = "Test")

        assertThat(meme1).isEqualTo(meme2)
        assertThat(meme1.hashCode()).isEqualTo(meme2.hashCode())
    }

    @Test
    fun `memes with different ids are not equal`() {
        val meme1 = createTestMeme(id = 1L)
        val meme2 = createTestMeme(id = 2L)

        assertThat(meme1).isNotEqualTo(meme2)
    }

    @Test
    fun `memes with different properties are not equal`() {
        val meme1 = createTestMeme(title = "Title 1")
        val meme2 = createTestMeme(title = "Title 2")

        assertThat(meme1).isNotEqualTo(meme2)
    }

    // emojiDisplay computed property tests
    @Test
    fun `emojiDisplay returns empty string for no tags`() {
        val meme = createTestMeme(emojiTags = emptyList())

        assertThat(meme.emojiDisplay).isEmpty()
    }

    @Test
    fun `emojiDisplay returns single emoji for one tag`() {
        val meme = createTestMeme(
            emojiTags = listOf(EmojiTag("😂", "face_with_tears_of_joy"))
        )

        assertThat(meme.emojiDisplay).isEqualTo("😂")
    }

    @Test
    fun `emojiDisplay returns space-separated emojis for multiple tags`() {
        val meme = createTestMeme(
            emojiTags = listOf(
                EmojiTag("😂", "face_with_tears_of_joy"),
                EmojiTag("🔥", "fire"),
                EmojiTag("💯", "hundred_points")
            )
        )

        assertThat(meme.emojiDisplay).isEqualTo("😂 🔥 💯")
    }

    // hasSearchableContent computed property tests
    @Test
    fun `hasSearchableContent returns false for empty meme`() {
        val meme = createTestMeme(
            title = null,
            description = null,
            textContent = null,
            emojiTags = emptyList()
        )

        assertThat(meme.hasSearchableContent).isFalse()
    }

    @Test
    fun `hasSearchableContent returns false for blank strings`() {
        val meme = createTestMeme(
            title = "   ",
            description = "",
            textContent = "\t\n",
            emojiTags = emptyList()
        )

        assertThat(meme.hasSearchableContent).isFalse()
    }

    @Test
    fun `hasSearchableContent returns true when title is present`() {
        val meme = createTestMeme(title = "Meme Title")

        assertThat(meme.hasSearchableContent).isTrue()
    }

    @Test
    fun `hasSearchableContent returns true when description is present`() {
        val meme = createTestMeme(description = "Meme Description")

        assertThat(meme.hasSearchableContent).isTrue()
    }

    @Test
    fun `hasSearchableContent returns true when textContent is present`() {
        val meme = createTestMeme(textContent = "Text in meme")

        assertThat(meme.hasSearchableContent).isTrue()
    }

    @Test
    fun `hasSearchableContent returns true when emojiTags are present`() {
        val meme = createTestMeme(
            emojiTags = listOf(EmojiTag("😂", "face_with_tears_of_joy"))
        )

        assertThat(meme.hasSearchableContent).isTrue()
    }

    @Test
    fun `hasSearchableContent returns true when multiple fields are present`() {
        val meme = createTestMeme(
            title = "Title",
            description = "Description",
            emojiTags = listOf(EmojiTag("🔥", "fire"))
        )

        assertThat(meme.hasSearchableContent).isTrue()
    }
}
