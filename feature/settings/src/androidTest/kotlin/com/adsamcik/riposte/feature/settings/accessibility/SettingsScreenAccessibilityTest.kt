package com.adsamcik.riposte.feature.settings.accessibility

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import com.adsamcik.riposte.feature.settings.presentation.SettingsScreen
import com.adsamcik.riposte.feature.settings.presentation.SettingsUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility tests for the Settings screen.
 *
 * Uses the stateless SettingsScreen composable directly (no ViewModel/Hilt needed).
 * Compose 1.8+ Accessibility Testing Framework checks are enabled, which automatically
 * validates touch target sizes, content descriptions, and contrast on every interaction.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenAccessibilityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.enableAccessibilityChecks()
    }

    @Test
    fun settingsScreen_hasAccessibleTitle() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(isLoading = false),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_passesFullAccessibilityCheck() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(isLoading = false),
                    onIntent = {},
                    onNavigateBack = {},
                )
            }
        }

        // ATF checks run automatically on every assertion and interaction,
        // validating touch target sizes (≥48dp), content descriptions, and contrast.
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()
    }
}
