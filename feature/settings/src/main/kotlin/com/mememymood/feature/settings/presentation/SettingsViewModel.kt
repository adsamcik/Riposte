package com.mememymood.feature.settings.presentation

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mememymood.core.datastore.PreferencesDataStore
import com.mememymood.core.model.DarkMode
import com.mememymood.core.model.ImageFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferencesDataStore: PreferencesDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val supportedLanguages = listOf(
        AppLanguage(code = null, displayName = "System default", nativeName = "System default"),
        AppLanguage(code = "en", displayName = "English", nativeName = "English"),
        AppLanguage(code = "cs", displayName = "Czech", nativeName = "Čeština"),
        AppLanguage(code = "de", displayName = "German", nativeName = "Deutsch"),
        AppLanguage(code = "es", displayName = "Spanish", nativeName = "Español"),
        AppLanguage(code = "pt", displayName = "Portuguese", nativeName = "Português"),
    )

    init {
        loadSettings()
        calculateCacheSize()
        loadCurrentLanguage()
    }

    private fun loadCurrentLanguage() {
        val locales = AppCompatDelegate.getApplicationLocales()
        val currentCode = if (locales.isEmpty) null else locales.toLanguageTags().split(",").firstOrNull()
        _uiState.update {
            it.copy(
                currentLanguage = currentCode,
                availableLanguages = supportedLanguages,
            )
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                preferencesDataStore.appPreferences,
                preferencesDataStore.sharingPreferences,
            ) { appPrefs, sharingPrefs ->
                _uiState.value.copy(
                    darkMode = appPrefs.darkMode,
                    dynamicColorsEnabled = appPrefs.dynamicColors,
                    defaultFormat = sharingPrefs.defaultFormat,
                    defaultQuality = sharingPrefs.defaultQuality,
                    defaultMaxDimension = sharingPrefs.maxWidth, // Using maxWidth as the max dimension setting
                    keepMetadata = sharingPrefs.keepMetadata,
                    enableSemanticSearch = appPrefs.enableSemanticSearch,
                    saveSearchHistory = appPrefs.saveSearchHistory,
                    appVersion = getAppVersion(),
                    isLoading = false,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
            "${packageInfo.versionName} ($versionCode)"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            // Appearance
            is SettingsIntent.SetDarkMode -> setDarkMode(intent.mode)
            is SettingsIntent.SetLanguage -> setLanguage(intent.languageCode)
            is SettingsIntent.SetDynamicColors -> setDynamicColors(intent.enabled)

            // Sharing
            is SettingsIntent.SetDefaultFormat -> setDefaultFormat(intent.format)
            is SettingsIntent.SetDefaultQuality -> setDefaultQuality(intent.quality)
            is SettingsIntent.SetDefaultMaxDimension -> setDefaultMaxDimension(intent.dimension)
            is SettingsIntent.SetKeepMetadata -> setKeepMetadata(intent.keep)

            // Search
            is SettingsIntent.SetEnableSemanticSearch -> setEnableSemanticSearch(intent.enabled)
            is SettingsIntent.SetSaveSearchHistory -> setSaveSearchHistory(intent.save)

            // Storage
            is SettingsIntent.CalculateCacheSize -> calculateCacheSize()
            is SettingsIntent.ShowClearCacheDialog -> showClearCacheDialog()
            is SettingsIntent.DismissDialog -> dismissDialog()
            is SettingsIntent.ConfirmClearCache -> confirmClearCache()
            is SettingsIntent.ExportData -> exportData()
            is SettingsIntent.ImportData -> importData()

            // About
            is SettingsIntent.OpenLicenses -> openLicenses()
            is SettingsIntent.OpenPrivacyPolicy -> openPrivacyPolicy()
        }
    }

    private fun setDarkMode(mode: DarkMode) {
        viewModelScope.launch {
            preferencesDataStore.setDarkMode(mode)
        }
    }

    private fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch {
            val current = preferencesDataStore.appPreferences.first()
            preferencesDataStore.updateAppPreferences(current.copy(dynamicColors = enabled))
        }
    }

    private fun setLanguage(languageCode: String?) {
        val localeList = if (languageCode == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
        _uiState.update { it.copy(currentLanguage = languageCode) }
    }

    private fun setEnableSemanticSearch(enabled: Boolean) {
        viewModelScope.launch {
            val current = preferencesDataStore.appPreferences.first()
            preferencesDataStore.updateAppPreferences(current.copy(enableSemanticSearch = enabled))
        }
    }

    private fun setSaveSearchHistory(save: Boolean) {
        viewModelScope.launch {
            val current = preferencesDataStore.appPreferences.first()
            preferencesDataStore.updateAppPreferences(current.copy(saveSearchHistory = save))
        }
    }

    private fun setDefaultFormat(format: ImageFormat) {
        viewModelScope.launch {
            val current = preferencesDataStore.sharingPreferences.first()
            preferencesDataStore.updateSharingPreferences(current.copy(defaultFormat = format))
        }
    }

    private fun setDefaultQuality(quality: Int) {
        viewModelScope.launch {
            val current = preferencesDataStore.sharingPreferences.first()
            preferencesDataStore.updateSharingPreferences(current.copy(defaultQuality = quality))
        }
    }

    private fun setDefaultMaxDimension(dimension: Int) {
        viewModelScope.launch {
            val current = preferencesDataStore.sharingPreferences.first()
            preferencesDataStore.updateSharingPreferences(
                current.copy(maxWidth = dimension, maxHeight = dimension)
            )
        }
    }

    private fun setKeepMetadata(keep: Boolean) {
        viewModelScope.launch {
            val current = preferencesDataStore.sharingPreferences.first()
            preferencesDataStore.updateSharingPreferences(current.copy(keepMetadata = keep))
        }
    }

    private fun calculateCacheSize() {
        viewModelScope.launch {
            val cacheDir = context.cacheDir
            val size = calculateDirectorySize(cacheDir)
            _uiState.update { it.copy(cacheSize = formatFileSize(size)) }
        }
    }

    private fun calculateDirectorySize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
            bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun showClearCacheDialog() {
        _uiState.update { it.copy(showClearCacheDialog = true) }
    }

    private fun dismissDialog() {
        _uiState.update { it.copy(showClearCacheDialog = false) }
    }

    private fun confirmClearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(showClearCacheDialog = false) }

            try {
                clearCacheDirectory(context.cacheDir)
                calculateCacheSize()
                _effects.send(SettingsEffect.ShowSnackbar("Cache cleared successfully"))
            } catch (e: Exception) {
                _effects.send(SettingsEffect.ShowSnackbar("Failed to clear cache"))
            }
        }
    }

    private fun clearCacheDirectory(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                clearCacheDirectory(file)
            }
            file.delete()
        }
    }

    private fun exportData() {
        viewModelScope.launch {
            try {
                val exportDir = File(context.getExternalFilesDir(null), "exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                
                val timestamp = System.currentTimeMillis()
                val exportFile = File(exportDir, "meme_my_mood_backup_$timestamp.json")
                
                // Export preferences
                val sharingPrefs = preferencesDataStore.sharingPreferences.first()
                val appPrefs = preferencesDataStore.appPreferences.first()
                
                val exportData = mapOf(
                    "version" to 1,
                    "timestamp" to timestamp,
                    "sharingPreferences" to mapOf(
                        "defaultFormat" to sharingPrefs.defaultFormat.name,
                        "defaultQuality" to sharingPrefs.defaultQuality,
                        "maxWidth" to sharingPrefs.maxWidth,
                        "maxHeight" to sharingPrefs.maxHeight,
                        "keepMetadata" to sharingPrefs.keepMetadata
                    ),
                    "appPreferences" to mapOf(
                        "darkMode" to appPrefs.darkMode.name,
                        "dynamicColors" to appPrefs.dynamicColors,
                        "enableSemanticSearch" to appPrefs.enableSemanticSearch,
                        "saveSearchHistory" to appPrefs.saveSearchHistory
                    )
                )
                
                exportFile.writeText(kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.json.JsonObject.serializer(),
                    kotlinx.serialization.json.JsonObject(exportData.mapValues { (_, value) ->
                        when (value) {
                            is Int -> kotlinx.serialization.json.JsonPrimitive(value)
                            is Long -> kotlinx.serialization.json.JsonPrimitive(value)
                            is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
                            is String -> kotlinx.serialization.json.JsonPrimitive(value)
                            is Map<*, *> -> kotlinx.serialization.json.JsonObject(
                                (value as Map<String, Any>).mapValues { (_, v) ->
                                    when (v) {
                                        is Int -> kotlinx.serialization.json.JsonPrimitive(v)
                                        is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
                                        is String -> kotlinx.serialization.json.JsonPrimitive(v)
                                        else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
                                    }
                                }
                            )
                            else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
                        }
                    })
                ))
                
                _effects.send(SettingsEffect.ExportComplete(exportFile.absolutePath))
                _effects.send(SettingsEffect.ShowSnackbar("Data exported to ${exportFile.name}"))
            } catch (e: Exception) {
                _effects.send(SettingsEffect.ShowSnackbar("Export failed: ${e.message}"))
            }
        }
    }

    private fun importData() {
        viewModelScope.launch {
            try {
                // Trigger file picker for import
                _effects.send(SettingsEffect.LaunchImportPicker)
            } catch (e: Exception) {
                _effects.send(SettingsEffect.ShowSnackbar("Import failed: ${e.message}"))
            }
        }
    }

    fun importFromFile(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot open file")
                
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val jsonElement = Json.parseToJsonElement(jsonString)
                
                if (jsonElement is JsonObject) {
                    val sharingPrefsJson = jsonElement["sharingPreferences"] as? JsonObject
                    val appPrefsJson = jsonElement["appPreferences"] as? JsonObject
                    
                    // Import sharing preferences
                    if (sharingPrefsJson != null) {
                        val currentSharing = preferencesDataStore.sharingPreferences.first()
                        val formatName = (sharingPrefsJson["defaultFormat"] as? JsonPrimitive)?.contentOrNull
                        val format = formatName?.let { 
                            try { ImageFormat.valueOf(it) } catch (_: Exception) { null }
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
                                    ?: currentSharing.keepMetadata
                            )
                        )
                    }
                    
                    // Import app preferences
                    if (appPrefsJson != null) {
                        val currentApp = preferencesDataStore.appPreferences.first()
                        val darkModeName = (appPrefsJson["darkMode"] as? JsonPrimitive)?.contentOrNull
                        val darkMode = darkModeName?.let {
                            try { DarkMode.valueOf(it) } catch (_: Exception) { null }
                        } ?: currentApp.darkMode
                        
                        preferencesDataStore.updateAppPreferences(
                            currentApp.copy(
                                darkMode = darkMode,
                                dynamicColors = (appPrefsJson["dynamicColors"] as? JsonPrimitive)?.booleanOrNull
                                    ?: currentApp.dynamicColors,
                                enableSemanticSearch = (appPrefsJson["enableSemanticSearch"] as? JsonPrimitive)?.booleanOrNull
                                    ?: currentApp.enableSemanticSearch,
                                saveSearchHistory = (appPrefsJson["saveSearchHistory"] as? JsonPrimitive)?.booleanOrNull
                                    ?: currentApp.saveSearchHistory
                            )
                        )
                    }
                    
                    _effects.send(SettingsEffect.ShowSnackbar("Settings imported successfully"))
                } else {
                    _effects.send(SettingsEffect.ShowSnackbar("Invalid backup file format"))
                }
            } catch (e: Exception) {
                _effects.send(SettingsEffect.ShowSnackbar("Import failed: ${e.message}"))
            }
        }
    }

    private fun openLicenses() {
        viewModelScope.launch {
            _effects.send(SettingsEffect.NavigateToLicenses)
        }
    }

    private fun openPrivacyPolicy() {
        viewModelScope.launch {
            _effects.send(SettingsEffect.OpenUrl("https://meme-my-mood.app/privacy"))
        }
    }
}
