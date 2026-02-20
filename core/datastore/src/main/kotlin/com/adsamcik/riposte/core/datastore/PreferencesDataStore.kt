package com.adsamcik.riposte.core.datastore

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
import com.adsamcik.riposte.core.model.AppPreferences
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.SharingPreferences
import com.adsamcik.riposte.core.model.UserDensityPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "riposte_preferences",
)

/**
 * DataStore-based storage for app preferences.
 */
@Singleton
class PreferencesDataStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private object PreferencesKeys {
            // Sharing preferences
            val DEFAULT_FORMAT = stringPreferencesKey("default_format")
            val DEFAULT_QUALITY = intPreferencesKey("default_quality")
            val MAX_WIDTH = intPreferencesKey("max_width")
            val MAX_HEIGHT = intPreferencesKey("max_height")
            val STRIP_METADATA = booleanPreferencesKey("strip_metadata")

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
            val SORT_EMOJIS_BY_USAGE = booleanPreferencesKey("sort_emojis_by_usage")

            // Search preferences - use string key with JSON to preserve order
            val RECENT_SEARCHES = stringPreferencesKey("recent_searches_json")

            // Suggestion preferences
            val LAST_SESSION_SUGGESTION_IDS = stringSetPreferencesKey("last_session_suggestion_ids")

            // Onboarding tip preferences
            val HAS_SHOWN_EMOJI_TIP = booleanPreferencesKey("has_shown_emoji_tip")
            val HAS_SHOWN_SEARCH_TIP = booleanPreferencesKey("has_shown_search_tip")
            val HAS_SHOWN_SHARE_TIP = booleanPreferencesKey("has_shown_share_tip")

            // Milestone preferences
            val UNLOCKED_MILESTONES = stringPreferencesKey("unlocked_milestones_json")
        }

        /**
         * Flow of sharing preferences.
         */
        val sharingPreferences: Flow<SharingPreferences> =
            context.dataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }
                .map { prefs ->
                    SharingPreferences(
                        defaultFormat =
                            prefs[PreferencesKeys.DEFAULT_FORMAT]?.let {
                                ImageFormat.valueOf(it)
                            } ?: ImageFormat.JPEG,
                        defaultQuality = prefs[PreferencesKeys.DEFAULT_QUALITY] ?: 85,
                        maxWidth = prefs[PreferencesKeys.MAX_WIDTH] ?: 1080,
                        maxHeight = prefs[PreferencesKeys.MAX_HEIGHT] ?: 1080,
                        stripMetadata = prefs[PreferencesKeys.STRIP_METADATA] ?: true,
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
            }
        }

        /**
         * Flow of app preferences.
         */
        val appPreferences: Flow<AppPreferences> =
            context.dataStore.data
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
                        userDensityPreference =
                            prefs[PreferencesKeys.USER_DENSITY_PREFERENCE]?.let {
                                UserDensityPreference.valueOf(it)
                            } ?: UserDensityPreference.AUTO,
                        holdToShareDelayMs = prefs[PreferencesKeys.HOLD_TO_SHARE_DELAY_MS] ?: 600L,
                        sortEmojisByUsage = prefs[PreferencesKeys.SORT_EMOJIS_BY_USAGE] ?: true,
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
                prefs[PreferencesKeys.SORT_EMOJIS_BY_USAGE] = preferences.sortEmojisByUsage
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
        val recentSearches: Flow<List<String>> =
            context.dataStore.data
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
                            Timber.e(e, "Failed to decode recent searches JSON")
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
                val current =
                    currentJson?.let {
                        try {
                            Json.decodeFromString<List<String>>(it).toMutableList()
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to decode recent searches for adding")
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
                val current =
                    currentJson?.let {
                        try {
                            Json.decodeFromString<List<String>>(it).toMutableList()
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to decode recent searches for deleting")
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
        val lastSessionSuggestionIds: Flow<Set<Long>> =
            context.dataStore.data
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
        val hasShownEmojiTip: Flow<Boolean> =
            context.dataStore.data
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
        val hasShownSearchTip: Flow<Boolean> =
            context.dataStore.data
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
        val hasShownShareTip: Flow<Boolean> =
            context.dataStore.data
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

        // region Milestones

        /**
         * Flow of unlocked milestone IDs with their unlock timestamps.
         */
        val unlockedMilestones: Flow<Map<String, Long>> =
            context.dataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }
                .map { prefs ->
                    prefs[PreferencesKeys.UNLOCKED_MILESTONES]?.let { json ->
                        try {
                            Json.decodeFromString<Map<String, Long>>(json)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to decode unlocked milestones JSON")
                            emptyMap()
                        }
                    } ?: emptyMap()
                }

        /**
         * Marks a milestone as unlocked with the current timestamp.
         */
        suspend fun unlockMilestone(milestoneId: String) {
            context.dataStore.edit { prefs ->
                val currentJson = prefs[PreferencesKeys.UNLOCKED_MILESTONES]
                val current =
                    currentJson?.let {
                        try {
                            Json.decodeFromString<Map<String, Long>>(it).toMutableMap()
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to decode milestones for unlocking")
                            mutableMapOf()
                        }
                    } ?: mutableMapOf()
                if (milestoneId !in current) {
                    current[milestoneId] = System.currentTimeMillis()
                    prefs[PreferencesKeys.UNLOCKED_MILESTONES] = Json.encodeToString(current)
                }
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
