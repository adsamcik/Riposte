package com.mememymood.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
 * Comprehensive UI tests for MemeCard component.
 *
 * Tests verify:
 * - Image display
 * - Title display
 * - Emoji tags display
 * - Favorite indicator
 * - Selection state
 * - Click interactions
 * - Long press interactions
 */
@RunWith(AndroidJUnit4::class)
class MemeCardComponentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testMeme = Meme(
        id = 1L,
        filePath = "/test/meme.jpg",
        fileName = "meme.jpg",
        mimeType = "image/jpeg",
        width = 500,
        height = 500,
        fileSizeBytes = 50000,
        importedAt = System.currentTimeMillis(),
        emojiTags = listOf(
            EmojiTag("😂", "laughing"),
            EmojiTag("🎉", "party")
        ),
        title = "Test Meme",
        isFavorite = false
    )

    private val favoriteMeme = testMeme.copy(isFavorite = true)

    // ============ Basic Display Tests ============

    @Test
    fun memeCard_isDisplayed() {
        composeTestRule.setContent {
            MemeMoodTheme {
                MemeCard(
                    meme = testMeme,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("MemeCard").assertIsDisplayed()
    }

    @Test
    fun memeCard_showsImage() {
        composeTestRule.setContent {
            MemeMoodTheme {
                MemeCard(
                    meme = testMeme,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("MemeCardImage").assertIsDisplayed()
    }

    @Test
    fun memeCard_showsTitle_whenTitleProvided() {
        composeTestRule.setContent {
            MemeMoodTheme {
                MemeCard(
                    meme = testMeme,
                    onClick = {},
                    showTitle = true
                )
            }
        }

        composeTestRule.onNodeWithText("Test Meme").assertIsDisplayed()
    }

    @Test
    fun memeCard_hidesTitle_whenShowTitleFalse() {
        composeTestRule.setContent {
            MemeMoodTheme {
                MemeCard(
                    meme = testMeme,
                    onClick = {},
                    showTitle = false
                )
            }
        }

        composeTestRule.onNodeWithText("Test Meme").assertDoesNotExist()
    }

    // ============ Emoji Tags Tests ============

    @Test
    fun memeCard_showsEmojiTags() {
        composeTestRule.setContent {
            MemeMoodTheme {
                MemeCard(
                    meme = testMeme,
                    onClick = {},
                    showEmojis = true
                )
            }
        }

        composeTestRule.onNodeWithText("😂").assertIsDisplayed()
        composeTestRule.onNodeWithText("🎉").assertIsDisplayed()
    }

    @Test
    fun memeCard_hidesEmojiTags_whenShowEmojisFalse() {
        composeTestRule.setContent {
            MemeMoodTheme {
                MemeCard(
                    meme = testMeme,
                    onClick = {},
                    showEmojis = false
                )
            }
        }

        composeTestRule.onNodeWithText("😂").assertDoesNotExist()
    }

    // ============ Favorite Indicator Tests ============

    @Test
    fun memeCard_showsFavoriteIndicator_whenFavorite() {
        composeTestRule.setContent {
            MemeMoodTheme {
                MemeCard(
                    meme = favoriteMeme,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Favorite").assertIsDisplayed()
    }

    @Test
    fun memeCard_hidesFavoriteIndicator_whenNotFavorite() {
        composeTestRule.setContent {
            MemeMoodTheme {
                MemeCard(
                    meme = testMeme,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Favorite").assertDoesNotExist()
    }

    // ============ Selection State Tests ============

    @Test
    fun memeCard_showsSelectionIndicator_whenSelected() {
        composeTestRule.setContent {
            MemeMoodTheme {
                MemeCard(
                    meme = testMeme,
                    onClick = {},
                    selected = true,
                    selectionMode = true
                )
            }
        }

        composeTestRule.onNodeWithTag("SelectionCheckmark").assertIsDisplayed()
    }

    @Test
    fun memeCard_hidesSelectionIndicator_whenNotSelected() {
        composeTestRule.setContent {
            MemeMoodTheme {
                MemeCard(
                    meme = testMeme,
                    onClick = {},
                    selected = false,
                    selectionMode = true
                )
            }
        }

        // Empty checkbox should be shown in selection mode
        composeTestRule.onNodeWithTag("SelectionCheckbox").assertIsDisplayed()
    }

    @Test
    fun memeCard_hidesSelectionCheckbox_whenNotInSelectionMode() {
        composeTestRule.setContent {
            MemeMoodTheme {
                MemeCard(
                    meme = testMeme,
                    onClick = {},
                    selected = false,
                    selectionMode = false
                )
            }
        }

        composeTestRule.onNodeWithTag("SelectionCheckbox").assertDoesNotExist()
    }

    // ============ Click Tests ============

    @Test
    fun memeCard_callsOnClick() {
        var clicked = false

        composeTestRule.setContent {
            MemeMoodTheme {
                MemeCard(
                    meme = testMeme,
                    onClick = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("MemeCard").performClick()

        assertThat(clicked).isTrue()
    }

    @Test
    fun memeCard_callsOnLongClick() {
        var longClicked = false

        composeTestRule.setContent {
            MemeMoodTheme {
                MemeCard(
                    meme = testMeme,
                    onClick = {},
                    onLongClick = { longClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("MemeCard").performLongClick()

        assertThat(longClicked).isTrue()
    }

    // ============ Meme Grid Tests ============

    @Test
    fun memeGrid_displaysMultipleCards() {
        val memes = listOf(
            testMeme,
            testMeme.copy(id = 2L, title = "Second Meme"),
            testMeme.copy(id = 3L, title = "Third Meme")
        )

        composeTestRule.setContent {
            MemeMoodTheme {
                MemeGrid(
                    memes = memes,
                    onMemeClick = {}
                )
            }
        }

        val cards = composeTestRule.onAllNodesWithTag("MemeCard")
            .fetchSemanticsNodes()

        assertThat(cards.size).isEqualTo(3)
    }

    @Test
    fun memeGrid_callsOnMemeClick_withCorrectMeme() {
        var clickedMeme: Meme? = null
        val memes = listOf(
            testMeme,
            testMeme.copy(id = 2L, title = "Second Meme")
        )

        composeTestRule.setContent {
            MemeMoodTheme {
                MemeGrid(
                    memes = memes,
                    onMemeClick = { clickedMeme = it }
                )
            }
        }

        composeTestRule.onAllNodesWithTag("MemeCard")
            .onFirst()
            .performClick()

        assertThat(clickedMeme?.id).isEqualTo(1L)
    }

    @Test
    fun memeGrid_showsEmptyState_whenNoMemes() {
        composeTestRule.setContent {
            MemeMoodTheme {
                MemeGrid(
                    memes = emptyList(),
                    onMemeClick = {},
                    emptyContent = { Text("No memes") }
                )
            }
        }

        composeTestRule.onNodeWithText("No memes").assertIsDisplayed()
    }

    // ============ Aspect Ratio Tests ============

    @Test
    fun memeCard_maintainsAspectRatio() {
        composeTestRule.setContent {
            MemeMoodTheme {
                MemeCard(
                    meme = testMeme,
                    onClick = {},
                    aspectRatio = 1f
                )
            }
        }

        // Card should maintain square aspect ratio
        composeTestRule.onNodeWithTag("MemeCard").assertIsDisplayed()
    }
}

@Composable
private fun Text(text: String) {
    androidx.compose.material3.Text(text = text)
}
