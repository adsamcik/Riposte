package com.mememymood.feature.search.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.MatchType
import com.mememymood.core.model.Meme
import com.mememymood.core.model.SearchResult
import com.mememymood.core.ui.theme.MemeMoodTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [SearchScreen].
 *
 * Tests verify:
 * - Search bar functionality
 * - Results display
 * - Empty state
 * - Search mode switching
 * - Recent searches
 * - Navigation
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testMemes = listOf(
        createTestMeme(1L, "Funny Cat"),
        createTestMeme(2L, "Doge Meme"),
        createTestMeme(3L, "Success Kid")
    )

    private val testResults = testMemes.mapIndexed { index, meme ->
        SearchResult(
            meme = meme,
            relevanceScore = 1.0f - (index * 0.1f),
            matchType = MatchType.TEXT
        )
    }

    private val recentSearches = listOf("funny", "cat", "reaction")

    // ============ Search Bar Tests ============

    @Test
    fun searchScreen_showsSearchBar() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("SearchBar").assertIsDisplayed()
    }

    @Test
    fun searchScreen_showsPlaceholder_whenEmpty() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Search memes...").assertIsDisplayed()
    }

    @Test
    fun searchScreen_updatesQuery_onTextInput() {
        var receivedIntent: SearchIntent? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(),
                    onIntent = { receivedIntent = it },
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("SearchTextField").performTextInput("funny cat")

        assertThat(receivedIntent).isInstanceOf(SearchIntent.UpdateQuery::class.java)
    }

    @Test
    fun searchScreen_showsClearButton_whenQueryNotEmpty() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(query = "test"),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Clear search").assertIsDisplayed()
    }

    @Test
    fun searchScreen_clearsQuery_onClearClick() {
        var receivedIntent: SearchIntent? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(query = "test"),
                    onIntent = { receivedIntent = it },
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Clear search").performClick()

        assertThat(receivedIntent).isEqualTo(SearchIntent.ClearQuery)
    }

    // ============ Search Mode Tests ============

    @Test
    fun searchScreen_showsSearchModeButtons() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Text").assertIsDisplayed()
        composeTestRule.onNodeWithText("Semantic").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hybrid").assertIsDisplayed()
    }

    @Test
    fun searchScreen_switchesSearchMode_onModeClick() {
        var receivedIntent: SearchIntent? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(),
                    onIntent = { receivedIntent = it },
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Semantic").performClick()

        assertThat(receivedIntent).isInstanceOf(SearchIntent.SetSearchMode::class.java)
    }

    // ============ Results Display Tests ============

    @Test
    fun searchScreen_showsResults_whenResultsExist() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(
                        query = "test",
                        results = testResults,
                        hasSearched = true
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("SearchResultsGrid").assertIsDisplayed()
        
        val resultCards = composeTestRule.onAllNodesWithTag("SearchResultCard")
            .fetchSemanticsNodes()
        
        assertThat(resultCards.size).isEqualTo(3)
    }

    @Test
    fun searchScreen_showsResultTitles() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(
                        query = "test",
                        results = testResults,
                        hasSearched = true
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Funny Cat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Doge Meme").assertIsDisplayed()
    }

    @Test
    fun searchScreen_navigatesToMeme_onResultClick() {
        var navigatedToId: Long? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(
                        query = "test",
                        results = testResults,
                        hasSearched = true
                    ),
                    onIntent = {},
                    onNavigateToMeme = { navigatedToId = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onAllNodesWithTag("SearchResultCard")
            .onFirst()
            .performClick()

        assertThat(navigatedToId).isNotNull()
    }

    // ============ Empty State Tests ============

    @Test
    fun searchScreen_showsNoResults_whenSearchedButEmpty() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(
                        query = "asdfghjkl",
                        results = emptyList(),
                        hasSearched = true
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No results found").assertIsDisplayed()
    }

    @Test
    fun searchScreen_showsSearchPrompt_whenNotSearchedYet() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(
                        query = "",
                        hasSearched = false
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Search for memes").assertIsDisplayed()
    }

    // ============ Loading State Tests ============

    @Test
    fun searchScreen_showsLoadingIndicator_whenSearching() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(
                        query = "test",
                        isSearching = true
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("SearchLoadingIndicator").assertIsDisplayed()
    }

    // ============ Recent Searches Tests ============

    @Test
    fun searchScreen_showsRecentSearches_whenEmpty() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(
                        query = "",
                        recentSearches = recentSearches,
                        hasSearched = false
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Recent").assertIsDisplayed()
        composeTestRule.onNodeWithText("funny").assertIsDisplayed()
        composeTestRule.onNodeWithText("cat").assertIsDisplayed()
    }

    @Test
    fun searchScreen_usesRecentSearch_onClick() {
        var receivedIntent: SearchIntent? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(
                        query = "",
                        recentSearches = recentSearches,
                        hasSearched = false
                    ),
                    onIntent = { receivedIntent = it },
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("funny").performClick()

        assertThat(receivedIntent).isInstanceOf(SearchIntent.SelectRecentSearch::class.java)
    }

    @Test
    fun searchScreen_showsClearRecentButton() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(
                        query = "",
                        recentSearches = recentSearches,
                        hasSearched = false
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Clear").assertIsDisplayed()
    }

    @Test
    fun searchScreen_clearsRecentSearches_onClearClick() {
        var receivedIntent: SearchIntent? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(
                        query = "",
                        recentSearches = recentSearches,
                        hasSearched = false
                    ),
                    onIntent = { receivedIntent = it },
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Clear").performClick()

        assertThat(receivedIntent).isEqualTo(SearchIntent.ClearRecentSearches)
    }

    // ============ Emoji Filter Tests ============

    @Test
    fun searchScreen_showsEmojiFilters() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("EmojiFilterRow").assertIsDisplayed()
    }

    @Test
    fun searchScreen_filtersbyEmoji_onEmojiClick() {
        var receivedIntent: SearchIntent? = null

        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(),
                    onIntent = { receivedIntent = it },
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        // Click on an emoji filter chip
        composeTestRule.onAllNodesWithTag("EmojiFilterChip")
            .onFirst()
            .performClick()

        assertThat(receivedIntent).isInstanceOf(SearchIntent.FilterByEmoji::class.java)
    }

    // ============ Navigation Tests ============

    @Test
    fun searchScreen_showsBackButton() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
    }

    @Test
    fun searchScreen_navigatesBack_onBackClick() {
        var navigatedBack = false

        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = { navigatedBack = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()

        assertThat(navigatedBack).isTrue()
    }

    // ============ Error State Tests ============

    @Test
    fun searchScreen_showsError_whenErrorPresent() {
        composeTestRule.setContent {
            MemeMoodTheme {
                SearchScreen(
                    uiState = SearchUiState(
                        error = "Search failed"
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Search failed").assertIsDisplayed()
    }

    // ============ Helper Functions ============

    private fun createTestMeme(id: Long, title: String) = Meme(
        id = id,
        filePath = "/test/meme_$id.jpg",
        fileName = "meme_$id.jpg",
        mimeType = "image/jpeg",
        width = 500,
        height = 500,
        fileSizeBytes = 50000,
        importedAt = System.currentTimeMillis(),
        emojiTags = listOf(EmojiTag("😂", "laughing")),
        title = title
    )
}
