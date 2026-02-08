package com.adsamcik.riposte.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoadingStatesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // LoadingScreen tests

    @Test
    fun loadingScreen_displaysProgressIndicator() {
        composeTestRule.setContent {
            LoadingScreen()
        }

        // CircularProgressIndicator should be present (no text by default)
        composeTestRule.onNode(hasClickAction().not()).assertExists()
    }

    @Test
    fun loadingScreen_displaysMessageWhenProvided() {
        composeTestRule.setContent {
            LoadingScreen(message = "Loading memes...")
        }

        composeTestRule.onNodeWithText("Loading memes...").assertIsDisplayed()
    }

    @Test
    fun loadingScreen_hidesMessageWhenNull() {
        composeTestRule.setContent {
            LoadingScreen(message = null)
        }

        composeTestRule.onNodeWithText("Loading").assertDoesNotExist()
    }

    // EmptyState tests

    @Test
    fun emptyState_displaysEmoji() {
        composeTestRule.setContent {
            EmptyState(
                emoji = "📭",
                title = "No memes",
                message = "Start by importing some memes"
            )
        }

        composeTestRule.onNodeWithText("📭").assertIsDisplayed()
    }

    @Test
    fun emptyState_displaysTitle() {
        composeTestRule.setContent {
            EmptyState(
                emoji = "📭",
                title = "No memes yet",
                message = "Start by importing some memes"
            )
        }

        composeTestRule.onNodeWithText("No memes yet").assertIsDisplayed()
    }

    @Test
    fun emptyState_displaysMessage() {
        composeTestRule.setContent {
            EmptyState(
                emoji = "📭",
                title = "No memes",
                message = "Start by importing some memes"
            )
        }

        composeTestRule.onNodeWithText("Start by importing some memes").assertIsDisplayed()
    }

    @Test
    fun emptyState_displaysActionButton() {
        composeTestRule.setContent {
            EmptyState(
                emoji = "📭",
                title = "No memes",
                message = "Start by importing",
                actionLabel = "Import Memes",
                onAction = {}
            )
        }

        composeTestRule.onNodeWithText("Import Memes").assertIsDisplayed()
    }

    @Test
    fun emptyState_actionButtonClickable() {
        var clicked = false
        
        composeTestRule.setContent {
            EmptyState(
                emoji = "📭",
                title = "No memes",
                message = "Start by importing",
                actionLabel = "Import Memes",
                onAction = { clicked = true }
            )
        }

        composeTestRule.onNodeWithText("Import Memes").performClick()
        
        assertThat(clicked).isTrue()
    }

    @Test
    fun emptyState_hidesActionButtonWhenNull() {
        composeTestRule.setContent {
            EmptyState(
                emoji = "📭",
                title = "No memes",
                message = "Start by importing",
                actionLabel = null,
                onAction = null
            )
        }

        composeTestRule.onNodeWithText("Import").assertDoesNotExist()
    }

    // ErrorState tests

    @Test
    fun errorState_displaysErrorEmoji() {
        composeTestRule.setContent {
            ErrorState(
                title = "Something went wrong",
                message = "Failed to load memes"
            )
        }

        composeTestRule.onNodeWithText("😵").assertIsDisplayed()
    }

    @Test
    fun errorState_displaysTitle() {
        composeTestRule.setContent {
            ErrorState(
                title = "Oops!",
                message = "Something went wrong"
            )
        }

        composeTestRule.onNodeWithText("Oops!").assertIsDisplayed()
    }

    @Test
    fun errorState_displaysMessage() {
        composeTestRule.setContent {
            ErrorState(
                title = "Error",
                message = "Failed to load memes"
            )
        }

        composeTestRule.onNodeWithText("Failed to load memes").assertIsDisplayed()
    }

    @Test
    fun errorState_displaysRetryButton() {
        composeTestRule.setContent {
            ErrorState(
                title = "Error",
                message = "Failed",
                retryLabel = "Try Again",
                onRetry = {}
            )
        }

        composeTestRule.onNodeWithText("Try Again").assertIsDisplayed()
    }

    @Test
    fun errorState_retryButtonClickable() {
        var retried = false
        
        composeTestRule.setContent {
            ErrorState(
                title = "Error",
                message = "Failed",
                retryLabel = "Retry",
                onRetry = { retried = true }
            )
        }

        composeTestRule.onNodeWithText("Retry").performClick()
        
        assertThat(retried).isTrue()
    }
}
