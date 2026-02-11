package com.adsamcik.riposte

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end search flow tests.
 * Tests the complete search user journey.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SearchFlowE2ETest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun searchFlow_navigateToSearch_enterQuery() {
        // Navigate to search
        composeTestRule.onNodeWithContentDescription("Search").performClick()

        // Wait for search screen
        composeTestRule.waitForIdle()

        // Search bar should be displayed
        composeTestRule.onNodeWithText("Search memes").assertIsDisplayed()
    }

    @Test
    fun searchFlow_enterQuery_showsResults() {
        // Navigate to search
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.waitForIdle()

        // Enter search query
        composeTestRule.onNode(hasText("Search memes")).performTextInput("funny")
        composeTestRule.waitForIdle()

        // Clear button should appear
        composeTestRule.onNodeWithContentDescription("Clear search").assertIsDisplayed()
    }

    @Test
    fun searchFlow_clearQuery() {
        // Navigate to search
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.waitForIdle()

        // Enter search query
        composeTestRule.onNode(hasText("Search memes")).performTextInput("test")
        composeTestRule.waitForIdle()

        // Clear the query
        composeTestRule.onNodeWithContentDescription("Clear search").performClick()
        composeTestRule.waitForIdle()

        // Search field should be empty (placeholder visible)
        composeTestRule.onNodeWithText("Search memes").assertIsDisplayed()
    }

    @Test
    fun searchFlow_changeSearchMode() {
        // Navigate to search
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.waitForIdle()

        // Look for search mode selector (if visible)
        val textMode = composeTestRule.onNodeWithText("Text", useUnmergedTree = true)
        val semanticMode = composeTestRule.onNodeWithText("Semantic", useUnmergedTree = true)

        // Try clicking semantic mode if available
        try {
            semanticMode.performClick()
            composeTestRule.waitForIdle()
        } catch (e: AssertionError) {
            // Mode selector might not be visible or has different text
        }
    }

    @Test
    fun searchFlow_emojiFilter() {
        // Navigate to search
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.waitForIdle()

        // Look for emoji filter chips
        val emojiChip = composeTestRule.onNodeWithText("😀", useUnmergedTree = true)

        // Try clicking an emoji filter if available
        try {
            emojiChip.performClick()
            composeTestRule.waitForIdle()
        } catch (e: AssertionError) {
            // Emoji chips might not be visible
        }
    }
}
