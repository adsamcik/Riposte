package com.adsamcik.riposte.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for Dialog components.
 *
 * Tests verify:
 * - Confirmation dialogs
 * - Alert dialogs
 * - Selection dialogs
 * - Bottom sheets
 * - Button interactions
 * - Dismissal behavior
 */
@RunWith(AndroidJUnit4::class)
class DialogComponentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ============ Confirmation Dialog Tests ============

    @Test
    fun confirmationDialog_isDisplayed() {
        composeTestRule.setContent {
            RiposteTheme {
                ConfirmationDialog(
                    title = "Delete Meme",
                    message = "Are you sure?",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("ConfirmationDialog").assertIsDisplayed()
    }

    @Test
    fun confirmationDialog_showsTitle() {
        composeTestRule.setContent {
            RiposteTheme {
                ConfirmationDialog(
                    title = "Delete Meme",
                    message = "Are you sure?",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Delete Meme").assertIsDisplayed()
    }

    @Test
    fun confirmationDialog_showsMessage() {
        composeTestRule.setContent {
            RiposteTheme {
                ConfirmationDialog(
                    title = "Delete",
                    message = "This action cannot be undone.",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("This action cannot be undone.").assertIsDisplayed()
    }

    @Test
    fun confirmationDialog_showsConfirmButton() {
        composeTestRule.setContent {
            RiposteTheme {
                ConfirmationDialog(
                    title = "Delete",
                    message = "Are you sure?",
                    confirmText = "Delete",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun confirmationDialog_showsCancelButton() {
        composeTestRule.setContent {
            RiposteTheme {
                ConfirmationDialog(
                    title = "Delete",
                    message = "Are you sure?",
                    dismissText = "Cancel",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun confirmationDialog_callsOnConfirm() {
        var confirmed = false

        composeTestRule.setContent {
            RiposteTheme {
                ConfirmationDialog(
                    title = "Delete",
                    message = "Are you sure?",
                    confirmText = "Confirm",
                    onConfirm = { confirmed = true },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Confirm").performClick()

        assertThat(confirmed).isTrue()
    }

    @Test
    fun confirmationDialog_callsOnDismiss() {
        var dismissed = false

        composeTestRule.setContent {
            RiposteTheme {
                ConfirmationDialog(
                    title = "Delete",
                    message = "Are you sure?",
                    dismissText = "Cancel",
                    onConfirm = {},
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertThat(dismissed).isTrue()
    }

    // ============ Alert Dialog Tests ============

    @Test
    fun alertDialog_isDisplayed() {
        composeTestRule.setContent {
            RiposteTheme {
                AlertMessageDialog(
                    title = "Error",
                    message = "Something went wrong",
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("AlertDialog").assertIsDisplayed()
    }

    @Test
    fun alertDialog_showsOkButton() {
        composeTestRule.setContent {
            RiposteTheme {
                AlertMessageDialog(
                    title = "Info",
                    message = "Information",
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun alertDialog_dismissesOnOkClick() {
        var dismissed = false

        composeTestRule.setContent {
            RiposteTheme {
                AlertMessageDialog(
                    title = "Info",
                    message = "Information",
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("OK").performClick()

        assertThat(dismissed).isTrue()
    }

    // ============ Selection Dialog Tests ============

    @Test
    fun selectionDialog_isDisplayed() {
        composeTestRule.setContent {
            RiposteTheme {
                SelectionDialog(
                    title = "Select Theme",
                    options = listOf("Light", "Dark", "System"),
                    selectedOption = "System",
                    onSelect = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("SelectionDialog").assertIsDisplayed()
    }

    @Test
    fun selectionDialog_showsAllOptions() {
        composeTestRule.setContent {
            RiposteTheme {
                SelectionDialog(
                    title = "Select Theme",
                    options = listOf("Light", "Dark", "System"),
                    selectedOption = "System",
                    onSelect = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Light").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
        composeTestRule.onNodeWithText("System").assertIsDisplayed()
    }

    @Test
    fun selectionDialog_callsOnSelect() {
        var selected: String? = null

        composeTestRule.setContent {
            RiposteTheme {
                SelectionDialog(
                    title = "Select Theme",
                    options = listOf("Light", "Dark", "System"),
                    selectedOption = "System",
                    onSelect = { selected = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Dark").performClick()

        assertThat(selected).isEqualTo("Dark")
    }

    // ============ Bottom Sheet Tests ============

    @Test
    fun bottomSheet_isDisplayed() {
        composeTestRule.setContent {
            RiposteTheme {
                ActionBottomSheet(
                    onDismiss = {}
                ) {
                    // Content
                }
            }
        }

        composeTestRule.onNodeWithTag("BottomSheet").assertIsDisplayed()
    }

    @Test
    fun bottomSheet_showsDragHandle() {
        composeTestRule.setContent {
            RiposteTheme {
                ActionBottomSheet(
                    onDismiss = {},
                    showDragHandle = true
                ) {
                    // Content
                }
            }
        }

        composeTestRule.onNodeWithTag("DragHandle").assertIsDisplayed()
    }

    // ============ Destructive Confirmation Dialog Tests ============

    @Test
    fun destructiveDialog_showsDestructiveButton() {
        composeTestRule.setContent {
            RiposteTheme {
                DestructiveConfirmationDialog(
                    title = "Delete All",
                    message = "This will delete all memes permanently.",
                    confirmText = "Delete All",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Delete All").assertIsDisplayed()
    }

    // ============ Input Dialog Tests ============

    @Test
    fun inputDialog_isDisplayed() {
        composeTestRule.setContent {
            RiposteTheme {
                InputDialog(
                    title = "Rename",
                    initialValue = "",
                    placeholder = "Enter name",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("InputDialog").assertIsDisplayed()
    }

    @Test
    fun inputDialog_showsInitialValue() {
        composeTestRule.setContent {
            RiposteTheme {
                InputDialog(
                    title = "Rename",
                    initialValue = "Current Name",
                    placeholder = "Enter name",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Current Name").assertIsDisplayed()
    }

    @Test
    fun inputDialog_showsPlaceholder() {
        composeTestRule.setContent {
            RiposteTheme {
                InputDialog(
                    title = "Rename",
                    initialValue = "",
                    placeholder = "Enter name",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Enter name").assertIsDisplayed()
    }
}
