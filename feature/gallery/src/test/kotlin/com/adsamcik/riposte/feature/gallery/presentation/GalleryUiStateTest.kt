package com.adsamcik.riposte.feature.gallery.presentation

import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [GalleryUiState] derived properties and state combinations
 * relevant to emoji rail scroll-hide behavior and transparent top bar.
 */
class GalleryUiStateTest {

    private val testMemes = listOf(
        createTestMeme(1L, isFavorite = true),
        createTestMeme(2L),
        createTestMeme(3L),
    )

    // ── isEmpty ──

    @Test
    fun `isEmpty is true when memes empty and not loading and not paging`() {
        val state = GalleryUiState(
            memes = emptyList(),
            isLoading = false,
            usePaging = false,
        )
        assertThat(state.isEmpty).isTrue()
    }

    @Test
    fun `isEmpty is false when memes exist`() {
        val state = GalleryUiState(
            memes = testMemes,
            isLoading = false,
            usePaging = false,
        )
        assertThat(state.isEmpty).isFalse()
    }

    @Test
    fun `isEmpty is false when loading`() {
        val state = GalleryUiState(
            memes = emptyList(),
            isLoading = true,
            usePaging = false,
        )
        assertThat(state.isEmpty).isFalse()
    }

    @Test
    fun `isEmpty is false when using paging even with empty memes`() {
        val state = GalleryUiState(
            memes = emptyList(),
            isLoading = false,
            usePaging = true,
        )
        assertThat(state.isEmpty).isFalse()
    }

    // ── selectionCount / hasSelection ──

    @Test
    fun `selectionCount returns zero when no selection`() {
        val state = GalleryUiState(selectedMemeIds = emptySet())
        assertThat(state.selectionCount).isEqualTo(0)
    }

    @Test
    fun `selectionCount returns correct count`() {
        val state = GalleryUiState(selectedMemeIds = setOf(1L, 2L, 3L))
        assertThat(state.selectionCount).isEqualTo(3)
    }

    @Test
    fun `hasSelection is false when no memes selected`() {
        val state = GalleryUiState(selectedMemeIds = emptySet())
        assertThat(state.hasSelection).isFalse()
    }

    @Test
    fun `hasSelection is true when memes selected`() {
        val state = GalleryUiState(selectedMemeIds = setOf(1L))
        assertThat(state.hasSelection).isTrue()
    }

    // ── ScreenMode defaults ──

    @Test
    fun `default screenMode is Browsing`() {
        val state = GalleryUiState()
        assertThat(state.screenMode).isEqualTo(ScreenMode.Browsing)
    }

    @Test
    fun `screenMode can be set to Searching`() {
        val state = GalleryUiState(screenMode = ScreenMode.Searching)
        assertThat(state.screenMode).isEqualTo(ScreenMode.Searching)
    }

    // ── Emoji rail visibility state combinations ──

    @Test
    fun `uniqueEmojis defaults to empty`() {
        val state = GalleryUiState()
        assertThat(state.uniqueEmojis).isEmpty()
    }

    @Test
    fun `uniqueEmojis preserves order and counts`() {
        val emojis = listOf("😂" to 10, "🔥" to 5, "💀" to 3)
        val state = GalleryUiState(uniqueEmojis = emojis)
        assertThat(state.uniqueEmojis).isEqualTo(emojis)
    }

    @Test
    fun `isSelectionMode defaults to false`() {
        val state = GalleryUiState()
        assertThat(state.isSelectionMode).isFalse()
    }

    @Test
    fun `favoritesCount defaults to zero`() {
        val state = GalleryUiState()
        assertThat(state.favoritesCount).isEqualTo(0)
    }

    @Test
    fun `favoritesCount can be set`() {
        val state = GalleryUiState(favoritesCount = 5)
        assertThat(state.favoritesCount).isEqualTo(5)
    }

    // ── GalleryFilter types ──

    @Test
    fun `default filter is All`() {
        val state = GalleryUiState()
        assertThat(state.filter).isEqualTo(GalleryFilter.All)
    }

    @Test
    fun `filter can be Favorites`() {
        val state = GalleryUiState(filter = GalleryFilter.Favorites)
        assertThat(state.filter).isInstanceOf(GalleryFilter.Favorites::class.java)
    }

    // ── SearchSliceState defaults ──

    @Test
    fun `searchState defaults to empty query`() {
        val state = GalleryUiState()
        assertThat(state.searchState.query).isEmpty()
    }

    @Test
    fun `searchState defaults to not searching`() {
        val state = GalleryUiState()
        assertThat(state.searchState.isSearching).isFalse()
    }

    @Test
    fun `searchState defaults to not hasSearched`() {
        val state = GalleryUiState()
        assertThat(state.searchState.hasSearched).isFalse()
    }

    @Test
    fun `searchState query can be set`() {
        val state = GalleryUiState(
            searchState = SearchSliceState(query = "😂"),
        )
        assertThat(state.searchState.query).isEqualTo("😂")
    }

    // ── Computed property edge cases ──

    @Test
    fun `isEmpty is false when memes exist even if not loading`() {
        val state = GalleryUiState(
            memes = testMemes,
            isLoading = false,
            usePaging = false,
        )
        assertThat(state.isEmpty).isFalse()
        assertThat(state.memes).isNotEmpty()
    }

    @Test
    fun `hasSelection reflects multiple selected memes`() {
        val state = GalleryUiState(
            selectedMemeIds = setOf(1L, 2L, 3L),
        )
        assertThat(state.hasSelection).isTrue()
        assertThat(state.selectionCount).isEqualTo(3)
    }

    @Test
    fun `selectionCount is zero implies hasSelection is false`() {
        val state = GalleryUiState(selectedMemeIds = emptySet())
        assertThat(state.selectionCount).isEqualTo(0)
        assertThat(state.hasSelection).isFalse()
    }

    @Test
    fun `isEmpty is false when usePaging is true regardless of memes`() {
        val state = GalleryUiState(
            memes = emptyList(),
            isLoading = false,
            usePaging = true,
        )
        assertThat(state.isEmpty).isFalse()
    }

    @Test
    fun `isEmpty is false when loading even with empty memes and no paging`() {
        val state = GalleryUiState(
            memes = emptyList(),
            isLoading = true,
            usePaging = false,
        )
        assertThat(state.isEmpty).isFalse()
    }

    // ── ImportWorkStatus ──

    @Test
    fun `importStatus defaults to Idle`() {
        val state = GalleryUiState()
        assertThat(state.importStatus).isEqualTo(ImportWorkStatus.Idle)
    }

    // ── isSearchFocused ──

    @Test
    fun `isSearchFocused defaults to false`() {
        val state = GalleryUiState()
        assertThat(state.isSearchFocused).isFalse()
    }

    @Test
    fun `isSearchFocused can be set`() {
        val state = GalleryUiState(isSearchFocused = true)
        assertThat(state.isSearchFocused).isTrue()
    }

    // ── Helpers ──

    private fun createTestMeme(
        id: Long,
        isFavorite: Boolean = false,
    ) = Meme(
        id = id,
        filePath = "/test/meme$id.jpg",
        fileName = "meme$id.jpg",
        mimeType = "image/jpeg",
        width = 500,
        height = 500,
        fileSizeBytes = 50_000L,
        importedAt = System.currentTimeMillis(),
        emojiTags = listOf(EmojiTag("😂", "laughing")),
        title = "Test Meme $id",
        isFavorite = isFavorite,
    )
}
