package com.adsamcik.riposte

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end import flow tests.
 * Tests the complete import user journey.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ImportFlowE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun importFlow_navigateToImport() {
        // Navigate to import
        composeTestRule.onNodeWithContentDescription("Import").performClick()
        composeTestRule.waitForIdle()
        
        // Import screen should be visible
        composeTestRule.onNodeWithText("Import Memes").assertIsDisplayed()
    }

    @Test
    fun importFlow_showsPickImagesButton() {
        // Navigate to import
        composeTestRule.onNodeWithContentDescription("Import").performClick()
        composeTestRule.waitForIdle()
        
        // Pick images button should be visible
        composeTestRule.onNodeWithText("Pick Images").assertIsDisplayed()
    }

    @Test
    fun importFlow_showsEmptyState() {
        // Navigate to import
        composeTestRule.onNodeWithContentDescription("Import").performClick()
        composeTestRule.waitForIdle()
        
        // Empty state message should be visible
        composeTestRule.onNodeWithText("No images selected").assertIsDisplayed()
    }

    @Test
    fun importFlow_backNavigatesToGallery() {
        // Navigate to import
        composeTestRule.onNodeWithContentDescription("Import").performClick()
        composeTestRule.waitForIdle()
        
        // Navigate back
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        
        // Gallery should be displayed
        composeTestRule.onNodeWithText("Gallery").assertIsDisplayed()
    }
}
