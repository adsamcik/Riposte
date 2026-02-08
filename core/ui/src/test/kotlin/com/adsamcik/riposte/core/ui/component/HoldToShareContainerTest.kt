package com.adsamcik.riposte.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HoldToShareContainerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tap triggers onTap callback`() {
        var tapped = false
        var holdCompleted = false

        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                HoldToShareContainer(
                    onTap = { tapped = true },
                    onHoldComplete = { holdCompleted = true },
                    modifier = Modifier
                        .size(100.dp)
                        .testTag("container"),
                ) {
                    Text("Content")
                }
            }
        }

        composeRule.onNodeWithTag("container").performClick()

        assertThat(tapped).isTrue()
        assertThat(holdCompleted).isFalse()
    }

    @Test
    fun `hold gesture triggers onHoldComplete callback`() {
        var tapped = false
        var holdCompleted = false

        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                HoldToShareContainer(
                    onTap = { tapped = true },
                    onHoldComplete = { holdCompleted = true },
                    modifier = Modifier
                        .size(100.dp)
                        .testTag("container"),
                ) {
                    Box(modifier = Modifier.matchParentSize())
                }
            }
        }

        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag("container").performTouchInput {
            down(center)
        }

        // Advance past start delay + full animation duration
        composeRule.mainClock.advanceTimeBy(HOLD_START_DELAY_MS + HOLD_TO_SHARE_DURATION_MS + 100)

        composeRule.onNodeWithTag("container").performTouchInput {
            up()
        }

        composeRule.mainClock.advanceTimeBy(100)

        assertThat(holdCompleted).isTrue()
        assertThat(tapped).isFalse()
    }

    @Test
    fun `progress indicator appears during hold`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                HoldToShareContainer(
                    onTap = {},
                    onHoldComplete = {},
                    modifier = Modifier
                        .size(100.dp)
                        .testTag("container"),
                ) {
                    Box(modifier = Modifier.matchParentSize())
                }
            }
        }

        // Progress overlay should not exist initially
        composeRule.onNodeWithTag(HOLD_TO_SHARE_PROGRESS_TEST_TAG)
            .assertDoesNotExist()

        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag("container").performTouchInput {
            down(center)
        }

        // Advance past start delay so isHolding becomes true
        composeRule.mainClock.advanceTimeBy(HOLD_START_DELAY_MS + 50)

        // Progress overlay should now be visible
        composeRule.onNodeWithTag(HOLD_TO_SHARE_PROGRESS_TEST_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `content is rendered inside container`() {
        composeRule.setContent {
            RiposteTheme(dynamicColor = false) {
                HoldToShareContainer(
                    onTap = {},
                    onHoldComplete = {},
                    modifier = Modifier.size(100.dp),
                ) {
                    Text("Meme Content")
                }
            }
        }

        composeRule.onNodeWithText("Meme Content").assertIsDisplayed()
    }
}
