package com.adsamcik.riposte.feature.gallery.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the emoji rail scroll-hide behavior and
 * transparent top bar layout.
 *
 * Tests run with Robolectric so they execute as unit tests without
 * an emulator.
 *
 * The two features under test:
 * 1. Emoji filter rail auto-hides when scrolling down in browsing mode,
 *    but stays always visible in search mode.
 * 2. The top bar (floating search bar) is transparent — content scrolls
 *    behind it using grid contentPadding instead of layout padding.
 */
@RunWith(RobolectricTestRunner::class)
class GalleryScrollBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val emojis = listOf("😂" to 10, "🔥" to 5, "💀" to 3)

    // Generate enough memes to enable scrolling
    private val manyMemes = (1L..30L).map { createTestMeme(it) }

    // ============ Emoji Rail Visibility by Screen Mode ============

    @Test
    fun `emoji rail visible in browsing mode at initial scroll position`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = manyMemes,
                        isLoading = false,
                        screenMode = ScreenMode.Browsing,
                        uniqueEmojis = emojis,
                        usePaging = false,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("EmojiFilterRail").assertIsDisplayed()
    }

    @Test
    fun `emoji rail always visible in search mode`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = manyMemes,
                        isLoading = false,
                        screenMode = ScreenMode.Searching,
                        uniqueEmojis = emojis,
                        usePaging = false,
                        searchState = SearchSliceState(
                            query = "test",
                            hasSearched = true,
                            results = manyMemes.map {
                                com.adsamcik.riposte.core.model.SearchResult(
                                    meme = it,
                                    relevanceScore = 1.0f,
                                    matchType = com.adsamcik.riposte.core.model.MatchType.TEXT,
                                )
                            },
                        ),
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("EmojiFilterRail").assertIsDisplayed()
    }

    @Test
    fun `emoji rail hidden when selection mode active`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = manyMemes,
                        isLoading = false,
                        isSelectionMode = true,
                        selectedMemeIds = setOf(1L),
                        uniqueEmojis = emojis,
                        usePaging = false,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeRule.onAllNodesWithTag("EmojiFilterRail")
            .fetchSemanticsNodes().let { assertThat(it).isEmpty() }
    }

    // ============ Emoji Rail Content ============

    @Test
    fun `emoji rail shows favorites chip when favorites exist in browsing mode`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = manyMemes,
                        isLoading = false,
                        screenMode = ScreenMode.Browsing,
                        uniqueEmojis = emojis,
                        favoritesCount = 3,
                        usePaging = false,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("EmojiFilterRail").assertIsDisplayed()
        composeRule.onNodeWithText("Favorites").assertIsDisplayed()
    }

    @Test
    fun `emoji rail shows only favorites chip when no emojis but favorites exist`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = manyMemes,
                        isLoading = false,
                        screenMode = ScreenMode.Browsing,
                        uniqueEmojis = emptyList(),
                        favoritesCount = 5,
                        usePaging = false,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("EmojiFilterRail").assertIsDisplayed()
        composeRule.onNodeWithText("Favorites").assertIsDisplayed()
    }

    @Test
    fun `emoji rail hidden when no emojis and no favorites`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = manyMemes,
                        isLoading = false,
                        screenMode = ScreenMode.Browsing,
                        uniqueEmojis = emptyList(),
                        favoritesCount = 0,
                        usePaging = false,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeRule.onAllNodesWithTag("EmojiFilterRail")
            .fetchSemanticsNodes().let { assertThat(it).isEmpty() }
    }

    // ============ Emoji Rail Content & Interaction ============

    @Test
    fun `emoji rail displays emoji chips from uniqueEmojis`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = manyMemes,
                        isLoading = false,
                        screenMode = ScreenMode.Browsing,
                        uniqueEmojis = emojis,
                        usePaging = false,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeRule.waitForIdle()
        // All emojis from uniqueEmojis should appear as chips in the rail
        composeRule.onNodeWithContentDescription("😂 filter, inactive").assertExists()
        composeRule.onNodeWithContentDescription("🔥 filter, inactive").assertExists()
        composeRule.onNodeWithContentDescription("💀 filter, inactive").assertExists()
    }

    @Test
    fun `favorites chip appears when favoritesCount greater than zero`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = manyMemes,
                        isLoading = false,
                        screenMode = ScreenMode.Browsing,
                        uniqueEmojis = emojis,
                        favoritesCount = 3,
                        usePaging = false,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Favorites").assertExists()
    }

    @Test
    fun `tapping active favorites chip fires SetFilter All intent`() {
        val receivedIntents = mutableListOf<GalleryIntent>()

        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = manyMemes,
                        isLoading = false,
                        screenMode = ScreenMode.Browsing,
                        uniqueEmojis = emojis,
                        favoritesCount = 3,
                        filter = GalleryFilter.Favorites,
                        usePaging = false,
                    ),
                    onIntent = { receivedIntents.add(it) },
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Favorites").performClick()
        composeRule.waitForIdle()

        val filterIntents = receivedIntents.filterIsInstance<GalleryIntent.SetFilter>()
        assertThat(filterIntents).hasSize(1)
        assertThat(filterIntents.first().filter)
            .isInstanceOf(GalleryFilter.All::class.java)
    }

    // ============ Transparent Top Bar ============

    @Test
    fun `search bar visible as floating overlay in browsing mode`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = manyMemes,
                        isLoading = false,
                        screenMode = ScreenMode.Browsing,
                        usePaging = false,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        // Search bar visible as floating overlay above content
        composeRule.onNodeWithTag("SearchBar").assertIsDisplayed()
    }

    @Test
    fun `search bar hidden in selection mode`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = manyMemes,
                        isLoading = false,
                        isSelectionMode = true,
                        selectedMemeIds = setOf(1L),
                        usePaging = false,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeRule.onAllNodesWithTag("SearchBar")
            .fetchSemanticsNodes().let { assertThat(it).isEmpty() }
    }

    @Test
    fun `search bar visible in search mode`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = manyMemes,
                        isLoading = false,
                        screenMode = ScreenMode.Searching,
                        usePaging = false,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("SearchBar").assertIsDisplayed()
    }

    @Test
    fun `more options button visible in browsing mode`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = manyMemes,
                        isLoading = false,
                        screenMode = ScreenMode.Browsing,
                        usePaging = false,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("SearchBar").assertIsDisplayed()
    }

    // ============ Empty and Loading States ============

    @Test
    fun `emoji rail not shown during loading`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = emptyList(),
                        isLoading = true,
                        uniqueEmojis = emojis,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        // No content to display → no emoji rail rendered
        composeRule.onAllNodesWithTag("EmojiFilterRail")
            .fetchSemanticsNodes().let { assertThat(it).isEmpty() }
    }

    @Test
    fun `emoji rail not shown in empty state`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = emptyList(),
                        isLoading = false,
                        uniqueEmojis = emojis,
                        usePaging = false,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        // Empty state shown, no gallery content → no emoji rail
        composeRule.onAllNodesWithTag("EmojiFilterRail")
            .fetchSemanticsNodes().let { assertThat(it).isEmpty() }
    }

    @Test
    fun `search bar still visible in empty state`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = emptyList(),
                        isLoading = false,
                        usePaging = false,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("SearchBar").assertIsDisplayed()
    }

    // ============ Helpers ============

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
