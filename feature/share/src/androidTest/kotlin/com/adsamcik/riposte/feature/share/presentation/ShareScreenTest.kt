package com.adsamcik.riposte.feature.share.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.ShareConfig
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [ShareScreen].
 *
 * Tests verify:
 * - Share preview display
 * - Format selection
 * - Quality slider
 * - Size presets
 * - Metadata toggle
 * - Share and save buttons
 * - Processing indicator
 * - Success/error states
 */
@RunWith(AndroidJUnit4::class)
class ShareScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val testMeme =
        Meme(
            id = 1L,
            filePath = "/test/meme.jpg",
            fileName = "meme.jpg",
            mimeType = "image/jpeg",
            width = 1920,
            height = 1080,
            fileSizeBytes = 500000,
            importedAt = System.currentTimeMillis(),
            emojiTags = listOf(EmojiTag("😂", "laughing")),
            title = "Test Meme",
        )

    private val defaultConfig = ShareConfig()

    // ============ Preview Display Tests ============

    @Test
    fun shareScreen_showsPreviewImage() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("SharePreviewImage").assertIsDisplayed()
    }

    @Test
    fun shareScreen_showsOriginalDimensions() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("1920 × 1080", substring = true).assertIsDisplayed()
    }

    @Test
    fun shareScreen_showsOriginalFileSize() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("KB", substring = true).assertIsDisplayed()
    }

    // ============ Format Selection Tests ============

    @Test
    fun shareScreen_showsFormatOptions() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Format").assertIsDisplayed()
        composeTestRule.onNodeWithText("WEBP").assertIsDisplayed()
        composeTestRule.onNodeWithText("PNG").assertIsDisplayed()
        composeTestRule.onNodeWithText("JPEG").assertIsDisplayed()
    }

    @Test
    fun shareScreen_selectsFormat_onFormatClick() {
        var receivedIntent: ShareIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isLoading = false,
                        ),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("PNG").performClick()

        assertThat(receivedIntent).isInstanceOf(ShareIntent.SetFormat::class.java)
        assertThat((receivedIntent as ShareIntent.SetFormat).format).isEqualTo(ImageFormat.PNG)
    }

    // ============ Quality Slider Tests ============

    @Test
    fun shareScreen_showsQualitySlider() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Quality").assertIsDisplayed()
        composeTestRule.onNodeWithTag("QualitySlider").assertIsDisplayed()
    }

    @Test
    fun shareScreen_showsQualityPercentage() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = ShareConfig(quality = 85),
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("85%").assertIsDisplayed()
    }

    // ============ Size Presets Tests ============

    @Test
    fun shareScreen_showsSizePresets() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Size").assertIsDisplayed()
        composeTestRule.onNodeWithText("Original").assertIsDisplayed()
        composeTestRule.onNodeWithText("1080p").assertIsDisplayed()
        composeTestRule.onNodeWithText("720p").assertIsDisplayed()
    }

    @Test
    fun shareScreen_selectsSizePreset_onPresetClick() {
        var receivedIntent: ShareIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isLoading = false,
                        ),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("720p").performClick()

        assertThat(receivedIntent).isInstanceOf(ShareIntent.SetMaxSize::class.java)
    }

    // ============ Toggle Tests ============

    @Test
    fun shareScreen_showsStripMetadataToggle() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Strip Metadata").assertIsDisplayed()
    }

    @Test
    fun shareScreen_togglesStripMetadata_onSwitchClick() {
        var receivedIntent: ShareIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = ShareConfig(stripMetadata = true),
                            isLoading = false,
                        ),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("StripMetadataSwitch").performClick()

        assertThat(receivedIntent).isInstanceOf(ShareIntent.SetStripMetadata::class.java)
    }

    // ============ Action Button Tests ============

    @Test
    fun shareScreen_showsShareButton() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
    }

    @Test
    fun shareScreen_showsSaveButton() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isLoading = false,
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Save to Gallery").assertIsDisplayed()
    }

    @Test
    fun shareScreen_initiatesShare_onShareClick() {
        var receivedIntent: ShareIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isLoading = false,
                        ),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Share").performClick()

        assertThat(receivedIntent).isEqualTo(ShareIntent.Share)
    }

    @Test
    fun shareScreen_initiatesSave_onSaveClick() {
        var receivedIntent: ShareIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isLoading = false,
                        ),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Save to Gallery").performClick()

        assertThat(receivedIntent).isEqualTo(ShareIntent.SaveToGallery)
    }

    @Test
    fun shareScreen_disablesButtons_whenProcessing() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isProcessing = true,
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("ShareButton").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("SaveButton").assertIsNotEnabled()
    }

    // ============ Processing State Tests ============

    @Test
    fun shareScreen_showsProcessingIndicator_whenProcessing() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isProcessing = true,
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("ProcessingIndicator").assertIsDisplayed()
    }

    @Test
    fun shareScreen_showsProcessingMessage_whenProcessing() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            isProcessing = true,
                            processingMessage = "Preparing image...",
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Preparing image...").assertIsDisplayed()
    }

    // ============ Loading State Tests ============

    @Test
    fun shareScreen_showsLoadingIndicator_whenLoading() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState = ShareUiState(isLoading = true),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("LoadingIndicator").assertIsDisplayed()
    }

    // ============ Error State Tests ============

    @Test
    fun shareScreen_showsError_whenErrorPresent() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            error = "Failed to process image",
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Failed to process image").assertIsDisplayed()
    }

    // ============ Navigation Tests ============

    @Test
    fun shareScreen_showsBackButton() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
    }

    @Test
    fun shareScreen_navigatesBack_onBackClick() {
        var navigatedBack = false

        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                        ),
                    onIntent = {},
                    onNavigateBack = { navigatedBack = true },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()

        assertThat(navigatedBack).isTrue()
    }

    // ============ Estimated Size Tests ============

    @Test
    fun shareScreen_showsEstimatedSize() {
        composeTestRule.setContent {
            RiposteTheme {
                ShareScreen(
                    uiState =
                        ShareUiState(
                            meme = testMeme,
                            config = defaultConfig,
                            estimatedSize = "~250 KB",
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("~250 KB").assertIsDisplayed()
    }
}
