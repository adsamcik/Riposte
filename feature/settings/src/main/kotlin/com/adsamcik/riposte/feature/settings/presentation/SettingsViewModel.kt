package com.adsamcik.riposte.feature.settings.presentation

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.riposte.core.common.crash.CrashLogManager
import com.adsamcik.riposte.core.common.di.IoDispatcher
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.UserDensityPreference
import com.adsamcik.riposte.feature.settings.R
import com.adsamcik.riposte.feature.settings.domain.usecase.ExportPreferencesUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.GetAppPreferencesUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.GetFunStatisticsUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.GetMilestonesUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.GetSharingPreferencesUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.ImportPreferencesUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.ObserveEmbeddingStatisticsUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.ObserveLibraryStatsUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.SetDarkModeUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.SetDefaultFormatUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.SetDefaultMaxDimensionUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.SetDefaultQualityUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.SetDynamicColorsUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.SetEnableSemanticSearchUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.SetGridDensityUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.SetSaveSearchHistoryUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.SetSortEmojisByUsageUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.SetStripMetadataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import timber.log.Timber
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val getAppPreferencesUseCase: GetAppPreferencesUseCase,
        private val getSharingPreferencesUseCase: GetSharingPreferencesUseCase,
        private val getFunStatisticsUseCase: GetFunStatisticsUseCase,
        private val getMilestonesUseCase: GetMilestonesUseCase,
        private val setDarkModeUseCase: SetDarkModeUseCase,
        private val setDynamicColorsUseCase: SetDynamicColorsUseCase,
        private val setEnableSemanticSearchUseCase: SetEnableSemanticSearchUseCase,
        private val setSaveSearchHistoryUseCase: SetSaveSearchHistoryUseCase,
        private val setSortEmojisByUsageUseCase: SetSortEmojisByUsageUseCase,
        private val setDefaultFormatUseCase: SetDefaultFormatUseCase,
        private val setDefaultQualityUseCase: SetDefaultQualityUseCase,
        private val setDefaultMaxDimensionUseCase: SetDefaultMaxDimensionUseCase,
        private val setStripMetadataUseCase: SetStripMetadataUseCase,
        private val setGridDensityUseCase: SetGridDensityUseCase,
        private val exportPreferencesUseCase: ExportPreferencesUseCase,
        private val importPreferencesUseCase: ImportPreferencesUseCase,
        private val observeEmbeddingStatisticsUseCase: ObserveEmbeddingStatisticsUseCase,
        private val observeLibraryStatsUseCase: ObserveLibraryStatsUseCase,
        private val crashLogManager: CrashLogManager,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
        val effects = _effects.receiveAsFlow()

        private val supportedLanguages =
            listOf(
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
            observeEmbeddingStatistics()
            observeLibraryStats()
            refreshCrashLogCount()
            loadFunStatistics()
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

        private fun observeEmbeddingStatistics() {
            viewModelScope.launch {
                observeEmbeddingStatisticsUseCase()
                    .collect { statusInfo ->
                        val stats = statusInfo.statistics
                        val modelInfo = statusInfo.modelInfo
                        _uiState.update {
                            it.copy(
                                embeddingSearchState =
                                    EmbeddingSearchState(
                                        modelName = modelInfo.name,
                                        modelVersion = modelInfo.version.substringAfter(":"),
                                        dimension = modelInfo.dimension,
                                        indexedCount = stats.validEmbeddingCount,
                                        totalCount =
                                            stats.validEmbeddingCount +
                                                stats.pendingEmbeddingCount +
                                                stats.regenerationNeededCount,
                                        pendingCount = stats.pendingEmbeddingCount,
                                        regenerationCount = stats.regenerationNeededCount,
                                        modelError = stats.modelError,
                                    ),
                            )
                        }
                    }
            }
        }

        private fun observeLibraryStats() {
            viewModelScope.launch {
                observeLibraryStatsUseCase()
                    .collect { stats ->
                        _uiState.update {
                            it.copy(
                                totalMemeCount = stats.totalMemes,
                                favoriteMemeCount = stats.favoriteMemes,
                            )
                        }
                    }
            }
        }

        private fun loadFunStatistics() {
            viewModelScope.launch {
                try {
                    val stats = withContext(ioDispatcher) { getFunStatisticsUseCase() }
                    val milestones = getMilestonesUseCase(stats)
                    val weeklyData = computeWeeklyData(stats)
                    val trend = computeMomentumTrend(weeklyData)

                    _uiState.update {
                        it.copy(
                            collectionTitle = computeCollectionTitle(context, stats.totalMemes),
                            totalStorageBytes = stats.totalStorageBytes,
                            storageFunFact = computeStorageFunFact(stats.totalStorageBytes),
                            topVibes = stats.topEmojis,
                            vibeTagline = computeVibeTagline(stats.topEmojis),
                            funFactOfTheDay = computeFunFactOfTheDay(context, stats),
                            weeklyImportCounts = weeklyData,
                            momentumTrend = trend,
                            memesThisWeek = weeklyData.lastOrNull() ?: 0,
                            milestones = milestones,
                            unlockedMilestoneCount = milestones.count { m -> m.isUnlocked },
                            totalMilestoneCount = milestones.size,
                        )
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load fun statistics")
                }
            }
        }

        private fun loadSettings() {
            viewModelScope.launch {
                combine(
                    getAppPreferencesUseCase(),
                    getSharingPreferencesUseCase(),
                ) { appPrefs, sharingPrefs ->
                    _uiState.value.copy(
                        darkMode = appPrefs.darkMode,
                        dynamicColorsEnabled = appPrefs.dynamicColors,
                        gridDensityPreference = appPrefs.userDensityPreference,
                        defaultFormat = sharingPrefs.defaultFormat,
                        defaultQuality = sharingPrefs.defaultQuality,
                        defaultMaxDimension = sharingPrefs.maxWidth,
                        stripMetadata = sharingPrefs.stripMetadata,
                        enableSemanticSearch = appPrefs.enableSemanticSearch,
                        saveSearchHistory = appPrefs.saveSearchHistory,
                        sortEmojisByUsage = appPrefs.sortEmojisByUsage,
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
                Timber.w(e, "Failed to get app version")
                "1.0.0"
            }
        }

        fun onIntent(intent: SettingsIntent) {
            when (intent) {
                // Appearance
                is SettingsIntent.SetDarkMode -> setDarkMode(intent.mode)
                is SettingsIntent.SetLanguage -> setLanguage(intent.languageCode)
                is SettingsIntent.SetDynamicColors -> setDynamicColors(intent.enabled)

                // Display
                is SettingsIntent.SetGridDensity -> setGridDensity(intent.preference)

                // Sharing
                is SettingsIntent.SetDefaultFormat -> setDefaultFormat(intent.format)
                is SettingsIntent.SetDefaultQuality -> setDefaultQuality(intent.quality)
                is SettingsIntent.SetDefaultMaxDimension -> setDefaultMaxDimension(intent.dimension)
                is SettingsIntent.SetStripMetadata -> setStripMetadata(intent.strip)

                // Search
                is SettingsIntent.SetEnableSemanticSearch -> setEnableSemanticSearch(intent.enabled)
                is SettingsIntent.SetSaveSearchHistory -> setSaveSearchHistory(intent.save)
                is SettingsIntent.SetSortEmojisByUsage -> setSortEmojisByUsage(intent.enabled)

                // Storage
                is SettingsIntent.CalculateCacheSize -> calculateCacheSize()
                is SettingsIntent.ShowClearCacheDialog -> showClearCacheDialog()
                is SettingsIntent.DismissDialog -> dismissDialog()
                is SettingsIntent.ConfirmClearCache -> confirmClearCache()

                // Export
                is SettingsIntent.ShowExportOptionsDialog -> showExportOptionsDialog()
                is SettingsIntent.DismissExportOptionsDialog -> dismissExportOptionsDialog()
                is SettingsIntent.SetExportSettings -> _uiState.update { it.copy(exportSettings = intent.include) }
                is SettingsIntent.SetExportImages -> _uiState.update { it.copy(exportImages = intent.include) }
                is SettingsIntent.SetExportTags -> _uiState.update { it.copy(exportTags = intent.include) }
                is SettingsIntent.ConfirmExport -> confirmExport()
                is SettingsIntent.ExportToUri -> exportToUri(intent.uri)

                // Import
                is SettingsIntent.ImportData -> importData()
                is SettingsIntent.ImportFromUri -> importFromUri(intent.uri)
                is SettingsIntent.ConfirmImport -> confirmImport()
                is SettingsIntent.DismissImportConfirmDialog -> dismissImportConfirmDialog()

                // About
                is SettingsIntent.OpenLicenses -> openLicenses()
                is SettingsIntent.OpenPrivacyPolicy -> openPrivacyPolicy()

                // Crash Logs
                is SettingsIntent.ShareCrashLogs -> shareCrashLogs()
                is SettingsIntent.ClearCrashLogs -> clearCrashLogs()
            }
        }

        private fun setDarkMode(mode: DarkMode) {
            viewModelScope.launch {
                setDarkModeUseCase(mode)
            }
        }

        private fun setDynamicColors(enabled: Boolean) {
            viewModelScope.launch {
                setDynamicColorsUseCase(enabled)
            }
        }

        private fun setGridDensity(preference: UserDensityPreference) {
            viewModelScope.launch {
                setGridDensityUseCase(preference)
            }
        }

        private fun setLanguage(languageCode: String?) {
            val localeList =
                if (languageCode == null) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(languageCode)
                }
            AppCompatDelegate.setApplicationLocales(localeList)
            _uiState.update { it.copy(currentLanguage = languageCode) }
        }

        private fun setEnableSemanticSearch(enabled: Boolean) {
            viewModelScope.launch {
                setEnableSemanticSearchUseCase(enabled)
            }
        }

        private fun setSaveSearchHistory(save: Boolean) {
            viewModelScope.launch {
                setSaveSearchHistoryUseCase(save)
            }
        }

        private fun setSortEmojisByUsage(enabled: Boolean) {
            viewModelScope.launch {
                setSortEmojisByUsageUseCase(enabled)
            }
        }

        private fun setDefaultFormat(format: ImageFormat) {
            viewModelScope.launch {
                setDefaultFormatUseCase(format)
            }
        }

        private fun setDefaultQuality(quality: Int) {
            viewModelScope.launch {
                setDefaultQualityUseCase(quality)
            }
        }

        private fun setDefaultMaxDimension(dimension: Int) {
            viewModelScope.launch {
                setDefaultMaxDimensionUseCase(dimension)
            }
        }

        private fun setStripMetadata(strip: Boolean) {
            viewModelScope.launch {
                setStripMetadataUseCase(strip)
            }
        }

        private fun calculateCacheSize() {
            viewModelScope.launch {
                val cacheDir = context.cacheDir
                val size =
                    withContext(ioDispatcher) {
                        calculateDirectorySize(cacheDir)
                    }
                _uiState.update { it.copy(cacheSize = formatFileSize(size)) }
            }
        }

        internal fun calculateDirectorySize(dir: File): Long {
            var size = 0L
            dir.listFiles()?.forEach { file ->
                size +=
                    if (file.isDirectory) {
                        calculateDirectorySize(file)
                    } else {
                        file.length()
                    }
            }
            return size
        }

        internal fun formatFileSize(bytes: Long): String {
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
                    withContext(ioDispatcher) {
                        clearCacheDirectory(context.cacheDir)
                    }
                    calculateCacheSize()
                    _effects.send(
                        SettingsEffect.ShowSnackbar(context.getString(R.string.settings_snackbar_cache_cleared)),
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to clear cache")
                    _effects.send(
                        SettingsEffect.ShowSnackbar(context.getString(R.string.settings_snackbar_cache_clear_failed)),
                    )
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

        // region Export

        private fun showExportOptionsDialog() {
            _uiState.update {
                it.copy(
                    showExportOptionsDialog = true,
                    exportSettings = true,
                    exportImages = true,
                    exportTags = true,
                )
            }
        }

        private fun dismissExportOptionsDialog() {
            _uiState.update { it.copy(showExportOptionsDialog = false) }
        }

        private fun confirmExport() {
            _uiState.update { it.copy(showExportOptionsDialog = false) }
            viewModelScope.launch {
                _effects.send(SettingsEffect.LaunchExportPicker)
            }
        }

        private fun exportToUri(uri: Uri) {
            viewModelScope.launch {
                try {
                    val outputStream =
                        context.contentResolver.openOutputStream(uri)
                            ?: throw Exception("Cannot open output stream")

                    ZipOutputStream(outputStream.buffered()).use { zip ->
                        if (_uiState.value.exportSettings) {
                            val settingsJson = exportPreferencesUseCase()
                            zip.putNextEntry(ZipEntry("settings.json"))
                            zip.write(settingsJson.toByteArray())
                            zip.closeEntry()
                        }
                        // TODO: Export meme images when MemeRepository is available in settings module
                        // TODO: Export tags/metadata when MemeRepository is available in settings module
                    }

                    _effects.send(
                        SettingsEffect.ShowSnackbar(context.getString(R.string.settings_snackbar_export_success, "")),
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to export settings")
                    _effects.send(
                        SettingsEffect.ShowSnackbar(
                            context.getString(R.string.settings_snackbar_export_failed, e.message ?: ""),
                        ),
                    )
                }
            }
        }

        // endregion

        // region Import

        private fun importData() {
            viewModelScope.launch {
                _effects.send(SettingsEffect.LaunchImportPicker)
            }
        }

        private fun importFromUri(uri: Uri) {
            viewModelScope.launch {
                try {
                    val inputStream =
                        context.contentResolver.openInputStream(uri)
                            ?: throw Exception("Cannot open file")

                    val bytes = inputStream.buffered().use { it.readBytes() }

                    // Try ZIP first, then fall back to plain JSON
                    val jsonString = tryReadZipSettings(bytes) ?: String(bytes)

                    // Parse to get timestamp for confirmation dialog
                    val jsonElement = Json.parseToJsonElement(jsonString)
                    val timestamp =
                        if (jsonElement is JsonObject) {
                            (jsonElement["timestamp"] as? JsonPrimitive)?.longOrNull
                        } else {
                            null
                        }

                    _uiState.update {
                        it.copy(
                            showImportConfirmDialog = true,
                            pendingImportJson = jsonString,
                            importBackupTimestamp = timestamp,
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to import settings from URI")
                    _effects.send(
                        SettingsEffect.ShowSnackbar(
                            context.getString(R.string.settings_snackbar_import_failed, e.message ?: ""),
                        ),
                    )
                }
            }
        }

        private fun tryReadZipSettings(bytes: ByteArray): String? {
            return try {
                ZipInputStream(bytes.inputStream()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "settings.json") {
                            return zip.bufferedReader().readText()
                        }
                        entry = zip.nextEntry
                    }
                    null
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to read settings from ZIP")
                null
            }
        }

        private fun confirmImport() {
            viewModelScope.launch {
                val json = _uiState.value.pendingImportJson ?: return@launch
                _uiState.update {
                    it.copy(showImportConfirmDialog = false, pendingImportJson = null, importBackupTimestamp = null)
                }

                val result = importPreferencesUseCase(json)
                if (result.isSuccess) {
                    _effects.send(
                        SettingsEffect.ShowSnackbar(context.getString(R.string.settings_snackbar_import_success)),
                    )
                } else {
                    Timber.w(result.exceptionOrNull(), "Settings import failed")
                    _effects.send(
                        SettingsEffect.ShowSnackbar(
                            context.getString(
                                R.string.settings_snackbar_import_failed,
                                result.exceptionOrNull()?.message ?: "",
                            ),
                        ),
                    )
                }
            }
        }

        private fun dismissImportConfirmDialog() {
            _uiState.update {
                it.copy(showImportConfirmDialog = false, pendingImportJson = null, importBackupTimestamp = null)
            }
        }

        // endregion

        // region Crash Logs

        private fun refreshCrashLogCount() {
            viewModelScope.launch {
                val count = withContext(ioDispatcher) { crashLogManager.getCrashLogCount() }
                _uiState.update { it.copy(crashLogCount = count) }
            }
        }

        private fun shareCrashLogs() {
            viewModelScope.launch {
                val report = withContext(ioDispatcher) { crashLogManager.getShareableReport() }
                _uiState.update { it.copy(crashLogCount = report.count) }
                if (report.text.isNotEmpty()) {
                    _effects.send(
                        SettingsEffect.ShareText(
                            text = report.text,
                            title = context.getString(R.string.settings_crash_share_title),
                        ),
                    )
                }
            }
        }

        private fun clearCrashLogs() {
            viewModelScope.launch {
                withContext(ioDispatcher) { crashLogManager.clearAll() }
                val count = withContext(ioDispatcher) { crashLogManager.getCrashLogCount() }
                _uiState.update { it.copy(crashLogCount = count) }
                _effects.send(
                    SettingsEffect.ShowSnackbar(context.getString(R.string.settings_crash_cleared)),
                )
            }
        }

        // endregion

        private fun openLicenses() {
            viewModelScope.launch {
                _effects.send(SettingsEffect.NavigateToLicenses)
            }
        }

        private fun openPrivacyPolicy() {
            viewModelScope.launch {
                _effects.send(SettingsEffect.OpenUrl("https://riposte.app/privacy"))
            }
        }
    }
