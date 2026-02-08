package com.adsamcik.riposte.feature.settings.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.adsamcik.riposte.core.datastore.DarkMode
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [SettingsScreen].
 *
 * Tests verify:
 * - All settings sections displayed
 * - Switch toggle interactions
 * - Dropdown/dialog selections
 * - Slider adjustments
 * - Confirmation dialogs
 * - Navigation
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ============ Section Display Tests ============

    @Test
    fun settingsScreen_showsAllSections() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search").assertIsDisplayed()
        composeTestRule.onNodeWithText("Storage").assertIsDisplayed()
        composeTestRule.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsTitle() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    // ============ Appearance Section Tests ============

    @Test
    fun settingsScreen_showsThemeSetting() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(darkMode = DarkMode.SYSTEM),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Theme").assertIsDisplayed()
        composeTestRule.onNodeWithText("System").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_opensThemeDialog_onThemeClick() {
        var receivedIntent: SettingsIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Theme").performClick()

        assertThat(receivedIntent).isEqualTo(SettingsIntent.ShowThemeDialog)
    }

    @Test
    fun settingsScreen_showsThemeDialog_whenShowThemeDialogTrue() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(showThemeDialog = true),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Select Theme").assertIsDisplayed()
        composeTestRule.onNodeWithText("Light").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
        composeTestRule.onNodeWithText("System").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_selectsTheme_inThemeDialog() {
        var receivedIntent: SettingsIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(showThemeDialog = true),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Dark").performClick()

        assertThat(receivedIntent).isInstanceOf(SettingsIntent.SetDarkMode::class.java)
        assertThat((receivedIntent as SettingsIntent.SetDarkMode).mode).isEqualTo(DarkMode.DARK)
    }

    @Test
    fun settingsScreen_showsDynamicColorsSwitch() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(dynamicColors = true),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Dynamic Colors").assertIsDisplayed()
        composeTestRule.onNodeWithTag("DynamicColorsSwitch").assertIsOn()
    }

    @Test
    fun settingsScreen_togglesDynamicColors_onSwitchClick() {
        var receivedIntent: SettingsIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(dynamicColors = true),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("DynamicColorsSwitch").performClick()

        assertThat(receivedIntent).isInstanceOf(SettingsIntent.SetDynamicColors::class.java)
        assertThat((receivedIntent as SettingsIntent.SetDynamicColors).enabled).isFalse()
    }

    @Test
    fun settingsScreen_showsGridColumnsSlider() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(gridColumns = 3),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Grid Columns").assertIsDisplayed()
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
    }

    // ============ Search Section Tests ============

    @Test
    fun settingsScreen_showsSemanticSearchSwitch() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(enableSemanticSearch = true),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Semantic Search").assertIsDisplayed()
        composeTestRule.onNodeWithTag("SemanticSearchSwitch").assertIsOn()
    }

    @Test
    fun settingsScreen_togglesSemanticSearch_onSwitchClick() {
        var receivedIntent: SettingsIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(enableSemanticSearch = true),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("SemanticSearchSwitch").performClick()

        assertThat(receivedIntent).isInstanceOf(SettingsIntent.SetSemanticSearch::class.java)
    }

    @Test
    fun settingsScreen_showsAutoExtractTextSwitch() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(autoExtractText = true),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Auto-Extract Text").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsSaveSearchHistorySwitch() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(saveSearchHistory = true),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Save Search History").assertIsDisplayed()
    }

    // ============ Storage Section Tests ============

    @Test
    fun settingsScreen_showsClearCacheButton() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Clear Cache").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsCacheSize() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(cacheSize = "25.5 MB"),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("25.5 MB").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsClearCacheConfirmation_onClearClick() {
        var receivedIntent: SettingsIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Clear Cache").performClick()

        assertThat(receivedIntent).isEqualTo(SettingsIntent.ShowClearCacheDialog)
    }

    @Test
    fun settingsScreen_showsClearCacheDialog_whenShowClearCacheDialogTrue() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(showClearCacheDialog = true),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Clear Cache").assertIsDisplayed()
        composeTestRule.onNodeWithText("Are you sure", substring = true).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_confirmsClearCache_onConfirmClick() {
        var receivedIntent: SettingsIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(showClearCacheDialog = true),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Confirm").performClick()

        assertThat(receivedIntent).isEqualTo(SettingsIntent.ConfirmClearCache)
    }

    @Test
    fun settingsScreen_showsClearSearchHistoryButton() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Clear Search History").assertIsDisplayed()
    }

    // ============ About Section Tests ============

    @Test
    fun settingsScreen_showsVersionInfo() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(appVersion = "1.0.0"),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Version").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.0.0").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsPrivacyPolicyLink() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Privacy Policy").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsLicensesLink() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Open Source Licenses").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_opensPrivacyPolicy_onPrivacyClick() {
        var receivedIntent: SettingsIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Privacy Policy").performClick()

        assertThat(receivedIntent).isEqualTo(SettingsIntent.OpenPrivacyPolicy)
    }

    @Test
    fun settingsScreen_opensLicenses_onLicensesClick() {
        var receivedIntent: SettingsIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Open Source Licenses").performClick()

        assertThat(receivedIntent).isEqualTo(SettingsIntent.OpenLicenses)
    }

    // ============ Navigation Tests ============

    @Test
    fun settingsScreen_showsBackButton() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_navigatesBack_onBackClick() {
        var navigatedBack = false

        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onIntent = {},
                    onNavigateBack = { navigatedBack = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()

        assertThat(navigatedBack).isTrue()
    }

    // ============ Show Emoji Names Switch Tests ============

    @Test
    fun settingsScreen_showsEmojiNamesSwitch() {
        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(showEmojiNames = false),
                    onIntent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Show Emoji Names").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_togglesEmojiNames_onSwitchClick() {
        var receivedIntent: SettingsIntent? = null

        composeTestRule.setContent {
            RiposteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(showEmojiNames = false),
                    onIntent = { receivedIntent = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("ShowEmojiNamesSwitch").performClick()

        assertThat(receivedIntent).isInstanceOf(SettingsIntent.SetShowEmojiNames::class.java)
    }
}
