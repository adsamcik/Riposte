package com.adsamcik.riposte.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemeCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testMeme = Meme(
        id = 1L,
        filePath = "/test/meme.jpg",
        fileName = "meme.jpg",
        title = "Test Meme",
        emojis = listOf("😀", "😂"),
        emojiTags = listOf(
            EmojiTag("😀", "happy"),
            EmojiTag("😂", "laughing")
        ),
        isFavorite = false
    )

    @Test
    fun memeCard_displaysEmojis() {
        composeTestRule.setContent {
            MemeCard(
                meme = testMeme,
                onClick = {},
                onFavoriteClick = {},
                showEmojis = true
            )
        }

        // EmojiChips should be displayed
        composeTestRule.onNodeWithText("😀").assertIsDisplayed()
        composeTestRule.onNodeWithText("😂").assertIsDisplayed()
    }

    @Test
    fun memeCard_hidesEmojisWhenShowEmojisFalse() {
        composeTestRule.setContent {
            MemeCard(
                meme = testMeme,
                onClick = {},
                onFavoriteClick = {},
                showEmojis = false
            )
        }

        composeTestRule.onNodeWithText("😀").assertDoesNotExist()
        composeTestRule.onNodeWithText("😂").assertDoesNotExist()
    }

    @Test
    fun memeCard_showsFavoriteButton() {
        composeTestRule.setContent {
            MemeCard(
                meme = testMeme,
                onClick = {},
                onFavoriteClick = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Add to favorites").assertIsDisplayed()
    }

    @Test
    fun memeCard_showsFilledFavoriteWhenFavorite() {
        val favoriteMeme = testMeme.copy(isFavorite = true)
        
        composeTestRule.setContent {
            MemeCard(
                meme = favoriteMeme,
                onClick = {},
                onFavoriteClick = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
    }

    @Test
    fun memeCard_clickable() {
        var clicked = false
        
        composeTestRule.setContent {
            MemeCard(
                meme = testMeme,
                onClick = { clicked = true },
                onFavoriteClick = {}
            )
        }

        composeTestRule.onNode(hasClickAction()).performClick()
        
        assertThat(clicked).isTrue()
    }

    @Test
    fun memeCard_favoriteButtonClickable() {
        var favoriteClicked = false
        
        composeTestRule.setContent {
            MemeCard(
                meme = testMeme,
                onClick = {},
                onFavoriteClick = { favoriteClicked = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("Add to favorites").performClick()
        
        assertThat(favoriteClicked).isTrue()
    }
}
