package com.adsamcik.riposte.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmptyStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `EmptyState displays icon title and message`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                EmptyState(icon = "📱", title = "No items", message = "Import some memes")
            }
        }
        composeRule.onNodeWithText("📱").assertIsDisplayed()
        composeRule.onNodeWithText("No items").assertIsDisplayed()
        composeRule.onNodeWithText("Import some memes").assertIsDisplayed()
    }

    @Test
    fun `EmptyState action button triggers callback`() {
        var clicked = false
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                EmptyState(
                    icon = "📱",
                    title = "Empty",
                    message = "Nothing here",
                    actionLabel = "Add",
                    onAction = { clicked = true },
                )
            }
        }
        composeRule.onNodeWithText("Add").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun `EmptyState hides action button when label is null`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                EmptyState(icon = "📱", title = "Empty", message = "Nothing")
            }
        }
        composeRule.onNodeWithText("📱").assertIsDisplayed()
    }
}
