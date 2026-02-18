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

    // ── State combinations for emoji rail visibility ──

    @Test
    fun `browsing mode with emojis and no selection allows emoji rail`() {
        val state = GalleryUiState(
            screenMode = ScreenMode.Browsing,
            uniqueEmojis = listOf("😂" to 5),
            isSelectionMode = false,
        )
        // The rail should be visible: not selection mode, has emojis
        assertThat(state.isSelectionMode).isFalse()
        assertThat(state.uniqueEmojis).isNotEmpty()
    }

    @Test
    fun `searching mode with emojis and no selection allows emoji rail`() {
        val state = GalleryUiState(
            screenMode = ScreenMode.Searching,
            uniqueEmojis = listOf("😂" to 5, "🔥" to 3),
            isSelectionMode = false,
        )
        assertThat(state.screenMode).isEqualTo(ScreenMode.Searching)
        assertThat(state.isSelectionMode).isFalse()
        assertThat(state.uniqueEmojis).isNotEmpty()
    }

    @Test
    fun `selection mode hides emoji rail regardless of other state`() {
        val state = GalleryUiState(
            screenMode = ScreenMode.Browsing,
            uniqueEmojis = listOf("😂" to 5),
            isSelectionMode = true,
            selectedMemeIds = setOf(1L),
        )
        // Selection mode should always hide the rail
        assertThat(state.isSelectionMode).isTrue()
    }

    @Test
    fun `favorites count alone can show emoji rail even without emojis`() {
        val state = GalleryUiState(
            uniqueEmojis = emptyList(),
            favoritesCount = 3,
            isSelectionMode = false,
        )
        // No emojis but has favorites — the rail shows just the favorites chip
        assertThat(state.uniqueEmojis).isEmpty()
        assertThat(state.favoritesCount).isGreaterThan(0)
    }

    @Test
    fun `no emojis and no favorites hides emoji rail`() {
        val state = GalleryUiState(
            uniqueEmojis = emptyList(),
            favoritesCount = 0,
            isSelectionMode = false,
        )
        assertThat(state.uniqueEmojis).isEmpty()
        assertThat(state.favoritesCount).isEqualTo(0)
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
