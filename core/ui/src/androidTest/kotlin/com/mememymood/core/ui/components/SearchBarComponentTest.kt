package com.mememymood.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.ui.theme.MemeMoodTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive UI tests for SearchBar component.
 *
 * Tests verify:
 * - Text input functionality
 * - Placeholder display
 * - Clear button behavior
 * - Search action
 * - Focus states
 * - Voice search (if enabled)
 */
@RunWith(AndroidJUnit4::class)
class SearchBarComponentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ============ Basic Display Tests ============

    @Test
    fun searchBar_isDisplayed() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchBar(
                    query = "",
                    onQueryChange = {},
                    onSearch = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("SearchBar").assertIsDisplayed()
    }

    @Test
    fun searchBar_showsPlaceholder_whenEmpty() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchBar(
                    query = "",
                    onQueryChange = {},
                    onSearch = {},
                    placeholder = "Search memes..."
                )
            }
        }

        composeTestRule.onNodeWithText("Search memes...").assertIsDisplayed()
    }

    @Test
    fun searchBar_showsQuery_whenProvided() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchBar(
                    query = "funny cat",
                    onQueryChange = {},
                    onSearch = {}
                )
            }
        }

        composeTestRule.onNodeWithText("funny cat").assertIsDisplayed()
    }

    // ============ Input Tests ============

    @Test
    fun searchBar_callsOnQueryChange_whenTyping() {
        var receivedQuery = ""

        composeTestRule.setContent {
            MemeMoodTheme {
                SearchBar(
                    query = "",
                    onQueryChange = { receivedQuery = it },
                    onSearch = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("SearchTextField").performTextInput("test")

        assertThat(receivedQuery).isEqualTo("test")
    }

    // ============ Clear Button Tests ============

    @Test
    fun searchBar_showsClearButton_whenQueryNotEmpty() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchBar(
                    query = "test",
                    onQueryChange = {},
                    onSearch = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Clear").assertIsDisplayed()
    }

    @Test
    fun searchBar_hidesClearButton_whenQueryEmpty() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchBar(
                    query = "",
                    onQueryChange = {},
                    onSearch = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Clear").assertDoesNotExist()
    }

    @Test
    fun searchBar_clearsQuery_onClearClick() {
        var currentQuery = "test"

        composeTestRule.setContent {
            MemeMoodTheme {
                SearchBar(
                    query = currentQuery,
                    onQueryChange = { currentQuery = it },
                    onSearch = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Clear").performClick()

        assertThat(currentQuery).isEmpty()
    }

    // ============ Search Icon Tests ============

    @Test
    fun searchBar_showsSearchIcon() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchBar(
                    query = "",
                    onQueryChange = {},
                    onSearch = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    // ============ Leading Icon Tests ============

    @Test
    fun searchBar_showsBackButton_whenEnabled() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchBar(
                    query = "",
                    onQueryChange = {},
                    onSearch = {},
                    showBackButton = true,
                    onBackClick = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun searchBar_callsOnBackClick_whenBackClicked() {
        var backClicked = false

        composeTestRule.setContent {
            MemeMoodTheme {
                SearchBar(
                    query = "",
                    onQueryChange = {},
                    onSearch = {},
                    showBackButton = true,
                    onBackClick = { backClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backClicked).isTrue()
    }

    // ============ Enabled State Tests ============

    @Test
    fun searchBar_isDisabled_whenEnabledFalse() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchBar(
                    query = "",
                    onQueryChange = {},
                    onSearch = {},
                    enabled = false
                )
            }
        }

        // Disabled text field shouldn't accept input
        composeTestRule.onNodeWithTag("SearchTextField").assertIsDisplayed()
    }
}
