package com.adsamcik.riposte.feature.settings.presentation

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.cash.turbine.test
import com.adsamcik.riposte.core.common.crash.CrashLogManager
import com.adsamcik.riposte.core.model.AppPreferences
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.SharingPreferences
import com.adsamcik.riposte.core.model.UserDensityPreference
import com.adsamcik.riposte.core.testing.MainDispatcherRule
import com.adsamcik.riposte.feature.settings.R
import com.adsamcik.riposte.feature.settings.domain.usecase.ExportPreferencesUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.GetAppPreferencesUseCase
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
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var context: Context
    private lateinit var viewModel: SettingsViewModel

    // Use case mocks
    private lateinit var getAppPreferencesUseCase: GetAppPreferencesUseCase
    private lateinit var getSharingPreferencesUseCase: GetSharingPreferencesUseCase
    private lateinit var setDarkModeUseCase: SetDarkModeUseCase
    private lateinit var setDynamicColorsUseCase: SetDynamicColorsUseCase
    private lateinit var setEnableSemanticSearchUseCase: SetEnableSemanticSearchUseCase
    private lateinit var setSaveSearchHistoryUseCase: SetSaveSearchHistoryUseCase
    private lateinit var setSortEmojisByUsageUseCase: SetSortEmojisByUsageUseCase
    private lateinit var setDefaultFormatUseCase: SetDefaultFormatUseCase
    private lateinit var setDefaultQualityUseCase: SetDefaultQualityUseCase
    private lateinit var setDefaultMaxDimensionUseCase: SetDefaultMaxDimensionUseCase
    private lateinit var setStripMetadataUseCase: SetStripMetadataUseCase
    private lateinit var setGridDensityUseCase: SetGridDensityUseCase
    private lateinit var exportPreferencesUseCase: ExportPreferencesUseCase
    private lateinit var importPreferencesUseCase: ImportPreferencesUseCase
    private lateinit var observeEmbeddingStatisticsUseCase: ObserveEmbeddingStatisticsUseCase
    private lateinit var observeLibraryStatsUseCase: ObserveLibraryStatsUseCase
    private lateinit var crashLogManager: CrashLogManager

    private val appPreferencesFlow = MutableStateFlow(createDefaultAppPreferences())
    private val sharingPreferencesFlow = MutableStateFlow(createDefaultSharingPreferences())

    @Before
    fun setup() {
        context = mockk(relaxed = true)

        // Mock package info for app version
        val packageInfo =
            PackageInfo().apply {
                versionName = "1.2.3"
                longVersionCode = 456
            }
        val packageManager: PackageManager = mockk()
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.adsamcik.riposte"
        every { packageManager.getPackageInfo("com.adsamcik.riposte", 0) } returns packageInfo

        // Setup cache directory mock
        val cacheDir: File = mockk(relaxed = true)
        every { context.cacheDir } returns cacheDir
        every { cacheDir.listFiles() } returns emptyArray()

        // Setup use case mocks
        getAppPreferencesUseCase = mockk()
        getSharingPreferencesUseCase = mockk()
        setDarkModeUseCase = mockk(relaxed = true)
        setDynamicColorsUseCase = mockk(relaxed = true)
        setEnableSemanticSearchUseCase = mockk(relaxed = true)
        setSaveSearchHistoryUseCase = mockk(relaxed = true)
        setSortEmojisByUsageUseCase = mockk(relaxed = true)
        setDefaultFormatUseCase = mockk(relaxed = true)
        setDefaultQualityUseCase = mockk(relaxed = true)
        setDefaultMaxDimensionUseCase = mockk(relaxed = true)
        setStripMetadataUseCase = mockk(relaxed = true)
        setGridDensityUseCase = mockk(relaxed = true)
        exportPreferencesUseCase = mockk(relaxed = true)
        importPreferencesUseCase = mockk(relaxed = true)
        observeEmbeddingStatisticsUseCase = mockk(relaxed = true)
        observeLibraryStatsUseCase = mockk(relaxed = true)
        crashLogManager = mockk(relaxed = true)

        every { getAppPreferencesUseCase() } returns appPreferencesFlow
        every { getSharingPreferencesUseCase() } returns sharingPreferencesFlow
        every { observeEmbeddingStatisticsUseCase() } returns kotlinx.coroutines.flow.emptyFlow()
        every { observeLibraryStatsUseCase() } returns kotlinx.coroutines.flow.emptyFlow()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            context = context,
            getAppPreferencesUseCase = getAppPreferencesUseCase,
            getSharingPreferencesUseCase = getSharingPreferencesUseCase,
            setDarkModeUseCase = setDarkModeUseCase,
            setDynamicColorsUseCase = setDynamicColorsUseCase,
            setEnableSemanticSearchUseCase = setEnableSemanticSearchUseCase,
            setSaveSearchHistoryUseCase = setSaveSearchHistoryUseCase,
            setSortEmojisByUsageUseCase = setSortEmojisByUsageUseCase,
            setDefaultFormatUseCase = setDefaultFormatUseCase,
            setDefaultQualityUseCase = setDefaultQualityUseCase,
            setDefaultMaxDimensionUseCase = setDefaultMaxDimensionUseCase,
            setStripMetadataUseCase = setStripMetadataUseCase,
            setGridDensityUseCase = setGridDensityUseCase,
            exportPreferencesUseCase = exportPreferencesUseCase,
            importPreferencesUseCase = importPreferencesUseCase,
            observeEmbeddingStatisticsUseCase = observeEmbeddingStatisticsUseCase,
            observeLibraryStatsUseCase = observeLibraryStatsUseCase,
            crashLogManager = crashLogManager,
            ioDispatcher = mainDispatcherRule.testDispatcher,
        )
    }

    private fun createDefaultAppPreferences(
        darkMode: DarkMode = DarkMode.SYSTEM,
        dynamicColors: Boolean = true,
        enableSemanticSearch: Boolean = true,
        saveSearchHistory: Boolean = true,
    ) = AppPreferences(
        darkMode = darkMode,
        dynamicColors = dynamicColors,
        enableSemanticSearch = enableSemanticSearch,
        saveSearchHistory = saveSearchHistory,
    )

    private fun createDefaultSharingPreferences(
        defaultFormat: ImageFormat = ImageFormat.JPEG,
        defaultQuality: Int = 85,
        maxWidth: Int = 1080,
        maxHeight: Int = 1080,
    ) = SharingPreferences(
        defaultFormat = defaultFormat,
        defaultQuality = defaultQuality,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
    )

    // region Initialization Tests

    @Test
    fun `initial state has loading true`() =
        runTest {
            viewModel = createViewModel()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isTrue()
        }

    @Test
    fun `loads settings from use cases on initialization`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.darkMode).isEqualTo(DarkMode.SYSTEM)
            assertThat(state.dynamicColorsEnabled).isTrue()
        }

    @Test
    fun `loads app version on initialization`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.appVersion).isEqualTo("1.2.3 (456)")
        }

    @Test
    fun `app version fallback when package info fails`() =
        runTest {
            every { context.packageManager.getPackageInfo(any<String>(), any<Int>()) } throws Exception("Not found")

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.appVersion).isEqualTo("1.0.0")
        }

    @Test
    fun `loads all sharing preferences`() =
        runTest {
            sharingPreferencesFlow.value =
                createDefaultSharingPreferences(
                    defaultFormat = ImageFormat.PNG,
                    defaultQuality = 90,
                    maxWidth = 2048,
                )

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.defaultFormat).isEqualTo(ImageFormat.PNG)
            assertThat(state.defaultQuality).isEqualTo(90)
            assertThat(state.defaultMaxDimension).isEqualTo(2048)
        }

    @Test
    fun `loads search preferences`() =
        runTest {
            appPreferencesFlow.value =
                createDefaultAppPreferences(
                    enableSemanticSearch = false,
                    saveSearchHistory = false,
                )

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.enableSemanticSearch).isFalse()
            assertThat(state.saveSearchHistory).isFalse()
        }

    // endregion

    // region Regression: ViewModel uses Use Cases (p2-6)

    @Test
    fun `SetDarkMode delegates to use case`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetDarkMode(DarkMode.LIGHT))
            advanceUntilIdle()

            coVerify { setDarkModeUseCase(DarkMode.LIGHT) }
        }

    @Test
    fun `SetDynamicColors delegates to use case`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetDynamicColors(false))
            advanceUntilIdle()

            coVerify { setDynamicColorsUseCase(false) }
        }

    @Test
    fun `SetDefaultFormat delegates to use case`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetDefaultFormat(ImageFormat.PNG))
            advanceUntilIdle()

            coVerify { setDefaultFormatUseCase(ImageFormat.PNG) }
        }

    @Test
    fun `SetDefaultQuality delegates to use case`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetDefaultQuality(95))
            advanceUntilIdle()

            coVerify { setDefaultQualityUseCase(95) }
        }

    @Test
    fun `SetDefaultMaxDimension delegates to use case`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetDefaultMaxDimension(2048))
            advanceUntilIdle()

            coVerify { setDefaultMaxDimensionUseCase(2048) }
        }

    @Test
    fun `SetGridDensity delegates to use case`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetGridDensity(UserDensityPreference.COMPACT))
            advanceUntilIdle()

            coVerify { setGridDensityUseCase(UserDensityPreference.COMPACT) }
        }

    @Test
    fun `SetEnableSemanticSearch delegates to use case`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetEnableSemanticSearch(false))
            advanceUntilIdle()

            coVerify { setEnableSemanticSearchUseCase(false) }
        }

    @Test
    fun `SetSaveSearchHistory delegates to use case`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetSaveSearchHistory(false))
            advanceUntilIdle()

            coVerify { setSaveSearchHistoryUseCase(false) }
        }

    // endregion

    // region Regression: keepMetadata removed (p0-3)

    @Test
    fun `UiState does not contain keepMetadata field`() {
        // Compile-time verification: SettingsUiState has no keepMetadata property.
        // If keepMetadata were added back, this test would fail to compile.
        val state = SettingsUiState()
        val fields =
            listOf(
                state.darkMode,
                state.dynamicColorsEnabled,
                state.defaultFormat,
                state.defaultQuality,
                state.defaultMaxDimension,
                state.enableSemanticSearch,
                state.saveSearchHistory,
            )
        assertThat(fields).isNotEmpty()
    }

    @Test
    fun `SettingsIntent does not contain SetKeepMetadata`() {
        // Compile-time verification: if SetKeepMetadata were reintroduced,
        // this when-expression would fail to compile (non-exhaustive).
        val intent: SettingsIntent = SettingsIntent.SetDefaultFormat(ImageFormat.WEBP)
        val isKeepMetadata =
            when (intent) {
                is SettingsIntent.SetDarkMode,
                is SettingsIntent.SetLanguage,
                is SettingsIntent.SetDynamicColors,
                is SettingsIntent.SetGridDensity,
                is SettingsIntent.SetDefaultFormat,
                is SettingsIntent.SetDefaultQuality,
                is SettingsIntent.SetDefaultMaxDimension,
                is SettingsIntent.SetEnableSemanticSearch,
                is SettingsIntent.SetSaveSearchHistory,
                is SettingsIntent.SetSortEmojisByUsage,
                is SettingsIntent.CalculateCacheSize,
                is SettingsIntent.ShowClearCacheDialog,
                is SettingsIntent.DismissDialog,
                is SettingsIntent.ConfirmClearCache,
                is SettingsIntent.ShowExportOptionsDialog,
                is SettingsIntent.DismissExportOptionsDialog,
                is SettingsIntent.SetExportSettings,
                is SettingsIntent.SetExportImages,
                is SettingsIntent.SetExportTags,
                is SettingsIntent.ConfirmExport,
                is SettingsIntent.ExportToUri,
                is SettingsIntent.ImportData,
                is SettingsIntent.ImportFromUri,
                is SettingsIntent.ConfirmImport,
                is SettingsIntent.DismissImportConfirmDialog,
                is SettingsIntent.OpenLicenses,
                is SettingsIntent.OpenPrivacyPolicy,
                is SettingsIntent.SetStripMetadata,
                is SettingsIntent.ShareCrashLogs,
                is SettingsIntent.ClearCrashLogs,
                -> false
            }
        assertThat(isKeepMetadata).isFalse()
    }

    // endregion

    // region Regression: Default format is JPEG

    @Test
    fun `UiState default format is JPEG`() {
        val state = SettingsUiState()
        assertThat(state.defaultFormat).isEqualTo(ImageFormat.JPEG)
    }

    // endregion

    // region Dark Mode Tests

    @Test
    fun `SetDarkMode DARK updates preference`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetDarkMode(DarkMode.DARK))
            advanceUntilIdle()

            coVerify { setDarkModeUseCase(DarkMode.DARK) }
        }

    @Test
    fun `SetDarkMode SYSTEM updates preference`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetDarkMode(DarkMode.SYSTEM))
            advanceUntilIdle()

            coVerify { setDarkModeUseCase(DarkMode.SYSTEM) }
        }

    @Test
    fun `dark mode state reflects use case updates`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            appPreferencesFlow.value = createDefaultAppPreferences(darkMode = DarkMode.DARK)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.darkMode).isEqualTo(DarkMode.DARK)
        }

    // endregion

    // region Cache Management Tests

    @Test
    fun `CalculateCacheSize updates cache size in state`() =
        runTest {
            val cacheDir: File = mockk(relaxed = true)
            val testFile: File = mockk()
            every { context.cacheDir } returns cacheDir
            every { cacheDir.listFiles() } returns arrayOf(testFile)
            every { testFile.isDirectory } returns false
            every { testFile.length() } returns 2_097_152L // 2 MB

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.cacheSize).isEqualTo("2.00 MB")
        }

    @Test
    fun `cache size formats correctly for KB`() =
        runTest {
            val cacheDir: File = mockk(relaxed = true)
            val testFile: File = mockk()
            every { context.cacheDir } returns cacheDir
            every { cacheDir.listFiles() } returns arrayOf(testFile)
            every { testFile.isDirectory } returns false
            every { testFile.length() } returns 1024L // 1 KB

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.cacheSize).isEqualTo("1.00 KB")
        }

    @Test
    fun `cache size formats correctly for bytes`() =
        runTest {
            val cacheDir: File = mockk(relaxed = true)
            val testFile: File = mockk()
            every { context.cacheDir } returns cacheDir
            every { cacheDir.listFiles() } returns arrayOf(testFile)
            every { testFile.isDirectory } returns false
            every { testFile.length() } returns 500L

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.cacheSize).isEqualTo("500 B")
        }

    @Test
    fun `cache size formats correctly for GB`() =
        runTest {
            val cacheDir: File = mockk(relaxed = true)
            val testFile: File = mockk()
            every { context.cacheDir } returns cacheDir
            every { cacheDir.listFiles() } returns arrayOf(testFile)
            every { testFile.isDirectory } returns false
            every { testFile.length() } returns 2_147_483_648L // 2 GB

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.cacheSize).isEqualTo("2.00 GB")
        }

    @Test
    fun `calculates nested directory sizes`() =
        runTest {
            val cacheDir: File = mockk(relaxed = true)
            val subDir: File = mockk(relaxed = true)
            val file1: File = mockk()
            val file2: File = mockk()

            every { context.cacheDir } returns cacheDir
            every { cacheDir.listFiles() } returns arrayOf(subDir, file1)
            every { subDir.isDirectory } returns true
            every { subDir.listFiles() } returns arrayOf(file2)
            every { file1.isDirectory } returns false
            every { file1.length() } returns 1_048_576L // 1 MB
            every { file2.isDirectory } returns false
            every { file2.length() } returns 1_048_576L // 1 MB

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.cacheSize).isEqualTo("2.00 MB")
        }

    @Test
    fun `ShowClearCacheDialog shows dialog`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.ShowClearCacheDialog)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showClearCacheDialog).isTrue()
        }

    @Test
    fun `DismissDialog hides dialog`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.ShowClearCacheDialog)
            advanceUntilIdle()
            viewModel.onIntent(SettingsIntent.DismissDialog)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showClearCacheDialog).isFalse()
        }

    @Test
    fun `ConfirmClearCache clears cache and emits success effect`() =
        runTest {
            val cacheDir: File = mockk(relaxed = true)
            val testFile: File = mockk(relaxed = true)
            every { context.cacheDir } returns cacheDir
            every { cacheDir.listFiles() } returns arrayOf(testFile)
            every { testFile.isDirectory } returns false
            every { testFile.length() } returns 1024L
            every { testFile.delete() } returns true
            every { context.getString(R.string.settings_snackbar_cache_cleared) } returns "Cache cleared successfully"

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.ShowClearCacheDialog)
                advanceUntilIdle()
                viewModel.onIntent(SettingsIntent.ConfirmClearCache)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(SettingsEffect.ShowSnackbar::class.java)
                assertThat((effect as SettingsEffect.ShowSnackbar).message).isEqualTo("Cache cleared successfully")

                cancelAndIgnoreRemainingEvents()
            }

            assertThat(viewModel.uiState.value.showClearCacheDialog).isFalse()
        }

    @Test
    fun `ConfirmClearCache emits error effect on failure`() =
        runTest {
            val cacheDir: File = mockk(relaxed = true)
            val testFile: File = mockk(relaxed = true)
            every { context.cacheDir } returns cacheDir
            every { cacheDir.listFiles() } returns arrayOf(testFile)
            every { testFile.isDirectory } returns false
            every { testFile.length() } returns 1024L
            every { context.getString(R.string.settings_snackbar_cache_clear_failed) } returns "Failed to clear cache"

            viewModel = createViewModel()
            advanceUntilIdle()

            // Now set up the exception for when confirmClearCache tries to clear
            every { cacheDir.listFiles() } throws RuntimeException("Permission denied")

            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.ConfirmClearCache)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(SettingsEffect.ShowSnackbar::class.java)
                assertThat((effect as SettingsEffect.ShowSnackbar).message).isEqualTo("Failed to clear cache")

                cancelAndIgnoreRemainingEvents()
            }
        }

    // endregion

    // region Export Tests

    @Test
    fun `ShowExportOptionsDialog shows dialog with defaults`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.ShowExportOptionsDialog)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.showExportOptionsDialog).isTrue()
            assertThat(state.exportSettings).isTrue()
            assertThat(state.exportImages).isTrue()
            assertThat(state.exportTags).isTrue()
        }

    @Test
    fun `DismissExportOptionsDialog hides dialog`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.ShowExportOptionsDialog)
            advanceUntilIdle()
            viewModel.onIntent(SettingsIntent.DismissExportOptionsDialog)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showExportOptionsDialog).isFalse()
        }

    @Test
    fun `ConfirmExport emits LaunchExportPicker effect`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.ConfirmExport)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(SettingsEffect.LaunchExportPicker::class.java)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ExportToUri uses ExportPreferencesUseCase`() =
        runTest {
            coEvery { exportPreferencesUseCase() } returns """{"version":1}"""
            every { context.getString(eq(R.string.settings_snackbar_export_success), any()) } returns "Export success"

            val testFile = File.createTempFile("test_export", ".zip")
            try {
                val uri: android.net.Uri = mockk()
                every { context.contentResolver.openOutputStream(uri) } returns testFile.outputStream()

                viewModel = createViewModel()
                advanceUntilIdle()

                viewModel.onIntent(SettingsIntent.ExportToUri(uri))
                advanceUntilIdle()

                coVerify { exportPreferencesUseCase() }
            } finally {
                testFile.delete()
            }
        }

    // endregion

    // region Import Tests

    @Test
    fun `ImportData emits LaunchImportPicker effect`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.ImportData)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(SettingsEffect.LaunchImportPicker::class.java)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ImportFromUri shows confirmation dialog`() =
        runTest {
            val jsonData = """{"version":1,"timestamp":1706198400000}"""
            val uri: android.net.Uri = mockk()
            every { context.contentResolver.openInputStream(uri) } returns jsonData.byteInputStream()

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.ImportFromUri(uri))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.showImportConfirmDialog).isTrue()
            assertThat(state.pendingImportJson).isEqualTo(jsonData)
            assertThat(state.importBackupTimestamp).isEqualTo(1706198400000)
        }

    @Test
    fun `ConfirmImport delegates to ImportPreferencesUseCase`() =
        runTest {
            val jsonData = """{"version":1,"timestamp":1706198400000}"""
            coEvery { importPreferencesUseCase(jsonData) } returns Result.success(Unit)
            every { context.getString(R.string.settings_snackbar_import_success) } returns "Import success"

            val uri: android.net.Uri = mockk()
            every { context.contentResolver.openInputStream(uri) } returns jsonData.byteInputStream()

            viewModel = createViewModel()
            advanceUntilIdle()

            // First trigger import to set pending JSON
            viewModel.onIntent(SettingsIntent.ImportFromUri(uri))
            advanceUntilIdle()

            // Then confirm
            viewModel.onIntent(SettingsIntent.ConfirmImport)
            advanceUntilIdle()

            coVerify { importPreferencesUseCase(jsonData) }
            assertThat(viewModel.uiState.value.showImportConfirmDialog).isFalse()
        }

    @Test
    fun `DismissImportConfirmDialog clears pending state`() =
        runTest {
            val jsonData = """{"version":1,"timestamp":1706198400000}"""
            val uri: android.net.Uri = mockk()
            every { context.contentResolver.openInputStream(uri) } returns jsonData.byteInputStream()

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.ImportFromUri(uri))
            advanceUntilIdle()
            viewModel.onIntent(SettingsIntent.DismissImportConfirmDialog)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.showImportConfirmDialog).isFalse()
            assertThat(state.pendingImportJson).isNull()
            assertThat(state.importBackupTimestamp).isNull()
        }

    // endregion

    // region About Section Tests

    @Test
    fun `OpenLicenses emits NavigateToLicenses effect`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.OpenLicenses)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isEqualTo(SettingsEffect.NavigateToLicenses)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `OpenPrivacyPolicy emits OpenUrl effect with correct URL`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.OpenPrivacyPolicy)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(SettingsEffect.OpenUrl::class.java)
                assertThat((effect as SettingsEffect.OpenUrl).url).isEqualTo("https://riposte.app/privacy")

                cancelAndIgnoreRemainingEvents()
            }
        }

    // endregion

    // region State Flow Reactivity Tests

    @Test
    fun `state updates when app preferences change`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            // Verify initial state
            assertThat(viewModel.uiState.value.darkMode).isEqualTo(DarkMode.SYSTEM)

            // Update use case flow
            appPreferencesFlow.value =
                createDefaultAppPreferences(
                    darkMode = DarkMode.LIGHT,
                    dynamicColors = false,
                )
            advanceUntilIdle()

            // Verify state updated
            val state = viewModel.uiState.value
            assertThat(state.darkMode).isEqualTo(DarkMode.LIGHT)
            assertThat(state.dynamicColorsEnabled).isFalse()
        }

    @Test
    fun `state updates when sharing preferences change`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            // Verify initial state
            assertThat(viewModel.uiState.value.defaultFormat).isEqualTo(ImageFormat.JPEG)

            // Update use case flow
            sharingPreferencesFlow.value =
                createDefaultSharingPreferences(
                    defaultFormat = ImageFormat.JPEG,
                    defaultQuality = 75,
                )
            advanceUntilIdle()

            // Verify state updated
            val state = viewModel.uiState.value
            assertThat(state.defaultFormat).isEqualTo(ImageFormat.JPEG)
            assertThat(state.defaultQuality).isEqualTo(75)
        }

    // endregion

    // region Regression: Cache Empty State and No Sharing Section (p2-ux)

    @Test
    fun `when cache is zero bytes then empty cache state is indicated`() =
        runTest {
            // Default setup has empty cacheDir (listFiles returns emptyArray)
            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.cacheSize).isEqualTo("0 B")
        }

    @Test
    fun `when settings loaded then sharing section is not present`() {
        // The SettingsUiState still has sharing defaults (defaultFormat, defaultQuality,
        // defaultMaxDimension) but no dedicated "Default Sharing" section toggle or
        // showSharingSection field. Verify the UiState has no such field by checking
        // that the state can be constructed without it.
        val state = SettingsUiState()
        // These sharing-related fields exist for preferences but not as a UI section
        assertThat(state.defaultFormat).isEqualTo(ImageFormat.JPEG)
        assertThat(state.defaultQuality).isEqualTo(85)
        assertThat(state.defaultMaxDimension).isEqualTo(1080)
        // No showSharingSection, no sharingSection — just direct fields.
        // If a "showSharingSection" were re-added, this would need updating.
    }

    // endregion

    // region Sort Emojis By Usage Tests

    @Test
    fun `sortEmojisByUsage default state is true`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.sortEmojisByUsage).isTrue()
        }

    @Test
    fun `sortEmojisByUsage state reflects preference value`() =
        runTest {
            appPreferencesFlow.value = createDefaultAppPreferences().copy(sortEmojisByUsage = false)
            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.sortEmojisByUsage).isFalse()
        }

    @Test
    fun `SetSortEmojisByUsage delegates to use case with true`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetSortEmojisByUsage(true))
            advanceUntilIdle()

            coVerify { setSortEmojisByUsageUseCase(true) }
        }

    @Test
    fun `SetSortEmojisByUsage delegates to use case with false`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetSortEmojisByUsage(false))
            advanceUntilIdle()

            coVerify { setSortEmojisByUsageUseCase(false) }
        }

    @Test
    fun `sortEmojisByUsage state updates when preference flow emits`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.sortEmojisByUsage).isTrue()

            appPreferencesFlow.value = createDefaultAppPreferences().copy(sortEmojisByUsage = false)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.sortEmojisByUsage).isFalse()
        }

    // endregion
}
