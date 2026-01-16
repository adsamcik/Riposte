package com.mememymood.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.model.EmojiTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmojiChipTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testEmojiTag = EmojiTag(emoji = "😀", name = "happy")

    @Test
    fun emojiChip_displaysEmoji() {
        composeTestRule.setContent {
            EmojiChip(
                emojiTag = testEmojiTag
            )
        }

        composeTestRule.onNodeWithText("😀").assertIsDisplayed()
    }

    @Test
    fun emojiChip_displaysEmojiAndNameWhenShowNameTrue() {
        composeTestRule.setContent {
            EmojiChip(
                emojiTag = testEmojiTag,
                showName = true
            )
        }

        composeTestRule.onNodeWithText("😀 happy").assertIsDisplayed()
    }

    @Test
    fun emojiChip_replacesUnderscoresInName() {
        val underscoreTag = EmojiTag(emoji = "🎉", name = "party_popper")
        
        composeTestRule.setContent {
            EmojiChip(
                emojiTag = underscoreTag,
                showName = true
            )
        }

        composeTestRule.onNodeWithText("🎉 party popper").assertIsDisplayed()
    }

    @Test
    fun emojiChip_isClickableWhenOnClickProvided() {
        var clicked = false
        
        composeTestRule.setContent {
            EmojiChip(
                emojiTag = testEmojiTag,
                onClick = { clicked = true }
            )
        }

        composeTestRule.onNode(hasClickAction()).performClick()
        
        assertThat(clicked).isTrue()
    }

    @Test
    fun emojiChip_variousEmojisDisplay() {
        val emojis = listOf("😂", "🔥", "❤️", "👍", "🎉")
        
        emojis.forEach { emoji ->
            val tag = EmojiTag(emoji = emoji, name = "test")
            composeTestRule.setContent {
                EmojiChip(emojiTag = tag)
            }
            
            composeTestRule.onNodeWithText(emoji).assertIsDisplayed()
        }
    }
}
