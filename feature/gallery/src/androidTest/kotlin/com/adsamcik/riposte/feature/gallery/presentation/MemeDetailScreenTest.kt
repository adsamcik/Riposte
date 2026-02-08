package com.adsamcik.riposte.feature.gallery.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [MemeDetailScreen].
 *
 * Tests verify:
 * - Detail view display
 * - Emoji chips display
 * - Edit mode functionality
 * - Share and delete buttons
 * - Dialog interactions
 */
@RunWith(AndroidJUnit4::class)
class MemeDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testMeme = Meme(
        id = 1L,
        filePath = "/test/meme.jpg",
        fileName = "meme.jpg",
        mimeType = "image/jpeg",
        width = 1080,
        height = 1080,
        fileSizeBytes = 102400,
        importedAt = System.currentTimeMillis(),
        emojiTags = listOf(
            EmojiTag("😂", "face_with_tears_of_joy"),
            EmojiTag("🔥", "fire"),
            EmojiTag("💀", "skull")
        ),
        title = "Funny Cat Meme",
        description = "A hilarious cat doing cat things",
        isFavorite = false
    )

    // ============ Loading State Tests ============

    @Test
    fun memeDetailScreen_showsLoadingIndicator_whenLoading() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(isLoading = true),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("LoadingIndicator").assertIsDisplayed()
    }

    // ============ Content Display Tests ============

    @Test
    fun memeDetailScreen_displaysMemeImage() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("MemeImage").assertIsDisplayed()
    }

    @Test
    fun memeDetailScreen_displaysTitle() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Funny Cat Meme").assertIsDisplayed()
    }

    @Test
    fun memeDetailScreen_displaysDescription() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("A hilarious cat doing cat things").assertIsDisplayed()
    }

    @Test
    fun memeDetailScreen_displaysEmojiTags() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("😂").assertIsDisplayed()
        composeTestRule.onNodeWithText("🔥").assertIsDisplayed()
        composeTestRule.onNodeWithText("💀").assertIsDisplayed()
    }

    // ============ Top Bar Tests ============

    @Test
    fun memeDetailScreen_showsBackButton() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
    }

    @Test
    fun memeDetailScreen_navigatesBack_onBackClick() {
        var navigatedBack = false

        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateBack = { navigatedBack = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()

        assertThat(navigatedBack).isTrue()
    }

    @Test
    fun memeDetailScreen_showsEditButton() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Edit").assertIsDisplayed()
    }

    @Test
    fun memeDetailScreen_showsShareButton() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Share").assertIsDisplayed()
    }

    @Test
    fun memeDetailScreen_showsDeleteButton() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Delete").assertIsDisplayed()
    }

    // ============ Favorite Button Tests ============

    @Test
    fun memeDetailScreen_showsFavoriteButton_notFavorited() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme.copy(isFavorite = false),
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Add to favorites").assertIsDisplayed()
    }

    @Test
    fun memeDetailScreen_showsFavoriteButton_favorited() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme.copy(isFavorite = true),
                        isLoading = false
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
    }

    @Test
    fun memeDetailScreen_togglesFavorite_onFavoriteClick() {
        var receivedIntent: MemeDetailIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false
                    ),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Add to favorites").performClick()

        assertThat(receivedIntent).isEqualTo(MemeDetailIntent.ToggleFavorite)
    }

    // ============ Edit Mode Tests ============

    @Test
    fun memeDetailScreen_showsEditFields_inEditMode() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false,
                        isEditMode = true,
                        editedTitle = "Funny Cat Meme",
                        editedDescription = "A hilarious cat doing cat things"
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("TitleTextField").assertIsDisplayed()
        composeTestRule.onNodeWithTag("DescriptionTextField").assertIsDisplayed()
    }

    @Test
    fun memeDetailScreen_showsSaveDiscardButtons_inEditMode() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false,
                        isEditMode = true,
                        editedTitle = "Funny Cat Meme"
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").assertIsDisplayed()
    }

    @Test
    fun memeDetailScreen_togglesEditMode_onEditClick() {
        var receivedIntent: MemeDetailIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false
                    ),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Edit").performClick()

        assertThat(receivedIntent).isEqualTo(MemeDetailIntent.ToggleEditMode)
    }

    @Test
    fun memeDetailScreen_savesChanges_onSaveClick() {
        var receivedIntent: MemeDetailIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false,
                        isEditMode = true,
                        editedTitle = "New Title"
                    ),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Save").performClick()

        assertThat(receivedIntent).isEqualTo(MemeDetailIntent.SaveChanges)
    }

    @Test
    fun memeDetailScreen_discardsChanges_onDiscardClick() {
        var receivedIntent: MemeDetailIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false,
                        isEditMode = true,
                        editedTitle = "New Title"
                    ),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Discard").performClick()

        assertThat(receivedIntent).isEqualTo(MemeDetailIntent.DiscardChanges)
    }

    // ============ Delete Dialog Tests ============

    @Test
    fun memeDetailScreen_showsDeleteDialog_whenShowDeleteDialogTrue() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false,
                        showDeleteDialog = true
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Delete Meme").assertIsDisplayed()
        composeTestRule.onNodeWithText("Are you sure", substring = true).assertIsDisplayed()
    }

    @Test
    fun memeDetailScreen_confirmsDelete_onDeleteConfirmClick() {
        var receivedIntent: MemeDetailIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false,
                        showDeleteDialog = true
                    ),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Delete").performClick()

        assertThat(receivedIntent).isEqualTo(MemeDetailIntent.ConfirmDelete)
    }

    @Test
    fun memeDetailScreen_dismissesDialog_onCancelClick() {
        var receivedIntent: MemeDetailIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false,
                        showDeleteDialog = true
                    ),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertThat(receivedIntent).isEqualTo(MemeDetailIntent.DismissDeleteDialog)
    }

    // ============ Emoji Picker Tests ============

    @Test
    fun memeDetailScreen_showsEmojiPicker_whenShowEmojiPickerTrue() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false,
                        isEditMode = true,
                        showEmojiPicker = true,
                        editedEmojis = listOf("😂", "🔥")
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("EmojiPicker").assertIsDisplayed()
    }

    @Test
    fun memeDetailScreen_showsAddEmojiButton_inEditMode() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false,
                        isEditMode = true,
                        editedEmojis = listOf("😂")
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Add emoji").assertIsDisplayed()
    }

    // ============ Error State Tests ============

    @Test
    fun memeDetailScreen_showsError_whenErrorMessagePresent() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = null,
                        isLoading = false,
                        errorMessage = "Failed to load meme"
                    ),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Failed to load meme").assertIsDisplayed()
    }

    // ============ Share Navigation Test ============

    @Test
    fun memeDetailScreen_navigatesToShare_onShareClick() {
        var receivedIntent: MemeDetailIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                MemeDetailScreen(
                    uiState = MemeDetailUiState(
                        meme = testMeme,
                        isLoading = false
                    ),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Share").performClick()

        assertThat(receivedIntent).isEqualTo(MemeDetailIntent.Share)
    }
}
