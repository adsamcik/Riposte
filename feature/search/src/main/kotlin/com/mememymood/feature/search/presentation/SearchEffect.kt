package com.mememymood.feature.search.presentation

sealed interface SearchEffect {
    data class NavigateToMeme(val memeId: Long) : SearchEffect
    data class ShowError(val message: String) : SearchEffect
    data class ShowSnackbar(val message: String) : SearchEffect
}
