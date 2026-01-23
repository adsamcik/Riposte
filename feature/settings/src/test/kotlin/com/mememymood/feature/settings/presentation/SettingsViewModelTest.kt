package com.mememymood.feature.settings.presentation

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.datastore.PreferencesDataStore
import com.mememymood.core.model.AppPreferences
import com.mememymood.core.model.DarkMode
import com.mememymood.core.model.ImageFormat
import com.mememymood.core.model.SharingPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var preferencesDataStore: PreferencesDataStore
    private lateinit var viewModel: SettingsViewModel

    private val appPreferencesFlow = MutableStateFlow(createDefaultAppPreferences())
    private val sharingPreferencesFlow = MutableStateFlow(createDefaultSharingPreferences())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        preferencesDataStore = mockk(relaxed = true)

        // Mock package info for app version
        val packageInfo = PackageInfo().apply {
            versionName = "1.2.3"
            longVersionCode = 456
        }
        val packageManager: PackageManager = mockk()
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.mememymood"
        every { packageManager.getPackageInfo("com.mememymood", 0) } returns packageInfo

        // Setup cache directory mock
        val cacheDir: File = mockk(relaxed = true)
        every { context.cacheDir } returns cacheDir
        every { cacheDir.listFiles() } returns emptyArray()

        // Setup external files directory for export
        val externalFilesDir = File(System.getProperty("java.io.tmpdir"), "test_exports")
        externalFilesDir.mkdirs()
        every { context.getExternalFilesDir(null) } returns externalFilesDir

        // Setup datastore flows
        every { preferencesDataStore.appPreferences } returns appPreferencesFlow
        every { preferencesDataStore.sharingPreferences } returns sharingPreferencesFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            context = context,
            preferencesDataStore = preferencesDataStore,
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
        defaultFormat: ImageFormat = ImageFormat.WEBP,
        defaultQuality: Int = 85,
        maxWidth: Int = 1080,
        maxHeight: Int = 1080,
        keepMetadata: Boolean = true,
    ) = SharingPreferences(
        defaultFormat = defaultFormat,
        defaultQuality = defaultQuality,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        keepMetadata = keepMetadata,
    )

    // region Initialization Tests

    @Test
    fun `initial state has loading true`() = runTest {
        viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isTrue()
    }

    @Test
    fun `loads settings from datastore on initialization`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.darkMode).isEqualTo(DarkMode.SYSTEM)
        assertThat(state.dynamicColorsEnabled).isTrue()
    }

    @Test
    fun `loads app version on initialization`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.appVersion).isEqualTo("1.2.3 (456)")
    }

    @Test
    fun `app version fallback when package info fails`() = runTest {
        every { context.packageManager.getPackageInfo(any<String>(), any<Int>()) } throws Exception("Not found")

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.appVersion).isEqualTo("1.0.0")
    }

    @Test
    fun `loads all sharing preferences`() = runTest {
        sharingPreferencesFlow.value = createDefaultSharingPreferences(
            defaultFormat = ImageFormat.PNG,
            defaultQuality = 90,
            maxWidth = 2048,
            keepMetadata = false,
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.defaultFormat).isEqualTo(ImageFormat.PNG)
        assertThat(state.defaultQuality).isEqualTo(90)
        assertThat(state.defaultMaxDimension).isEqualTo(2048)
        assertThat(state.keepMetadata).isFalse()
    }

    @Test
    fun `loads search preferences`() = runTest {
        appPreferencesFlow.value = createDefaultAppPreferences(
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

    // region Dark Mode Tests

    @Test
    fun `SetDarkMode LIGHT updates preference`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SettingsIntent.SetDarkMode(DarkMode.LIGHT))
        advanceUntilIdle()

        coVerify { preferencesDataStore.setDarkMode(DarkMode.LIGHT) }
    }

    @Test
    fun `SetDarkMode DARK updates preference`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SettingsIntent.SetDarkMode(DarkMode.DARK))
        advanceUntilIdle()

        coVerify { preferencesDataStore.setDarkMode(DarkMode.DARK) }
    }

    @Test
    fun `SetDarkMode SYSTEM updates preference`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SettingsIntent.SetDarkMode(DarkMode.SYSTEM))
        advanceUntilIdle()

        coVerify { preferencesDataStore.setDarkMode(DarkMode.SYSTEM) }
    }

    @Test
    fun `dark mode state reflects datastore updates`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        appPreferencesFlow.value = createDefaultAppPreferences(darkMode = DarkMode.DARK)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.darkMode).isEqualTo(DarkMode.DARK)
    }

    // endregion

    // region Dynamic Colors Tests

    @Test
    fun `SetDynamicColors enabled updates app preferences`() = runTest {
        val prefsSlot = slot<AppPreferences>()
        coEvery { preferencesDataStore.updateAppPreferences(capture(prefsSlot)) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SettingsIntent.SetDynamicColors(true))
        advanceUntilIdle()

        coVerify { preferencesDataStore.updateAppPreferences(any()) }
        assertThat(prefsSlot.captured.dynamicColors).isTrue()
    }

    @Test
    fun `SetDynamicColors disabled updates app preferences`() = runTest {
        val prefsSlot = slot<AppPreferences>()
        coEvery { preferencesDataStore.updateAppPreferences(capture(prefsSlot)) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SettingsIntent.SetDynamicColors(false))
        advanceUntilIdle()

        coVerify { preferencesDataStore.updateAppPreferences(any()) }
        assertThat(prefsSlot.captured.dynamicColors).isFalse()
    }

    // endregion

    // region Sharing Preferences Tests

    @Test
    fun `SetDefaultFormat updates sharing preferences`() = runTest {
        val prefsSlot = slot<SharingPreferences>()
        coEvery { preferencesDataStore.updateSharingPreferences(capture(prefsSlot)) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SettingsIntent.SetDefaultFormat(ImageFormat.PNG))
        advanceUntilIdle()

        coVerify { preferencesDataStore.updateSharingPreferences(any()) }
        assertThat(prefsSlot.captured.defaultFormat).isEqualTo(ImageFormat.PNG)
    }

    @Test
    fun `SetDefaultQuality updates sharing preferences`() = runTest {
        val prefsSlot = slot<SharingPreferences>()
        coEvery { preferencesDataStore.updateSharingPreferences(capture(prefsSlot)) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SettingsIntent.SetDefaultQuality(95))
        advanceUntilIdle()

        coVerify { preferencesDataStore.updateSharingPreferences(any()) }
        assertThat(prefsSlot.captured.defaultQuality).isEqualTo(95)
    }

    @Test
    fun `SetDefaultMaxDimension updates both maxWidth and maxHeight`() = runTest {
        val prefsSlot = slot<SharingPreferences>()
        coEvery { preferencesDataStore.updateSharingPreferences(capture(prefsSlot)) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SettingsIntent.SetDefaultMaxDimension(2048))
        advanceUntilIdle()

        coVerify { preferencesDataStore.updateSharingPreferences(any()) }
        assertThat(prefsSlot.captured.maxWidth).isEqualTo(2048)
        assertThat(prefsSlot.captured.maxHeight).isEqualTo(2048)
    }

    @Test
    fun `SetKeepMetadata updates sharing preferences`() = runTest {
        val prefsSlot = slot<SharingPreferences>()
        coEvery { preferencesDataStore.updateSharingPreferences(capture(prefsSlot)) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SettingsIntent.SetKeepMetadata(false))
        advanceUntilIdle()

        coVerify { preferencesDataStore.updateSharingPreferences(any()) }
        assertThat(prefsSlot.captured.keepMetadata).isFalse()
    }

    // endregion

    // region Search Settings Tests

    @Test
    fun `SetEnableSemanticSearch updates app preferences`() = runTest {
        val prefsSlot = slot<AppPreferences>()
        coEvery { preferencesDataStore.updateAppPreferences(capture(prefsSlot)) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SettingsIntent.SetEnableSemanticSearch(false))
        advanceUntilIdle()

        coVerify { preferencesDataStore.updateAppPreferences(any()) }
        assertThat(prefsSlot.captured.enableSemanticSearch).isFalse()
    }

    @Test
    fun `SetSaveSearchHistory updates app preferences`() = runTest {
        val prefsSlot = slot<AppPreferences>()
        coEvery { preferencesDataStore.updateAppPreferences(capture(prefsSlot)) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SettingsIntent.SetSaveSearchHistory(false))
        advanceUntilIdle()

        coVerify { preferencesDataStore.updateAppPreferences(any()) }
        assertThat(prefsSlot.captured.saveSearchHistory).isFalse()
    }

    // endregion

    // region Cache Management Tests

    @Test
    fun `CalculateCacheSize updates cache size in state`() = runTest {
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
    fun `cache size formats correctly for KB`() = runTest {
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
    fun `cache size formats correctly for bytes`() = runTest {
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
    fun `cache size formats correctly for GB`() = runTest {
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
    fun `calculates nested directory sizes`() = runTest {
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
    fun `ShowClearCacheDialog shows dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SettingsIntent.ShowClearCacheDialog)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showClearCacheDialog).isTrue()
    }

    @Test
    fun `DismissDialog hides dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SettingsIntent.ShowClearCacheDialog)
        advanceUntilIdle()
        viewModel.onIntent(SettingsIntent.DismissDialog)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showClearCacheDialog).isFalse()
    }

    @Test
    fun `ConfirmClearCache clears cache and emits success effect`() = runTest {
        val cacheDir: File = mockk(relaxed = true)
        val testFile: File = mockk(relaxed = true)
        every { context.cacheDir } returns cacheDir
        every { cacheDir.listFiles() } returns arrayOf(testFile)
        every { testFile.isDirectory } returns false
        every { testFile.length() } returns 1024L
        every { testFile.delete() } returns true

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
    fun `ConfirmClearCache emits error effect on failure`() = runTest {
        val cacheDir: File = mockk(relaxed = true)
        val testFile: File = mockk(relaxed = true)
        every { context.cacheDir } returns cacheDir
        every { cacheDir.listFiles() } returns arrayOf(testFile)
        every { testFile.isDirectory } returns false
        every { testFile.length() } returns 1024L

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

    // region Export/Import Tests

    @Test
    fun `ExportData emits ExportComplete effect`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(SettingsIntent.ExportData)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(SettingsEffect.ExportComplete::class.java)
            // The path will be dynamic based on temp file
            assertThat((effect as SettingsEffect.ExportComplete).path).contains("meme_my_mood_backup_")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ImportData emits LaunchImportPicker effect`() = runTest {
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

    // endregion

    // region About Section Tests

    @Test
    fun `OpenLicenses emits NavigateToLicenses effect`() = runTest {
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
    fun `OpenPrivacyPolicy emits OpenUrl effect with correct URL`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(SettingsIntent.OpenPrivacyPolicy)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(SettingsEffect.OpenUrl::class.java)
            assertThat((effect as SettingsEffect.OpenUrl).url).isEqualTo("https://meme-my-mood.app/privacy")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region State Flow Reactivity Tests

    @Test
    fun `state updates when datastore preferences change`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Verify initial state
        assertThat(viewModel.uiState.value.darkMode).isEqualTo(DarkMode.SYSTEM)

        // Update datastore
        appPreferencesFlow.value = createDefaultAppPreferences(
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
    fun `state updates when sharing preferences change`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Verify initial state
        assertThat(viewModel.uiState.value.defaultFormat).isEqualTo(ImageFormat.WEBP)

        // Update datastore
        sharingPreferencesFlow.value = createDefaultSharingPreferences(
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
}
