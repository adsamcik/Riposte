package com.mememymood.feature.import_feature.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.ui.theme.MemeMoodTheme
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [ImportScreen].
 *
 * Tests verify:
 * - Empty state and image picker launch
 * - Selected images preview
 * - Import button states
 * - Progress indicator
 * - Success/error messages
 * - Emoji selection
 * - Cancel functionality
 */
@RunWith(AndroidJUnit4::class)
class ImportScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testImages = listOf(
        ImportImage(
            uri = mockk(relaxed = true),
            fileName = "meme1.jpg",
            isProcessing = false,
            suggestedEmojis = listOf("😂", "🔥"),
            emojis = listOf(EmojiTag("😂", "laughing"))
        ),
        ImportImage(
            uri = mockk(relaxed = true),
            fileName = "meme2.jpg",
            isProcessing = false,
            suggestedEmojis = listOf("😍", "❤️"),
            emojis = listOf(EmojiTag("😍", "love"))
        )
    )

    // ============ Empty State Tests ============

    @Test
    fun importScreen_showsEmptyState_whenNoImages() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Select Images").assertIsDisplayed()
        composeTestRule.onNodeWithText("Choose images to import").assertIsDisplayed()
    }

    @Test
    fun importScreen_showsSelectButton_inEmptyState() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("SelectImagesButton").assertIsDisplayed()
    }

    @Test
    fun importScreen_launchesImagePicker_onSelectClick() {
        var pickerLaunched = false

        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = { pickerLaunched = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("SelectImagesButton").performClick()

        assertThat(pickerLaunched).isTrue()
    }

    // ============ Image Preview Tests ============

    @Test
    fun importScreen_showsImagePreviews_whenImagesSelected() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(selectedImages = testImages),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("ImagePreviewGrid").assertIsDisplayed()
        
        val previewCards = composeTestRule.onAllNodesWithTag("ImagePreviewCard")
            .fetchSemanticsNodes()
        
        assertThat(previewCards.size).isEqualTo(2)
    }

    @Test
    fun importScreen_showsFileNames_onPreviews() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(selectedImages = testImages),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithText("meme1.jpg").assertIsDisplayed()
        composeTestRule.onNodeWithText("meme2.jpg").assertIsDisplayed()
    }

    @Test
    fun importScreen_showsRemoveButton_onEachPreview() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(selectedImages = testImages),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        val removeButtons = composeTestRule.onAllNodesWithContentDescription("Remove image")
            .fetchSemanticsNodes()
        
        assertThat(removeButtons.size).isEqualTo(2)
    }

    @Test
    fun importScreen_removesImage_onRemoveClick() {
        var receivedIntent: ImportIntent? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(selectedImages = testImages),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onAllNodesWithContentDescription("Remove image")
            .onFirst()
            .performClick()

        assertThat(receivedIntent).isInstanceOf(ImportIntent.RemoveImage::class.java)
        assertThat((receivedIntent as ImportIntent.RemoveImage).index).isEqualTo(0)
    }

    // ============ Import Button Tests ============

    @Test
    fun importScreen_showsImportButton_whenImagesSelected() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(selectedImages = testImages),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Import").assertIsDisplayed()
    }

    @Test
    fun importScreen_importButtonEnabled_whenAllImagesHaveEmojis() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(selectedImages = testImages),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("ImportButton").assertIsEnabled()
    }

    @Test
    fun importScreen_importButtonDisabled_whenImagesWithoutEmojis() {
        val imagesWithoutEmojis = listOf(
            testImages[0],
            testImages[1].copy(emojis = emptyList())
        )

        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(selectedImages = imagesWithoutEmojis),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("ImportButton").assertIsNotEnabled()
    }

    @Test
    fun importScreen_startsImport_onImportClick() {
        var receivedIntent: ImportIntent? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(selectedImages = testImages),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("ImportButton").performClick()

        assertThat(receivedIntent).isEqualTo(ImportIntent.StartImport)
    }

    // ============ Progress Indicator Tests ============

    @Test
    fun importScreen_showsProgressIndicator_whenImporting() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(
                        selectedImages = testImages,
                        isImporting = true,
                        importProgress = 0.5f
                    ),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("ImportProgress").assertIsDisplayed()
    }

    @Test
    fun importScreen_showsProgressPercentage_whenImporting() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(
                        selectedImages = testImages,
                        isImporting = true,
                        importProgress = 0.5f
                    ),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithText("50%").assertIsDisplayed()
    }

    @Test
    fun importScreen_showsStatusMessage_whenImporting() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(
                        selectedImages = testImages,
                        isImporting = true,
                        statusMessage = "Importing image 1 of 2..."
                    ),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Importing image 1 of 2...").assertIsDisplayed()
    }

    @Test
    fun importScreen_disablesImportButton_whenImporting() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(
                        selectedImages = testImages,
                        isImporting = true
                    ),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("ImportButton").assertIsNotEnabled()
    }

    // ============ Cancel Tests ============

    @Test
    fun importScreen_showsCancelButton_whenImporting() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(
                        selectedImages = testImages,
                        isImporting = true
                    ),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun importScreen_cancelsImport_onCancelClick() {
        var receivedIntent: ImportIntent? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(
                        selectedImages = testImages,
                        isImporting = true
                    ),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertThat(receivedIntent).isEqualTo(ImportIntent.CancelImport)
    }

    // ============ Error State Tests ============

    @Test
    fun importScreen_showsErrorMessage_whenErrorPresent() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(
                        selectedImages = testImages,
                        error = "Failed to import images"
                    ),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Failed to import images").assertIsDisplayed()
    }

    // ============ Processing State Tests ============

    @Test
    fun importScreen_showsProcessingIndicator_onProcessingImage() {
        val processingImages = listOf(
            testImages[0].copy(isProcessing = true),
            testImages[1]
        )

        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(selectedImages = processingImages),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onAllNodesWithTag("ProcessingIndicator")
            .fetchSemanticsNodes()
            .let { nodes ->
                assertThat(nodes.size).isGreaterThan(0)
            }
    }

    // ============ Edit Image Tests ============

    @Test
    fun importScreen_opensEditor_onImageEditClick() {
        var receivedIntent: ImportIntent? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(selectedImages = testImages),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onAllNodesWithTag("EditImageButton")
            .onFirst()
            .performClick()

        assertThat(receivedIntent).isInstanceOf(ImportIntent.EditImage::class.java)
    }

    @Test
    fun importScreen_showsEditorSheet_whenEditingImage() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(
                        selectedImages = testImages,
                        editingImageIndex = 0
                    ),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("EditorBottomSheet").assertIsDisplayed()
    }

    @Test
    fun importScreen_showsTitleField_inEditor() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(
                        selectedImages = testImages,
                        editingImageIndex = 0
                    ),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("TitleTextField").assertIsDisplayed()
    }

    @Test
    fun importScreen_showsEmojiChips_inEditor() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(
                        selectedImages = testImages,
                        editingImageIndex = 0
                    ),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithText("😂").assertIsDisplayed()
    }

    @Test
    fun importScreen_showsSuggestedEmojis_inEditor() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(
                        selectedImages = testImages,
                        editingImageIndex = 0
                    ),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Suggested").assertIsDisplayed()
    }

    // ============ Add More Images Tests ============

    @Test
    fun importScreen_showsAddMoreButton_whenImagesSelected() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(selectedImages = testImages),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Add more images").assertIsDisplayed()
    }

    // ============ Navigation Tests ============

    @Test
    fun importScreen_showsBackButton() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
    }

    @Test
    fun importScreen_navigatesBack_onBackClick() {
        var navigatedBack = false

        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(),
                    onIntent = {},
                    onNavigateBack = { navigatedBack = true },
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()

        assertThat(navigatedBack).isTrue()
    }

    // ============ Clear All Tests ============

    @Test
    fun importScreen_showsClearAllButton_whenImagesSelected() {
        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(selectedImages = testImages),
                    onIntent = {},
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Clear All").assertIsDisplayed()
    }

    @Test
    fun importScreen_clearsAllImages_onClearAllClick() {
        var receivedIntent: ImportIntent? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                ImportScreen(
                    uiState = ImportUiState(selectedImages = testImages),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {},
                    onPickImages = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Clear All").performClick()

        assertThat(receivedIntent).isEqualTo(ImportIntent.ClearAll)
    }
}
