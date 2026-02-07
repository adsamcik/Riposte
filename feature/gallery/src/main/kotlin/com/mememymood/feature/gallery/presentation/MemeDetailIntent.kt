package com.mememymood.feature.gallery.presentation

sealed interface MemeDetailIntent {
    data object LoadMeme : MemeDetailIntent
    data object ToggleEditMode : MemeDetailIntent
    data class UpdateTitle(val title: String) : MemeDetailIntent
    data class UpdateDescription(val description: String) : MemeDetailIntent
    data class AddEmoji(val emoji: String) : MemeDetailIntent
    data class RemoveEmoji(val emoji: String) : MemeDetailIntent
    data object ToggleFavorite : MemeDetailIntent
    data object ShowDeleteDialog : MemeDetailIntent
    data object DismissDeleteDialog : MemeDetailIntent
    data object ConfirmDelete : MemeDetailIntent
    data object Share : MemeDetailIntent
    data object OpenShareScreen : MemeDetailIntent
    data object SaveChanges : MemeDetailIntent
    data object DiscardChanges : MemeDetailIntent
    data object ShowEmojiPicker : MemeDetailIntent
    data object DismissEmojiPicker : MemeDetailIntent
    data object Dismiss : MemeDetailIntent
    data object LoadSimilarMemes : MemeDetailIntent
    data class NavigateToSimilarMeme(val memeId: Long) : MemeDetailIntent
}
