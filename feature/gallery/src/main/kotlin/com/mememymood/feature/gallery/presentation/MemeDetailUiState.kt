package com.mememymood.feature.gallery.presentation

import com.mememymood.core.model.Meme

data class MemeDetailUiState(
    val meme: Meme? = null,
    val isLoading: Boolean = true,
    val isEditMode: Boolean = false,
    val editedTitle: String = "",
    val editedDescription: String = "",
    val editedEmojis: List<String> = emptyList(),
    val showDeleteDialog: Boolean = false,
    val showEmojiPicker: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val hasUnsavedChanges: Boolean
        get() = meme?.let { original ->
            editedTitle != (original.title ?: "") ||
                editedDescription != (original.description ?: "") ||
                editedEmojis != original.emojiTags.map { it.emoji }
        } ?: false
}
