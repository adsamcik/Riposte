package com.adsamcik.riposte.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for Loading State components.
 *
 * Tests verify:
 * - Loading indicator display
 * - Loading messages
 * - Shimmer effects
 * - Full screen loading
 * - Inline loading
 * - Button loading states
 */
@RunWith(AndroidJUnit4::class)
class LoadingStatesComponentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // ============ Circular Loading Tests ============

    @Test
    fun circularLoading_isDisplayed() {
        composeTestRule.setContent {
            RiposteTheme {
                CircularLoadingIndicator()
            }
        }

        composeTestRule.onNodeWithTag("CircularLoadingIndicator").assertIsDisplayed()
    }

    @Test
    fun circularLoading_showsMessage_whenProvided() {
        composeTestRule.setContent {
            RiposteTheme {
                CircularLoadingIndicator(
                    message = "Loading memes...",
                )
            }
        }

        composeTestRule.onNodeWithText("Loading memes...").assertIsDisplayed()
    }

    // ============ Linear Loading Tests ============

    @Test
    fun linearLoading_isDisplayed() {
        composeTestRule.setContent {
            RiposteTheme {
                LinearLoadingIndicator()
            }
        }

        composeTestRule.onNodeWithTag("LinearLoadingIndicator").assertIsDisplayed()
    }

    @Test
    fun linearLoading_showsProgress_whenProvided() {
        composeTestRule.setContent {
            RiposteTheme {
                LinearLoadingIndicator(
                    progress = 0.5f,
                )
            }
        }

        composeTestRule.onNodeWithTag("LinearLoadingIndicator").assertIsDisplayed()
    }

    // ============ Full Screen Loading Tests ============

    @Test
    fun fullScreenLoading_isDisplayed() {
        composeTestRule.setContent {
            RiposteTheme {
                FullScreenLoading()
            }
        }

        composeTestRule.onNodeWithTag("FullScreenLoading").assertIsDisplayed()
    }

    @Test
    fun fullScreenLoading_showsMessage() {
        composeTestRule.setContent {
            RiposteTheme {
                FullScreenLoading(
                    message = "Please wait...",
                )
            }
        }

        composeTestRule.onNodeWithText("Please wait...").assertIsDisplayed()
    }

    @Test
    fun fullScreenLoading_coversScreen() {
        composeTestRule.setContent {
            RiposteTheme {
                FullScreenLoading()
            }
        }

        composeTestRule.onNodeWithTag("FullScreenLoadingOverlay").assertIsDisplayed()
    }

    // ============ Shimmer Effect Tests ============

    @Test
    fun shimmerPlaceholder_isDisplayed() {
        composeTestRule.setContent {
            RiposteTheme {
                ShimmerPlaceholder()
            }
        }

        composeTestRule.onNodeWithTag("ShimmerPlaceholder").assertIsDisplayed()
    }

    @Test
    fun memeCardShimmer_isDisplayed() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeCardShimmer()
            }
        }

        composeTestRule.onNodeWithTag("MemeCardShimmer").assertIsDisplayed()
    }

    @Test
    fun memeGridShimmer_displaysMultipleShimmers() {
        composeTestRule.setContent {
            RiposteTheme {
                MemeGridShimmer(itemCount = 6)
            }
        }

        composeTestRule.onNodeWithTag("MemeGridShimmer").assertIsDisplayed()
    }

    // ============ Button Loading Tests ============

    @Test
    fun loadingButton_showsLoading_whenLoading() {
        composeTestRule.setContent {
            RiposteTheme {
                LoadingButton(
                    onClick = {},
                    loading = true,
                    text = "Submit",
                )
            }
        }

        composeTestRule.onNodeWithTag("ButtonLoadingIndicator").assertIsDisplayed()
    }

    @Test
    fun loadingButton_showsText_whenNotLoading() {
        composeTestRule.setContent {
            RiposteTheme {
                LoadingButton(
                    onClick = {},
                    loading = false,
                    text = "Submit",
                )
            }
        }

        composeTestRule.onNodeWithText("Submit").assertIsDisplayed()
    }

    @Test
    fun loadingButton_hidesText_whenLoading() {
        composeTestRule.setContent {
            RiposteTheme {
                LoadingButton(
                    onClick = {},
                    loading = true,
                    text = "Submit",
                )
            }
        }

        composeTestRule.onNodeWithText("Submit").assertDoesNotExist()
    }

    // ============ Pull to Refresh Tests ============

    @Test
    fun pullToRefresh_showsIndicator_whenRefreshing() {
        composeTestRule.setContent {
            RiposteTheme {
                PullToRefreshIndicator(
                    refreshing = true,
                )
            }
        }

        composeTestRule.onNodeWithTag("PullToRefreshIndicator").assertIsDisplayed()
    }

    // ============ Error with Retry Tests ============

    @Test
    fun errorWithRetry_showsErrorMessage() {
        composeTestRule.setContent {
            RiposteTheme {
                ErrorWithRetry(
                    message = "Something went wrong",
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
    }

    @Test
    fun errorWithRetry_showsRetryButton() {
        composeTestRule.setContent {
            RiposteTheme {
                ErrorWithRetry(
                    message = "Error",
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun errorWithRetry_showsLoading_whenRetrying() {
        composeTestRule.setContent {
            RiposteTheme {
                ErrorWithRetry(
                    message = "Error",
                    onRetry = {},
                    retrying = true,
                )
            }
        }

        composeTestRule.onNodeWithTag("RetryLoadingIndicator").assertIsDisplayed()
    }

    // ============ Empty State Tests ============

    @Test
    fun emptyState_showsMessage() {
        composeTestRule.setContent {
            RiposteTheme {
                EmptyState(
                    message = "No memes found",
                )
            }
        }

        composeTestRule.onNodeWithText("No memes found").assertIsDisplayed()
    }

    @Test
    fun emptyState_showsIcon() {
        composeTestRule.setContent {
            RiposteTheme {
                EmptyState(
                    message = "No memes",
                    showIcon = true,
                )
            }
        }

        composeTestRule.onNodeWithTag("EmptyStateIcon").assertIsDisplayed()
    }

    @Test
    fun emptyState_showsAction_whenProvided() {
        composeTestRule.setContent {
            RiposteTheme {
                EmptyState(
                    message = "No memes",
                    actionLabel = "Import",
                    onAction = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Import").assertIsDisplayed()
    }
}
