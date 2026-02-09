package com.adsamcik.riposte.feature.settings.data.repository

import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.model.AppPreferences
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.SharingPreferences
import com.adsamcik.riposte.core.model.UserDensityPreference
import com.adsamcik.riposte.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject

/**
 * Default implementation of [SettingsRepository] using [PreferencesDataStore].
 */
class DefaultSettingsRepository @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore,
) : SettingsRepository {

    override val appPreferences: Flow<AppPreferences> =
        preferencesDataStore.appPreferences

    override val sharingPreferences: Flow<SharingPreferences> =
        preferencesDataStore.sharingPreferences

    override suspend fun setDarkMode(mode: DarkMode) {
        preferencesDataStore.setDarkMode(mode)
    }

    override suspend fun setDynamicColors(enabled: Boolean) {
        val current = preferencesDataStore.appPreferences.first()
        preferencesDataStore.updateAppPreferences(current.copy(dynamicColors = enabled))
    }

    override suspend fun setEnableSemanticSearch(enabled: Boolean) {
        val current = preferencesDataStore.appPreferences.first()
        preferencesDataStore.updateAppPreferences(current.copy(enableSemanticSearch = enabled))
    }

    override suspend fun setSaveSearchHistory(save: Boolean) {
        val current = preferencesDataStore.appPreferences.first()
        preferencesDataStore.updateAppPreferences(current.copy(saveSearchHistory = save))
    }

    override suspend fun setHoldToShareDelay(delayMs: Long) {
        val current = preferencesDataStore.appPreferences.first()
        preferencesDataStore.updateAppPreferences(current.copy(holdToShareDelayMs = delayMs))
    }

    override suspend fun setUseNativeShareDialog(enabled: Boolean) {
        val current = preferencesDataStore.sharingPreferences.first()
        preferencesDataStore.updateSharingPreferences(current.copy(useNativeShareDialog = enabled))
    }

    override suspend fun setDefaultFormat(format: ImageFormat) {
        val current = preferencesDataStore.sharingPreferences.first()
        preferencesDataStore.updateSharingPreferences(current.copy(defaultFormat = format))
    }

    override suspend fun setDefaultQuality(quality: Int) {
        val current = preferencesDataStore.sharingPreferences.first()
        preferencesDataStore.updateSharingPreferences(current.copy(defaultQuality = quality))
    }

    override suspend fun setDefaultMaxDimension(dimension: Int) {
        val current = preferencesDataStore.sharingPreferences.first()
        preferencesDataStore.updateSharingPreferences(
            current.copy(maxWidth = dimension, maxHeight = dimension)
        )
    }

    override suspend fun setStripMetadata(strip: Boolean) {
        val current = preferencesDataStore.sharingPreferences.first()
        preferencesDataStore.updateSharingPreferences(current.copy(stripMetadata = strip))
    }

    override suspend fun setGridDensity(preference: UserDensityPreference) {
        val current = preferencesDataStore.appPreferences.first()
        preferencesDataStore.updateAppPreferences(current.copy(userDensityPreference = preference))
    }

    override suspend fun exportPreferences(): String {
        val sharingPrefs = preferencesDataStore.sharingPreferences.first()
        val appPrefs = preferencesDataStore.appPreferences.first()

        val exportData = mapOf(
            "version" to JsonPrimitive(1),
            "timestamp" to JsonPrimitive(System.currentTimeMillis()),
            "sharingPreferences" to JsonObject(
                mapOf(
                    "defaultFormat" to JsonPrimitive(sharingPrefs.defaultFormat.name),
                    "defaultQuality" to JsonPrimitive(sharingPrefs.defaultQuality),
                    "maxWidth" to JsonPrimitive(sharingPrefs.maxWidth),
                    "maxHeight" to JsonPrimitive(sharingPrefs.maxHeight),
                )
            ),
            "appPreferences" to JsonObject(
                mapOf(
                    "darkMode" to JsonPrimitive(appPrefs.darkMode.name),
                    "dynamicColors" to JsonPrimitive(appPrefs.dynamicColors),
                    "enableSemanticSearch" to JsonPrimitive(appPrefs.enableSemanticSearch),
                    "saveSearchHistory" to JsonPrimitive(appPrefs.saveSearchHistory),
                )
            ),
        )

        return Json.encodeToString(JsonObject.serializer(), JsonObject(exportData))
    }

    override suspend fun importPreferences(json: String): Result<Unit> = runCatching {
        val jsonElement = Json.parseToJsonElement(json)

        if (jsonElement !is JsonObject) {
            throw IllegalArgumentException("Invalid backup format: expected JSON object")
        }

        val sharingPrefsJson = jsonElement["sharingPreferences"] as? JsonObject
        val appPrefsJson = jsonElement["appPreferences"] as? JsonObject

        // Import sharing preferences
        if (sharingPrefsJson != null) {
            val currentSharing = preferencesDataStore.sharingPreferences.first()
            val formatName = (sharingPrefsJson["defaultFormat"] as? JsonPrimitive)?.contentOrNull
            val format = formatName?.let {
                runCatching { ImageFormat.valueOf(it) }.getOrNull()
            } ?: currentSharing.defaultFormat

            preferencesDataStore.updateSharingPreferences(
                currentSharing.copy(
                    defaultFormat = format,
                    defaultQuality = (sharingPrefsJson["defaultQuality"] as? JsonPrimitive)?.intOrNull
                        ?: currentSharing.defaultQuality,
                    maxWidth = (sharingPrefsJson["maxWidth"] as? JsonPrimitive)?.intOrNull
                        ?: currentSharing.maxWidth,
                    maxHeight = (sharingPrefsJson["maxHeight"] as? JsonPrimitive)?.intOrNull
                        ?: currentSharing.maxHeight,
                )
            )
        }

        // Import app preferences
        if (appPrefsJson != null) {
            val currentApp = preferencesDataStore.appPreferences.first()
            val darkModeName = (appPrefsJson["darkMode"] as? JsonPrimitive)?.contentOrNull
            val darkMode = darkModeName?.let {
                runCatching { DarkMode.valueOf(it) }.getOrNull()
            } ?: currentApp.darkMode

            preferencesDataStore.updateAppPreferences(
                currentApp.copy(
                    darkMode = darkMode,
                    dynamicColors = (appPrefsJson["dynamicColors"] as? JsonPrimitive)?.booleanOrNull
                        ?: currentApp.dynamicColors,
                    enableSemanticSearch = (appPrefsJson["enableSemanticSearch"] as? JsonPrimitive)?.booleanOrNull
                        ?: currentApp.enableSemanticSearch,
                    saveSearchHistory = (appPrefsJson["saveSearchHistory"] as? JsonPrimitive)?.booleanOrNull
                        ?: currentApp.saveSearchHistory,
                )
            )
        }
    }
}
