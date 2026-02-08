package com.adsamcik.riposte.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun searchBar_displaysPlaceholder() {
        composeTestRule.setContent {
            SearchBar(
                query = "",
                onQueryChange = {},
                onSearch = {},
                placeholder = "Search memes..."
            )
        }

        composeTestRule.onNodeWithText("Search memes...").assertIsDisplayed()
    }

    @Test
    fun searchBar_displaysCustomPlaceholder() {
        composeTestRule.setContent {
            SearchBar(
                query = "",
                onQueryChange = {},
                onSearch = {},
                placeholder = "Find your memes"
            )
        }

        composeTestRule.onNodeWithText("Find your memes").assertIsDisplayed()
    }

    @Test
    fun searchBar_displaysSearchIcon() {
        composeTestRule.setContent {
            SearchBar(
                query = "",
                onQueryChange = {},
                onSearch = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @Test
    fun searchBar_showsClearButtonWhenQueryNotEmpty() {
        composeTestRule.setContent {
            SearchBar(
                query = "test query",
                onQueryChange = {},
                onSearch = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Clear search").assertIsDisplayed()
    }

    @Test
    fun searchBar_hidesClearButtonWhenQueryEmpty() {
        composeTestRule.setContent {
            SearchBar(
                query = "",
                onQueryChange = {},
                onSearch = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Clear search").assertDoesNotExist()
    }

    @Test
    fun searchBar_displaysQueryText() {
        composeTestRule.setContent {
            SearchBar(
                query = "funny cats",
                onQueryChange = {},
                onSearch = {}
            )
        }

        composeTestRule.onNodeWithText("funny cats").assertIsDisplayed()
    }

    @Test
    fun searchBar_textInputCallsOnQueryChange() {
        var capturedQuery = ""
        
        composeTestRule.setContent {
            SearchBar(
                query = capturedQuery,
                onQueryChange = { capturedQuery = it },
                onSearch = {}
            )
        }

        composeTestRule.onNode(hasText("Search memes...")).performTextInput("test")
        
        assertThat(capturedQuery).isEqualTo("test")
    }

    @Test
    fun searchBar_clearButtonClearsQuery() {
        var capturedQuery = "some text"
        
        composeTestRule.setContent {
            SearchBar(
                query = capturedQuery,
                onQueryChange = { capturedQuery = it },
                onSearch = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Clear search").performClick()
        
        assertThat(capturedQuery).isEmpty()
    }

    @Test
    fun searchBar_imeActionCallsOnSearch() {
        var searchCalled = false
        
        composeTestRule.setContent {
            SearchBar(
                query = "test",
                onQueryChange = {},
                onSearch = { searchCalled = true }
            )
        }

        composeTestRule.onNodeWithText("test").performImeAction()
        
        assertThat(searchCalled).isTrue()
    }
}
