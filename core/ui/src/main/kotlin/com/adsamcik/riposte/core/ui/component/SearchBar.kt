package com.adsamcik.riposte.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.adsamcik.riposte.core.ui.R
import com.adsamcik.riposte.core.ui.theme.RiposteShapes

/**
 * Search bar component for searching memes.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    autoFocus: Boolean = false,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val defaultPlaceholder = stringResource(R.string.ui_search_placeholder)

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier =
            modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    onFocusChanged?.invoke(focusState.isFocused)
                }
                .testTag("SearchBar"),
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(
                text = placeholder ?: defaultPlaceholder,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = TextUnit.Unspecified),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.ui_search_icon_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.ui_search_clear_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        singleLine = true,
        shape = RiposteShapes.SearchBar,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        keyboardOptions =
            KeyboardOptions(
                imeAction = ImeAction.Search,
            ),
        keyboardActions =
            KeyboardActions(
                onSearch = {
                    onSearch()
                    keyboardController?.hide()
                },
            ),
    )
}

/**
 * Search bar with emoji filter buttons.
 */
@Composable
fun SearchBarWithEmoji(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    recentEmojis: List<String>,
    onEmojiClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            modifier = Modifier.padding(bottom = if (recentEmojis.isNotEmpty()) 48.dp else 0.dp),
        )

        // Recent emoji quick filters would go here
        // Implementation depends on UI requirements
    }
}
