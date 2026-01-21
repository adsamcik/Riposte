package com.mememymood.feature.gallery.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.Meme
import com.mememymood.core.ui.theme.MemeMoodTheme
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

    private val testMemes = listOf(
        createTestMeme(1L, "meme1.jpg", isFavorite = true),
        createTestMeme(2L, "meme2.jpg", isFavorite = false),
        createTestMeme(3L, "meme3.jpg", isFavorite = false)
    )

    // ============ Empty State Tests ============

    @Test
    fun galleryScreen_showsEmptyState_whenNoMemes() {
        composeTestRule.setContent {
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = emptyList(),
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
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
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = emptyList(),
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Import Memes").assertIsDisplayed()
    }

    // ============ Loading State Tests ============

    @Test
    fun galleryScreen_showsLoadingIndicator_whenLoading() {
        composeTestRule.setContent {
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = emptyList(),
                        isLoading = true
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("LoadingIndicator").assertIsDisplayed()
    }

    // ============ Meme Grid Tests ============

    @Test
    fun galleryScreen_displaysMemeGrid_whenMemesExist() {
        composeTestRule.setContent {
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
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
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
                )
            }
        }

        val memeCards = composeTestRule.onAllNodesWithTag("MemeCard")
            .fetchSemanticsNodes()
        
        assertThat(memeCards.size).isEqualTo(testMemes.size)
    }

    // ============ Filter Tab Tests ============

    @Test
    fun galleryScreen_showsFilterTabs() {
        composeTestRule.setContent {
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("All").assertIsDisplayed()
        composeTestRule.onNodeWithText("Favorites").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_switchesToFavorites_onTabClick() {
        var receivedIntent: GalleryIntent? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false
                    ),
                    onIntent = { receivedIntent = it },
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
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
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false
                    ),
                    onIntent = { receivedIntent = it },
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
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
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false,
                        isSelectionMode = true,
                        selectedMemeIds = setOf(1L)
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close selection").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_showsMultipleSelectedCount() {
        composeTestRule.setContent {
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false,
                        isSelectionMode = true,
                        selectedMemeIds = setOf(1L, 2L, 3L)
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("3 selected").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_showsDeleteButton_inSelectionMode() {
        composeTestRule.setContent {
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false,
                        isSelectionMode = true,
                        selectedMemeIds = setOf(1L)
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Delete selected").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_clearsSelection_onCloseClick() {
        var receivedIntent: GalleryIntent? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false,
                        isSelectionMode = true,
                        selectedMemeIds = setOf(1L)
                    ),
                    onIntent = { receivedIntent = it },
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
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
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
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
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = { navigatedToSearch = true },
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
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
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = { navigatedToImport = true },
                    onNavigateToSettings = {}
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
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = { navigatedToSettings = true }
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
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateToMeme = { navigatedToMemeId = it },
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
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
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
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
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = testMemes,
                        isLoading = false,
                        isSelectionMode = true,
                        selectedMemeIds = setOf(1L, 2L),
                        showDeleteConfirmation = true
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
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
            MemeMoodTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        memes = emptyList(),
                        isLoading = false,
                        error = "Failed to load memes"
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToSearch = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Failed to load memes").assertIsDisplayed()
    }

    // ============ Helper Functions ============

    private fun createTestMeme(
        id: Long,
        fileName: String,
        isFavorite: Boolean = false
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
        isFavorite = isFavorite
    )
}
