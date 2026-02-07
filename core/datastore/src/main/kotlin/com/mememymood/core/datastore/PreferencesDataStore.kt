package com.mememymood.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mememymood.core.model.AppPreferences
import com.mememymood.core.model.DarkMode
import com.mememymood.core.model.ImageFormat
import com.mememymood.core.model.SharingPreferences
import com.mememymood.core.model.UserDensityPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "meme_my_mood_preferences"
)

/**
 * DataStore-based storage for app preferences.
 */
@Singleton
class PreferencesDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private object PreferencesKeys {
        // Sharing preferences
        val DEFAULT_FORMAT = stringPreferencesKey("default_format")
        val DEFAULT_QUALITY = intPreferencesKey("default_quality")
        val MAX_WIDTH = intPreferencesKey("max_width")
        val MAX_HEIGHT = intPreferencesKey("max_height")
        val STRIP_METADATA = booleanPreferencesKey("strip_metadata")
        val RECENT_SHARE_TARGETS = stringSetPreferencesKey("recent_share_targets")
        val FAVORITE_SHARE_TARGETS = stringSetPreferencesKey("favorite_share_targets")

        // App preferences
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val SHOW_EMOJI_NAMES = booleanPreferencesKey("show_emoji_names")
        val ENABLE_SEMANTIC_SEARCH = booleanPreferencesKey("enable_semantic_search")
        val AUTO_EXTRACT_TEXT = booleanPreferencesKey("auto_extract_text")
        val SAVE_SEARCH_HISTORY = booleanPreferencesKey("save_search_history")
        val USER_DENSITY_PREFERENCE = stringPreferencesKey("user_density_preference")
        val HOLD_TO_SHARE_DELAY_MS = longPreferencesKey("hold_to_share_delay_ms")

        // Search preferences - use string key with JSON to preserve order
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches_json")

        // Suggestion preferences
        val LAST_SESSION_SUGGESTION_IDS = stringSetPreferencesKey("last_session_suggestion_ids")

        // Onboarding tip preferences
        val HAS_SHOWN_EMOJI_TIP = booleanPreferencesKey("has_shown_emoji_tip")
        val HAS_SHOWN_SEARCH_TIP = booleanPreferencesKey("has_shown_search_tip")
        val HAS_SHOWN_SHARE_TIP = booleanPreferencesKey("has_shown_share_tip")
    }

    /**
     * Flow of sharing preferences.
     */
    val sharingPreferences: Flow<SharingPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            SharingPreferences(
                defaultFormat = prefs[PreferencesKeys.DEFAULT_FORMAT]?.let { 
                    ImageFormat.valueOf(it) 
                } ?: ImageFormat.WEBP,
                defaultQuality = prefs[PreferencesKeys.DEFAULT_QUALITY] ?: 85,
                maxWidth = prefs[PreferencesKeys.MAX_WIDTH] ?: 1080,
                maxHeight = prefs[PreferencesKeys.MAX_HEIGHT] ?: 1080,
                stripMetadata = prefs[PreferencesKeys.STRIP_METADATA] ?: true,
                recentShareTargets = prefs[PreferencesKeys.RECENT_SHARE_TARGETS]?.toList() ?: emptyList(),
                favoriteShareTargets = prefs[PreferencesKeys.FAVORITE_SHARE_TARGETS]?.toList() ?: emptyList()
            )
        }

    /**
     * Updates sharing preferences.
     */
    suspend fun updateSharingPreferences(preferences: SharingPreferences) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DEFAULT_FORMAT] = preferences.defaultFormat.name
            prefs[PreferencesKeys.DEFAULT_QUALITY] = preferences.defaultQuality
            prefs[PreferencesKeys.MAX_WIDTH] = preferences.maxWidth
            prefs[PreferencesKeys.MAX_HEIGHT] = preferences.maxHeight
            prefs[PreferencesKeys.STRIP_METADATA] = preferences.stripMetadata
            prefs[PreferencesKeys.RECENT_SHARE_TARGETS] = preferences.recentShareTargets.toSet()
            prefs[PreferencesKeys.FAVORITE_SHARE_TARGETS] = preferences.favoriteShareTargets.toSet()
        }
    }

    /**
     * Adds a package to recent share targets.
     */
    suspend fun addRecentShareTarget(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.RECENT_SHARE_TARGETS]?.toMutableList() ?: mutableListOf()
            current.remove(packageName)
            current.add(0, packageName)
            // Keep only the 10 most recent
            prefs[PreferencesKeys.RECENT_SHARE_TARGETS] = current.take(10).toSet()
        }
    }

    /**
     * Flow of app preferences.
     */
    val appPreferences: Flow<AppPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            AppPreferences(
                darkMode = prefs[PreferencesKeys.DARK_MODE]?.let { DarkMode.valueOf(it) } ?: DarkMode.SYSTEM,
                dynamicColors = prefs[PreferencesKeys.DYNAMIC_COLORS] ?: true,
                gridColumns = prefs[PreferencesKeys.GRID_COLUMNS] ?: 2,
                showEmojiNames = prefs[PreferencesKeys.SHOW_EMOJI_NAMES] ?: false,
                enableSemanticSearch = prefs[PreferencesKeys.ENABLE_SEMANTIC_SEARCH] ?: true,
                autoExtractText = prefs[PreferencesKeys.AUTO_EXTRACT_TEXT] ?: true,
                saveSearchHistory = prefs[PreferencesKeys.SAVE_SEARCH_HISTORY] ?: true,
                userDensityPreference = prefs[PreferencesKeys.USER_DENSITY_PREFERENCE]?.let {
                    UserDensityPreference.valueOf(it)
                } ?: UserDensityPreference.AUTO,
                holdToShareDelayMs = prefs[PreferencesKeys.HOLD_TO_SHARE_DELAY_MS] ?: 600L,
            )
        }

    /**
     * Updates app preferences.
     */
    suspend fun updateAppPreferences(preferences: AppPreferences) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DARK_MODE] = preferences.darkMode.name
            prefs[PreferencesKeys.DYNAMIC_COLORS] = preferences.dynamicColors
            prefs[PreferencesKeys.GRID_COLUMNS] = preferences.gridColumns
            prefs[PreferencesKeys.SHOW_EMOJI_NAMES] = preferences.showEmojiNames
            prefs[PreferencesKeys.ENABLE_SEMANTIC_SEARCH] = preferences.enableSemanticSearch
            prefs[PreferencesKeys.AUTO_EXTRACT_TEXT] = preferences.autoExtractText
            prefs[PreferencesKeys.SAVE_SEARCH_HISTORY] = preferences.saveSearchHistory
            prefs[PreferencesKeys.USER_DENSITY_PREFERENCE] = preferences.userDensityPreference.name
            prefs[PreferencesKeys.HOLD_TO_SHARE_DELAY_MS] = preferences.holdToShareDelayMs
        }
    }

    /**
     * Sets the dark mode preference.
     */
    suspend fun setDarkMode(mode: DarkMode) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DARK_MODE] = mode.name
        }
    }

    /**
     * Sets the grid columns preference.
     */
    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.GRID_COLUMNS] = columns.coerceIn(2, 4)
        }
    }

    /**
     * Flow of recent searches.
     */
    val recentSearches: Flow<List<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            prefs[PreferencesKeys.RECENT_SEARCHES]?.let { json ->
                try {
                    Json.decodeFromString<List<String>>(json)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
        }

    /**
     * Adds a search query to recent searches.
     */
    suspend fun addRecentSearch(query: String) {
        context.dataStore.edit { prefs ->
            val currentJson = prefs[PreferencesKeys.RECENT_SEARCHES]
            val current = currentJson?.let {
                try {
                    Json.decodeFromString<List<String>>(it).toMutableList()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } ?: mutableListOf()
            current.remove(query) // Remove if exists to move to front
            current.add(0, query)
            // Keep only the 20 most recent
            prefs[PreferencesKeys.RECENT_SEARCHES] = Json.encodeToString(current.take(MAX_RECENT_SEARCHES))
        }
    }

    /**
     * Deletes a specific search query from recent searches.
     */
    suspend fun deleteRecentSearch(query: String) {
        context.dataStore.edit { prefs ->
            val currentJson = prefs[PreferencesKeys.RECENT_SEARCHES]
            val current = currentJson?.let {
                try {
                    Json.decodeFromString<List<String>>(it).toMutableList()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } ?: mutableListOf()
            current.remove(query)
            prefs[PreferencesKeys.RECENT_SEARCHES] = Json.encodeToString(current)
        }
    }

    /**
     * Clears all recent searches.
     */
    suspend fun clearRecentSearches() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.RECENT_SEARCHES)
        }
    }

    /**
     * Flow of last session's suggestion IDs (for staleness rotation).
     */
    val lastSessionSuggestionIds: Flow<Set<Long>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            prefs[PreferencesKeys.LAST_SESSION_SUGGESTION_IDS]
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet()
                ?: emptySet()
        }

    /**
     * Persists the current session's suggestion IDs for staleness rotation.
     */
    suspend fun updateLastSessionSuggestionIds(ids: Set<Long>) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_SESSION_SUGGESTION_IDS] = ids.map { it.toString() }.toSet()
        }
    }

    // region Onboarding Tips

    /**
     * Whether the emoji tagging tip has been shown.
     */
    val hasShownEmojiTip: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> prefs[PreferencesKeys.HAS_SHOWN_EMOJI_TIP] ?: false }

    /**
     * Whether the smart search tip has been shown.
     */
    val hasShownSearchTip: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> prefs[PreferencesKeys.HAS_SHOWN_SEARCH_TIP] ?: false }

    /**
     * Whether the long-press share tip has been shown.
     */
    val hasShownShareTip: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> prefs[PreferencesKeys.HAS_SHOWN_SHARE_TIP] ?: false }

    /**
     * Marks the emoji tagging tip as shown.
     */
    suspend fun setEmojiTipShown() {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.HAS_SHOWN_EMOJI_TIP] = true
        }
    }

    /**
     * Marks the smart search tip as shown.
     */
    suspend fun setSearchTipShown() {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.HAS_SHOWN_SEARCH_TIP] = true
        }
    }

    /**
     * Marks the long-press share tip as shown.
     */
    suspend fun setShareTipShown() {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.HAS_SHOWN_SHARE_TIP] = true
        }
    }

    // endregion

    /**
     * Clears all preferences. Primarily used for testing.
     */
    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    companion object {
        private const val MAX_RECENT_SEARCHES = 20
    }
}
