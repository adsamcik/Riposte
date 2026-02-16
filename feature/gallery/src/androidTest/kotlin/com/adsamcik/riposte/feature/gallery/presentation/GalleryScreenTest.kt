package com.adsamcik.riposte.feature.gallery.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performLongClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [GalleryScreen].
 *
 * Tests verify:
 * - Empty state display
 * - Meme grid display
 * - Filter tabs functionality
 * - Selection mode behavior
 * - Navigation interactions
 * - Favorite toggling
 */
@RunWith(AndroidJUnit4::class)
class GalleryScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val testMemes =
        listOf(
            createTestMeme(1L, "meme1.jpg", isFavorite = true),
            createTestMeme(2L, "meme2.jpg", isFavorite = false),
            createTestMeme(3L, "meme3.jpg", isFavorite = false),
        )

    // ============ Empty State Tests ============

    @Test
    fun galleryScreen_showsEmptyState_whenNoMemes() {
        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = emptyList(),
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No memes yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Import your first meme to get started")
            .assertIsDisplayed()
    }

    @Test
    fun galleryScreen_showsImportButton_inEmptyState() {
        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = emptyList(),
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Import Memes").assertIsDisplayed()
    }

    // ============ Loading State Tests ============

    @Test
    fun galleryScreen_showsLoadingIndicator_whenLoading() {
        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = emptyList(),
                            isLoading = true,
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("LoadingIndicator").assertIsDisplayed()
    }

    // ============ Meme Grid Tests ============

    @Test
    fun galleryScreen_displaysMemeGrid_whenMemesExist() {
        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        // Grid should be displayed
        composeTestRule.onNodeWithTag("MemeGrid").assertIsDisplayed()

        // Meme cards should be visible
        composeTestRule.onAllNodesWithTag("MemeCard").fetchSemanticsNodes()
            .let { nodes ->
                assertThat(nodes.size).isGreaterThan(0)
            }
    }

    @Test
    fun galleryScreen_showsCorrectMemeCount() {
        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        val memeCards =
            composeTestRule.onAllNodesWithTag("MemeCard")
                .fetchSemanticsNodes()

        assertThat(memeCards.size).isEqualTo(testMemes.size)
    }

    // ============ Overflow Menu Tests ============

    @Test
    fun galleryScreen_showsOverflowMenuItems() {
        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        // Open overflow menu
        composeTestRule.onNodeWithContentDescription("More options").performClick()

        composeTestRule.onNodeWithText("Select").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_showsFavoritesChipInSearchMode() {
        var receivedIntent: GalleryIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                            screenMode = ScreenMode.Searching,
                            favoritesCount = 3,
                        ),
                    onIntent = { receivedIntent = it },
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Favorites").performClick()

        assertThat(receivedIntent).isInstanceOf(GalleryIntent.SetFilter::class.java)
        assertThat((receivedIntent as GalleryIntent.SetFilter).filter)
            .isInstanceOf(GalleryFilter.Favorites::class.java)
    }

    // ============ Selection Mode Tests ============

    @Test
    fun galleryScreen_entersSelectionMode_onLongPress() {
        var receivedIntent: GalleryIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                        ),
                    onIntent = { receivedIntent = it },
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onAllNodesWithTag("MemeCard")
            .onFirst()
            .performLongClick()

        assertThat(receivedIntent).isInstanceOf(GalleryIntent.StartSelection::class.java)
    }

    @Test
    fun galleryScreen_showsSelectionToolbar_inSelectionMode() {
        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                            isSelectionMode = true,
                            selectedMemeIds = setOf(1L),
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close selection").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_showsMultipleSelectedCount() {
        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                            isSelectionMode = true,
                            selectedMemeIds = setOf(1L, 2L, 3L),
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithText("3 selected").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_showsDeleteButton_inSelectionMode() {
        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                            isSelectionMode = true,
                            selectedMemeIds = setOf(1L),
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Delete selected").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_clearsSelection_onCloseClick() {
        var receivedIntent: GalleryIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                            isSelectionMode = true,
                            selectedMemeIds = setOf(1L),
                        ),
                    onIntent = { receivedIntent = it },
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Close selection").performClick()

        assertThat(receivedIntent).isEqualTo(GalleryIntent.ClearSelection)
    }

    // ============ Navigation Button Tests ============

    @Test
    fun galleryScreen_showsTopBarButtons() {
        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Import").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_navigatesToSearch_onSearchClick() {
        var navigatedToSearch = false

        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Search").performClick()

        assertThat(navigatedToSearch).isTrue()
    }

    @Test
    fun galleryScreen_navigatesToImport_onImportClick() {
        var navigatedToImport = false

        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = { navigatedToImport = true },
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Import").performClick()

        assertThat(navigatedToImport).isTrue()
    }

    @Test
    fun galleryScreen_navigatesToSettings_onSettingsClick() {
        var navigatedToSettings = false

        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = { navigatedToSettings = true },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Settings").performClick()

        assertThat(navigatedToSettings).isTrue()
    }

    // ============ Meme Click Tests ============

    @Test
    fun galleryScreen_navigatesToMeme_onMemeClick() {
        var navigatedToMemeId: Long? = null

        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateToMeme = { navigatedToMemeId = it },
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onAllNodesWithTag("MemeCard")
            .onFirst()
            .performClick()

        assertThat(navigatedToMemeId).isNotNull()
    }

    // ============ Favorite Tests ============

    @Test
    fun galleryScreen_showsFavoriteIcon_forFavoritedMemes() {
        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        // At least one favorite icon should be visible (for meme1 which is favorited)
        composeTestRule.onAllNodesWithContentDescription("Remove from favorites")
            .fetchSemanticsNodes()
            .let { nodes ->
                assertThat(nodes.size).isGreaterThan(0)
            }
    }

    // ============ Delete Confirmation Tests ============

    @Test
    fun galleryScreen_showsDeleteConfirmation_whenConfirmDeleteTrue() {
        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                            isSelectionMode = true,
                            selectedMemeIds = setOf(1L, 2L),
                            showDeleteConfirmation = true,
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Delete Memes").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 memes", substring = true).assertIsDisplayed()
    }

    // ============ Error State Tests ============

    @Test
    fun galleryScreen_showsErrorMessage_whenErrorPresent() {
        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = emptyList(),
                            isLoading = false,
                            error = "Failed to load memes",
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Failed to load memes").assertIsDisplayed()
    }

    // ============ Search Focus → Emoji Rail Tests ============

    @Test
    fun galleryScreen_showsEmojiFilterRail_whenSearchFocused() {
        val emojis = listOf("😂" to 5, "🔥" to 3)

        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                            isSearchFocused = true,
                            uniqueEmojis = emojis,
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("EmojiFilterRail").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_hidesEmojiFilterRail_whenSearchNotFocusedAndBrowsing() {
        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                            isSearchFocused = false,
                            screenMode = ScreenMode.Browsing,
                            uniqueEmojis = listOf("😂" to 5),
                        ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onAllNodesWithTag("EmojiFilterRail")
            .fetchSemanticsNodes().let { nodes ->
                assertThat(nodes).isEmpty()
            }
    }

    @Test
    fun galleryScreen_emitsSearchFieldFocusChanged_whenSearchBarClicked() {
        var receivedIntent: GalleryIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                GalleryScreen(
                    uiState =
                        GalleryUiState(
                            memes = testMemes,
                            isLoading = false,
                        ),
                    onIntent = { receivedIntent = it },
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("SearchBar").performClick()

        assertThat(receivedIntent).isInstanceOf(GalleryIntent.SearchFieldFocusChanged::class.java)
        assertThat((receivedIntent as GalleryIntent.SearchFieldFocusChanged).isFocused).isTrue()
    }

    // ============ Helper Functions ============

    private fun createTestMeme(
        id: Long,
        fileName: String,
        isFavorite: Boolean = false,
    ) = Meme(
        id = id,
        filePath = "/test/$fileName",
        fileName = fileName,
        mimeType = "image/jpeg",
        width = 500,
        height = 500,
        fileSizeBytes = 50000,
        importedAt = System.currentTimeMillis(),
        emojiTags = listOf(EmojiTag("😂", "laughing")),
        title = "Test Meme $id",
        isFavorite = isFavorite,
    )
}
