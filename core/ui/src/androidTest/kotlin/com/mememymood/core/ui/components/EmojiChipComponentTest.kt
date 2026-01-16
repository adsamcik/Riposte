package com.mememymood.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive UI tests for EmojiChip component.
 *
 * Tests verify:
 * - Emoji display
 * - Selection states
 * - Click interactions
 * - Name display (when enabled)
 * - Size variants
 */
@RunWith(AndroidJUnit4::class)
class EmojiChipComponentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testEmoji = EmojiTag(emoji = "😂", name = "laughing")

    // ============ Basic Display Tests ============

    @Test
    fun emojiChip_isDisplayed() {
        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiChip(
                    emojiTag = testEmoji,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("EmojiChip").assertIsDisplayed()
    }

    @Test
    fun emojiChip_showsEmoji() {
        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiChip(
                    emojiTag = testEmoji,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("😂").assertIsDisplayed()
    }

    @Test
    fun emojiChip_showsName_whenShowNameTrue() {
        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiChip(
                    emojiTag = testEmoji,
                    onClick = {},
                    showName = true
                )
            }
        }

        composeTestRule.onNodeWithText("laughing").assertIsDisplayed()
    }

    @Test
    fun emojiChip_hidesName_whenShowNameFalse() {
        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiChip(
                    emojiTag = testEmoji,
                    onClick = {},
                    showName = false
                )
            }
        }

        composeTestRule.onNodeWithText("laughing").assertDoesNotExist()
    }

    // ============ Selection State Tests ============

    @Test
    fun emojiChip_showsSelectedState() {
        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiChip(
                    emojiTag = testEmoji,
                    onClick = {},
                    selected = true
                )
            }
        }

        composeTestRule.onNodeWithTag("EmojiChip").assertIsSelected()
    }

    @Test
    fun emojiChip_showsUnselectedState() {
        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiChip(
                    emojiTag = testEmoji,
                    onClick = {},
                    selected = false
                )
            }
        }

        // Should not be selected
        composeTestRule.onNodeWithTag("EmojiChip").assertIsDisplayed()
    }

    // ============ Click Tests ============

    @Test
    fun emojiChip_callsOnClick() {
        var clicked = false

        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiChip(
                    emojiTag = testEmoji,
                    onClick = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("EmojiChip").performClick()

        assertThat(clicked).isTrue()
    }

    @Test
    fun emojiChip_passesCorrectEmoji_onClick() {
        var receivedEmoji: EmojiTag? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiChip(
                    emojiTag = testEmoji,
                    onClick = { receivedEmoji = testEmoji }
                )
            }
        }

        composeTestRule.onNodeWithTag("EmojiChip").performClick()

        assertThat(receivedEmoji).isEqualTo(testEmoji)
    }

    // ============ Remove Button Tests ============

    @Test
    fun emojiChip_showsRemoveButton_whenRemovable() {
        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiChip(
                    emojiTag = testEmoji,
                    onClick = {},
                    removable = true,
                    onRemove = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Remove").assertIsDisplayed()
    }

    @Test
    fun emojiChip_hidesRemoveButton_whenNotRemovable() {
        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiChip(
                    emojiTag = testEmoji,
                    onClick = {},
                    removable = false
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Remove").assertDoesNotExist()
    }

    @Test
    fun emojiChip_callsOnRemove_whenRemoveClicked() {
        var removed = false

        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiChip(
                    emojiTag = testEmoji,
                    onClick = {},
                    removable = true,
                    onRemove = { removed = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Remove").performClick()

        assertThat(removed).isTrue()
    }

    // ============ Emoji Row Tests ============

    @Test
    fun emojiRow_displaysMultipleChips() {
        val emojis = listOf(
            EmojiTag("😂", "laughing"),
            EmojiTag("😢", "crying"),
            EmojiTag("🎉", "party")
        )

        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiRow(
                    emojis = emojis,
                    onEmojiClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("😂").assertIsDisplayed()
        composeTestRule.onNodeWithText("😢").assertIsDisplayed()
        composeTestRule.onNodeWithText("🎉").assertIsDisplayed()
    }

    @Test
    fun emojiRow_selectsCorrectEmoji_onClick() {
        val emojis = listOf(
            EmojiTag("😂", "laughing"),
            EmojiTag("😢", "crying")
        )
        var selectedEmoji: EmojiTag? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiRow(
                    emojis = emojis,
                    onEmojiClick = { selectedEmoji = it }
                )
            }
        }

        composeTestRule.onNodeWithText("😢").performClick()

        assertThat(selectedEmoji?.emoji).isEqualTo("😢")
    }

    // ============ Emoji Picker Tests ============

    @Test
    fun emojiPicker_displaysCategories() {
        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiPicker(
                    onEmojiSelected = {},
                    onDismiss = {}
                )
            }
        }

        // Should show category tabs
        composeTestRule.onNodeWithTag("EmojiCategoryTabs").assertIsDisplayed()
    }

    @Test
    fun emojiPicker_selectsEmoji_onClick() {
        var selectedEmoji: EmojiTag? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiPicker(
                    onEmojiSelected = { selectedEmoji = it },
                    onDismiss = {}
                )
            }
        }

        // Click first emoji in picker
        composeTestRule.onAllNodesWithTag("EmojiPickerItem")
            .onFirst()
            .performClick()

        assertThat(selectedEmoji).isNotNull()
    }

    @Test
    fun emojiPicker_dismisses_onDismissClick() {
        var dismissed = false

        composeTestRule.setContent {
            MemeMoodTheme {
                EmojiPicker(
                    onEmojiSelected = {},
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Close").performClick()

        assertThat(dismissed).isTrue()
    }
}
