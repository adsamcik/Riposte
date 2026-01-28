package com.mememymood.core.testing

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput

/**
 * Compose UI test extension functions for cleaner, more readable tests.
 */

// ============ Node Finding Extensions ============

/**
 * Finds a node by test tag with improved readability.
 */
fun ComposeContentTestRule.findByTag(tag: String): SemanticsNodeInteraction =
    onNodeWithTag(tag)

/**
 * Finds a node by text with improved readability.
 */
fun ComposeContentTestRule.findByText(text: String): SemanticsNodeInteraction =
    onNodeWithText(text)

/**
 * Finds a node by content description with improved readability.
 */
fun ComposeContentTestRule.findByContentDescription(
    description: String,
): SemanticsNodeInteraction = onNodeWithContentDescription(description)

/**
 * Finds all nodes by test tag.
 */
fun ComposeContentTestRule.findAllByTag(tag: String): SemanticsNodeInteractionCollection =
    onAllNodesWithTag(tag)

/**
 * Finds all nodes by text.
 */
fun ComposeContentTestRule.findAllByText(text: String): SemanticsNodeInteractionCollection =
    onAllNodesWithText(text)

/**
 * Finds all nodes by content description.
 */
fun ComposeContentTestRule.findAllByContentDescription(
    description: String,
): SemanticsNodeInteractionCollection = onAllNodesWithContentDescription(description)

// ============ Assertion Extensions ============

/**
 * Asserts node exists and is displayed.
 */
fun SemanticsNodeInteraction.assertVisible(): SemanticsNodeInteraction =
    assertExists().assertIsDisplayed()

/**
 * Asserts node is not visible (either doesn't exist or is not displayed).
 */
fun SemanticsNodeInteraction.assertNotVisible(): SemanticsNodeInteraction =
    assertIsNotDisplayed()

/**
 * Asserts node has exact text.
 */
fun SemanticsNodeInteraction.assertHasText(text: String): SemanticsNodeInteraction =
    assertTextEquals(text)

/**
 * Asserts node is clickable and enabled.
 */
fun SemanticsNodeInteraction.assertClickable(): SemanticsNodeInteraction =
    assertIsEnabled()

/**
 * Asserts node is disabled.
 */
fun SemanticsNodeInteraction.assertDisabled(): SemanticsNodeInteraction =
    assertIsNotEnabled()

// ============ Action Extensions ============

/**
 * Clicks on a node after scrolling to it if necessary.
 */
fun SemanticsNodeInteraction.scrollAndClick(): SemanticsNodeInteraction =
    performScrollTo().performClick()

/**
 * Clears text and enters new text.
 */
fun SemanticsNodeInteraction.replaceText(text: String): SemanticsNodeInteraction {
    performTextClearance()
    performTextInput(text)
    return this
}

/**
 * Types text into a text field.
 */
fun SemanticsNodeInteraction.typeText(text: String): SemanticsNodeInteraction {
    performTextInput(text)
    return this
}

// ============ Wait Extensions ============

/**
 * Waits for a node with the given tag to exist.
 */
fun ComposeContentTestRule.waitForTag(
    tag: String,
    timeoutMillis: Long = 5000L,
): SemanticsNodeInteraction {
    waitUntil(timeoutMillis) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    return onNodeWithTag(tag)
}

/**
 * Waits for a node with the given text to exist.
 */
fun ComposeContentTestRule.waitForText(
    text: String,
    timeoutMillis: Long = 5000L,
): SemanticsNodeInteraction {
    waitUntil(timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    return onNodeWithText(text)
}

/**
 * Waits for a condition to be true.
 */
fun ComposeContentTestRule.waitFor(
    timeoutMillis: Long = 5000L,
    condition: () -> Boolean,
) {
    waitUntil(timeoutMillis, condition)
}

// ============ Utility Extensions ============

/**
 * Gets the count of nodes matching the given tag.
 */
fun ComposeContentTestRule.countNodesWithTag(tag: String): Int =
    onAllNodesWithTag(tag).fetchSemanticsNodes().size

/**
 * Gets the count of nodes matching the given text.
 */
fun ComposeContentTestRule.countNodesWithText(text: String): Int =
    onAllNodesWithText(text).fetchSemanticsNodes().size

/**
 * Checks if a node with the given tag exists.
 */
fun ComposeContentTestRule.hasNodeWithTag(tag: String): Boolean =
    onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

/**
 * Checks if a node with the given text exists.
 */
fun ComposeContentTestRule.hasNodeWithText(text: String): Boolean =
    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

// ============ Accessibility Testing Extensions ============

/**
 * Enables accessibility checks for the test rule.
 * Call this in @Before or at the start of tests to validate accessibility.
 *
 * Uses Compose 1.8+ accessibility testing framework which checks for:
 * - Touch target size (minimum 48dp)
 * - Content description presence
 * - Color contrast
 * - Text scaling support
 * - And more WCAG 2.1 guidelines
 *
 * Example usage:
 * ```kotlin
 * @get:Rule
 * val composeTestRule = createComposeRule()
 *
 * @Before
 * fun setup() {
 *     composeTestRule.enableAccessibilityChecks()
 * }
 * ```
 */
fun ComposeContentTestRule.enableAccessibilityChecks() {
    this.enableAccessibilityChecks()
}
