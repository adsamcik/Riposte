package com.adsamcik.riposte.feature.gallery.presentation

import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.feature.gallery.domain.usecase.SimilarMemesStatus

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
    val similarMemesStatus: SimilarMemesStatus? = null,
    val isLoadingSimilar: Boolean = false,
    val allMemeIds: List<Long> = emptyList(),
) {
    val hasUnsavedChanges: Boolean
        get() =
            meme?.let { original ->
                editedTitle != (original.title ?: "") ||
                    editedDescription != (original.description ?: "") ||
                    editedEmojis != original.emojiTags.map { it.emoji }
            } ?: false
}
