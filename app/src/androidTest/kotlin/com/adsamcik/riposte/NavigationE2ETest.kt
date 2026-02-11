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
 * End-to-end navigation tests for the app.
 * Tests that users can navigate between all major screens.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationE2ETest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun appStarts_showsGalleryScreen() {
        // Gallery screen should be the start destination
        composeTestRule.onNodeWithText("Gallery").assertIsDisplayed()
    }

    @Test
    fun navigateToSearch_fromGallery() {
        // Click on search icon/button
        composeTestRule.onNodeWithContentDescription("Search").performClick()

        // Search screen should be displayed
        composeTestRule.onNodeWithText("Search memes").assertIsDisplayed()
    }

    @Test
    fun navigateToSettings_fromGallery() {
        // Click on settings icon
        composeTestRule.onNodeWithContentDescription("Settings").performClick()

        // Settings screen should be displayed
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun navigateToImport_fromGallery() {
        // Click on import/add button
        composeTestRule.onNodeWithContentDescription("Import").performClick()

        // Import screen should be displayed
        composeTestRule.onNodeWithText("Import Memes").assertIsDisplayed()
    }

    @Test
    fun navigateBack_fromSearch() {
        // Navigate to search
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithText("Search memes").assertIsDisplayed()

        // Navigate back
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Gallery should be displayed
        composeTestRule.onNodeWithText("Gallery").assertIsDisplayed()
    }

    @Test
    fun navigateBack_fromSettings() {
        // Navigate to settings
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()

        // Navigate back
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Gallery should be displayed
        composeTestRule.onNodeWithText("Gallery").assertIsDisplayed()
    }

    @Test
    fun navigateBack_fromImport() {
        // Navigate to import
        composeTestRule.onNodeWithContentDescription("Import").performClick()
        composeTestRule.onNodeWithText("Import Memes").assertIsDisplayed()

        // Navigate back
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Gallery should be displayed
        composeTestRule.onNodeWithText("Gallery").assertIsDisplayed()
    }
}
