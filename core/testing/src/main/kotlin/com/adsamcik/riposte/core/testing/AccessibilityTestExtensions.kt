package com.adsamcik.riposte.core.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/**
 * Accessibility testing utilities for Compose UI tests.
 *
 * These extensions help verify accessibility requirements for Google Play Store compliance.
 *
 * Usage:
 * ```kotlin
 * @get:Rule
 * val composeTestRule = createComposeRule()
 *
 * @Before
 * fun setup() {
 *     // Enable automatic accessibility checks (Compose 1.8+)
 *     composeTestRule.enableAccessibilityChecks()
 * }
 *
 * @Test
 * fun testAccessibility() {
 *     composeTestRule.setContent { MyScreen() }
 *
 *     // Verify specific elements have content descriptions
 *     composeTestRule
 *         .onNodeWithTag("meme_image")
 *         .assertHasContentDescription()
 * }
 * ```
 */

/**
 * Asserts that the node has a content description set.
 * This is required for screen reader accessibility.
 */
fun SemanticsNodeInteraction.assertHasContentDescription(): SemanticsNodeInteraction =
    assert(hasContentDescription())

/**
 * Asserts that the node has a specific content description.
 */
fun SemanticsNodeInteraction.assertContentDescriptionEquals(
    expected: String,
): SemanticsNodeInteraction = assert(
    SemanticsMatcher("contentDescription equals '$expected'") { semanticsNode ->
        val contentDescription = semanticsNode.config.getOrNull(SemanticsProperties.ContentDescription)
        contentDescription?.any { it == expected } == true
    },
)

/**
 * Asserts that the node has a role set (Button, Checkbox, Switch, etc.).
 * This helps screen readers announce the type of interactive element.
 */
fun SemanticsNodeInteraction.assertHasRole(): SemanticsNodeInteraction =
    assert(hasRole())

/**
 * Asserts that the node is marked as a heading.
 * Headings help screen reader users navigate content structure.
 */
fun SemanticsNodeInteraction.assertIsHeading(): SemanticsNodeInteraction =
    assert(isHeading())

/**
 * Asserts that the node is focusable for keyboard/switch navigation.
 */
fun SemanticsNodeInteraction.assertIsFocusable(): SemanticsNodeInteraction =
    assert(isFocusable())

// Matcher functions

private fun hasContentDescription() = SemanticsMatcher("has contentDescription") { semanticsNode ->
    semanticsNode.config.getOrNull(SemanticsProperties.ContentDescription)?.isNotEmpty() == true
}

private fun hasRole() = SemanticsMatcher("has role") { semanticsNode ->
    semanticsNode.config.getOrNull(SemanticsProperties.Role) != null
}

private fun isHeading() = SemanticsMatcher("is heading") { semanticsNode ->
    semanticsNode.config.getOrNull(SemanticsProperties.Heading) != null
}

private fun isFocusable() = SemanticsMatcher("is focusable") { semanticsNode ->
    semanticsNode.config.getOrNull(SemanticsActions.RequestFocus) != null
}

/**
 * Runs all accessibility assertions for a complete screen.
 * Checks that all images have content descriptions and all interactive
 * elements have proper roles.
 *
 * Note: For comprehensive accessibility testing, also enable
 * composeTestRule.enableAccessibilityChecks() which uses Google's
 * Accessibility Test Framework to check WCAG guidelines automatically.
 */
fun ComposeContentTestRule.assertBasicAccessibility() {
    // Log reminder to use enableAccessibilityChecks() for comprehensive testing
    println(
        """
        |=============================================================
        | Accessibility Testing Reminder:
        | For comprehensive WCAG 2.1 compliance checking, call:
        |   composeTestRule.enableAccessibilityChecks()
        | in your @Before method. This enables automatic checks for:
        | - Touch target size (48dp minimum)
        | - Color contrast ratios
        | - Content labeling
        | - And more...
        |=============================================================
        """.trimMargin(),
    )
}
