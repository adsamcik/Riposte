package com.mememymood.feature.settings.data.repository

import com.mememymood.core.datastore.PreferencesDataStore
import com.mememymood.core.model.AppPreferences
import com.mememymood.core.model.DarkMode
import com.mememymood.core.model.ImageFormat
import com.mememymood.core.model.SharingPreferences
import com.mememymood.feature.settings.domain.repository.SettingsRepository
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

    override suspend fun setKeepMetadata(keep: Boolean) {
        val current = preferencesDataStore.sharingPreferences.first()
        preferencesDataStore.updateSharingPreferences(current.copy(keepMetadata = keep))
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
                    "keepMetadata" to JsonPrimitive(sharingPrefs.keepMetadata),
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
                    keepMetadata = (sharingPrefsJson["keepMetadata"] as? JsonPrimitive)?.booleanOrNull
                        ?: currentSharing.keepMetadata,
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
