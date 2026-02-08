package com.adsamcik.riposte.core.testing

import com.adsamcik.riposte.core.model.AppPreferences
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.SharingPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Fake implementation of PreferencesDataStore for testing.
 *
 * Provides in-memory storage of preferences with flow-based observation.
 * All methods are synchronous for easy testing.
 *
 * Usage:
 * ```kotlin
 * val fakeDataStore = FakePreferencesDataStore()
 * fakeDataStore.setDarkMode(DarkMode.DARK)
 * 
 * viewModel.uiState.test {
 *     assertThat(awaitItem().darkMode).isEqualTo(DarkMode.DARK)
 * }
 * ```
 */
class FakePreferencesDataStore {

    private val _appPreferences = MutableStateFlow(DEFAULT_APP_PREFERENCES)
    private val _sharingPreferences = MutableStateFlow(DEFAULT_SHARING_PREFERENCES)
    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())

    /**
     * Flow of app preferences.
     */
    val appPreferences: Flow<AppPreferences> = _appPreferences.asStateFlow()

    /**
     * Flow of sharing preferences.
     */
    val sharingPreferences: Flow<SharingPreferences> = _sharingPreferences.asStateFlow()

    /**
     * Flow of recent searches.
     */
    val recentSearches: Flow<List<String>> = _recentSearches.asStateFlow()

    // ============ App Preferences ============

    /**
     * Gets the current app preferences snapshot.
     */
    fun currentAppPreferences(): AppPreferences = _appPreferences.value

    /**
     * Updates all app preferences at once.
     */
    suspend fun updateAppPreferences(preferences: AppPreferences) {
        _appPreferences.value = preferences
    }

    /**
     * Sets the dark mode preference.
     */
    suspend fun setDarkMode(mode: DarkMode) {
        _appPreferences.update { current: AppPreferences -> current.copy(darkMode = mode) }
    }

    /**
     * Sets whether dynamic colors are enabled.
     */
    suspend fun setDynamicColors(enabled: Boolean) {
        _appPreferences.update { current: AppPreferences -> current.copy(dynamicColors = enabled) }
    }

    /**
     * Sets the grid columns count.
     */
    suspend fun setGridColumns(columns: Int) {
        _appPreferences.update { current: AppPreferences -> current.copy(gridColumns = columns.coerceIn(2, 4)) }
    }

    /**
     * Sets whether emoji names are shown.
     */
    suspend fun setShowEmojiNames(show: Boolean) {
        _appPreferences.update { current: AppPreferences -> current.copy(showEmojiNames = show) }
    }

    /**
     * Sets whether semantic search is enabled.
     */
    suspend fun setEnableSemanticSearch(enabled: Boolean) {
        _appPreferences.update { current: AppPreferences -> current.copy(enableSemanticSearch = enabled) }
    }

    /**
     * Sets whether auto text extraction is enabled.
     */
    suspend fun setAutoExtractText(enabled: Boolean) {
        _appPreferences.update { current: AppPreferences -> current.copy(autoExtractText = enabled) }
    }

    /**
     * Sets whether search history is saved.
     */
    suspend fun setSaveSearchHistory(enabled: Boolean) {
        _appPreferences.update { current: AppPreferences -> current.copy(saveSearchHistory = enabled) }
    }

    // ============ Sharing Preferences ============

    /**
     * Gets the current sharing preferences snapshot.
     */
    fun currentSharingPreferences(): SharingPreferences = _sharingPreferences.value

    /**
     * Updates all sharing preferences at once.
     */
    suspend fun updateSharingPreferences(preferences: SharingPreferences) {
        _sharingPreferences.value = preferences
    }

    /**
     * Adds a package to recent share targets.
     */
    suspend fun addRecentShareTarget(packageName: String) {
        _sharingPreferences.update { prefs: SharingPreferences ->
            val current = prefs.recentShareTargets.toMutableList()
            current.remove(packageName)
            current.add(0, packageName)
            prefs.copy(recentShareTargets = current.take(10))
        }
    }

    /**
     * Toggles a package as a favorite share target.
     */
    suspend fun toggleFavoriteShareTarget(packageName: String) {
        _sharingPreferences.update { prefs: SharingPreferences ->
            val favorites = prefs.favoriteShareTargets.toMutableList()
            if (packageName in favorites) {
                favorites.remove(packageName)
            } else {
                favorites.add(packageName)
            }
            prefs.copy(favoriteShareTargets = favorites)
        }
    }

    // ============ Recent Searches ============

    /**
     * Gets the current recent searches snapshot.
     */
    fun currentRecentSearches(): List<String> = _recentSearches.value

    /**
     * Adds a search query to recent searches.
     */
    suspend fun addRecentSearch(query: String) {
        _recentSearches.update { searches ->
            val updated = searches.toMutableList()
            updated.remove(query)
            updated.add(0, query)
            updated.take(MAX_RECENT_SEARCHES)
        }
    }

    /**
     * Clears all recent searches.
     */
    suspend fun clearRecentSearches() {
        _recentSearches.value = emptyList()
    }

    // ============ Test Helpers ============

    /**
     * Resets all preferences to defaults.
     */
    fun reset() {
        _appPreferences.value = DEFAULT_APP_PREFERENCES
        _sharingPreferences.value = DEFAULT_SHARING_PREFERENCES
        _recentSearches.value = emptyList()
    }

    /**
     * Sets app preferences directly for testing initial state.
     */
    fun setInitialAppPreferences(preferences: AppPreferences) {
        _appPreferences.value = preferences
    }

    /**
     * Sets sharing preferences directly for testing initial state.
     */
    fun setInitialSharingPreferences(preferences: SharingPreferences) {
        _sharingPreferences.value = preferences
    }

    /**
     * Sets recent searches directly for testing.
     */
    fun setInitialRecentSearches(searches: List<String>) {
        _recentSearches.value = searches
    }

    companion object {
        private const val MAX_RECENT_SEARCHES = 20

        val DEFAULT_APP_PREFERENCES = AppPreferences(
            darkMode = DarkMode.SYSTEM,
            dynamicColors = true,
            gridColumns = 2,
            showEmojiNames = false,
            enableSemanticSearch = true,
            autoExtractText = true,
            saveSearchHistory = true
        )

        val DEFAULT_SHARING_PREFERENCES = SharingPreferences(
            defaultFormat = ImageFormat.WEBP,
            defaultQuality = 85,
            maxWidth = 1080,
            maxHeight = 1080,
            stripMetadata = true,
            recentShareTargets = emptyList(),
            favoriteShareTargets = emptyList()
        )
    }
}
