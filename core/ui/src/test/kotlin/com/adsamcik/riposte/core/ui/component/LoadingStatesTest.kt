package com.adsamcik.riposte.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoadingStatesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `ErrorState shows error emoji and retry button`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                ErrorState(message = "Something went wrong", onRetry = {})
            }
        }
        composeRule.onNodeWithText("😵").assertIsDisplayed()
        composeRule.onNodeWithText("Something went wrong").assertIsDisplayed()
    }

    @Test
    fun `ErrorState hides retry button when onRetry is null`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                ErrorState(message = "Error occurred")
            }
        }
        composeRule.onNodeWithText("😵").assertIsDisplayed()
        composeRule.onNodeWithText("Error occurred").assertIsDisplayed()
    }

    @Test
    fun `NoSearchResults displays query in message`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                NoSearchResults(query = "funny cat")
            }
        }
        composeRule.onNodeWithText("🔍").assertIsDisplayed()
    }
}
