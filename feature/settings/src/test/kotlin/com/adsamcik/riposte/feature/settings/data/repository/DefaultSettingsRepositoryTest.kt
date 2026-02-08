package com.adsamcik.riposte.feature.settings.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.model.AppPreferences
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.SharingPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSettingsRepositoryTest {

    private lateinit var preferencesDataStore: PreferencesDataStore
    private lateinit var repository: DefaultSettingsRepository

    private val appPreferencesFlow = MutableStateFlow(createDefaultAppPreferences())
    private val sharingPreferencesFlow = MutableStateFlow(createDefaultSharingPreferences())

    @Before
    fun setup() {
        preferencesDataStore = mockk(relaxed = true)
        every { preferencesDataStore.appPreferences } returns appPreferencesFlow
        every { preferencesDataStore.sharingPreferences } returns sharingPreferencesFlow

        repository = DefaultSettingsRepository(preferencesDataStore)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createDefaultAppPreferences(
        darkMode: DarkMode = DarkMode.SYSTEM,
        dynamicColors: Boolean = true,
        enableSemanticSearch: Boolean = true,
        saveSearchHistory: Boolean = true,
        holdToShareDelayMs: Long = 1500L,
    ) = AppPreferences(
        darkMode = darkMode,
        dynamicColors = dynamicColors,
        enableSemanticSearch = enableSemanticSearch,
        saveSearchHistory = saveSearchHistory,
        holdToShareDelayMs = holdToShareDelayMs,
    )

    private fun createDefaultSharingPreferences(
        defaultFormat: ImageFormat = ImageFormat.WEBP,
        defaultQuality: Int = 85,
        maxWidth: Int = 1080,
        maxHeight: Int = 1080,
    ) = SharingPreferences(
        defaultFormat = defaultFormat,
        defaultQuality = defaultQuality,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
    )

    // region Flow Tests

    @Test
    fun `appPreferences emits values from datastore`() = runTest {
        repository.appPreferences.test {
            val initial = awaitItem()
            assertThat(initial.darkMode).isEqualTo(DarkMode.SYSTEM)
            assertThat(initial.dynamicColors).isTrue()

            appPreferencesFlow.value = createDefaultAppPreferences(
                darkMode = DarkMode.DARK,
                dynamicColors = false,
            )

            val updated = awaitItem()
            assertThat(updated.darkMode).isEqualTo(DarkMode.DARK)
            assertThat(updated.dynamicColors).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sharingPreferences emits values from datastore`() = runTest {
        repository.sharingPreferences.test {
            val initial = awaitItem()
            assertThat(initial.defaultFormat).isEqualTo(ImageFormat.WEBP)
            assertThat(initial.defaultQuality).isEqualTo(85)

            sharingPreferencesFlow.value = createDefaultSharingPreferences(
                defaultFormat = ImageFormat.PNG,
                defaultQuality = 95,
            )

            val updated = awaitItem()
            assertThat(updated.defaultFormat).isEqualTo(ImageFormat.PNG)
            assertThat(updated.defaultQuality).isEqualTo(95)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region Dark Mode Tests

    @Test
    fun `setDarkMode delegates to datastore`() = runTest {
        repository.setDarkMode(DarkMode.LIGHT)

        coVerify { preferencesDataStore.setDarkMode(DarkMode.LIGHT) }
    }

    // endregion

    // region App Preferences Update Tests

    @Test
    fun `setDynamicColors updates app preferences with new value`() = runTest {
        val prefsSlot = slot<AppPreferences>()
        coEvery { preferencesDataStore.updateAppPreferences(capture(prefsSlot)) } returns Unit

        repository.setDynamicColors(false)

        coVerify { preferencesDataStore.updateAppPreferences(any()) }
        assertThat(prefsSlot.captured.dynamicColors).isFalse()
        // Verify other values are preserved
        assertThat(prefsSlot.captured.darkMode).isEqualTo(DarkMode.SYSTEM)
    }

    @Test
    fun `setEnableSemanticSearch updates app preferences with new value`() = runTest {
        val prefsSlot = slot<AppPreferences>()
        coEvery { preferencesDataStore.updateAppPreferences(capture(prefsSlot)) } returns Unit

        repository.setEnableSemanticSearch(false)

        coVerify { preferencesDataStore.updateAppPreferences(any()) }
        assertThat(prefsSlot.captured.enableSemanticSearch).isFalse()
    }

    @Test
    fun `setSaveSearchHistory updates app preferences with new value`() = runTest {
        val prefsSlot = slot<AppPreferences>()
        coEvery { preferencesDataStore.updateAppPreferences(capture(prefsSlot)) } returns Unit

        repository.setSaveSearchHistory(false)

        coVerify { preferencesDataStore.updateAppPreferences(any()) }
        assertThat(prefsSlot.captured.saveSearchHistory).isFalse()
    }

    @Test
    fun `setHoldToShareDelay updates app preferences with new value`() = runTest {
        val prefsSlot = slot<AppPreferences>()
        coEvery { preferencesDataStore.updateAppPreferences(capture(prefsSlot)) } returns Unit

        repository.setHoldToShareDelay(2000L)

        coVerify { preferencesDataStore.updateAppPreferences(any()) }
        assertThat(prefsSlot.captured.holdToShareDelayMs).isEqualTo(2000L)
    }

    // endregion

    // region Sharing Preferences Update Tests

    @Test
    fun `setDefaultFormat updates sharing preferences with new value`() = runTest {
        val prefsSlot = slot<SharingPreferences>()
        coEvery { preferencesDataStore.updateSharingPreferences(capture(prefsSlot)) } returns Unit

        repository.setDefaultFormat(ImageFormat.PNG)

        coVerify { preferencesDataStore.updateSharingPreferences(any()) }
        assertThat(prefsSlot.captured.defaultFormat).isEqualTo(ImageFormat.PNG)
        // Verify other values are preserved
        assertThat(prefsSlot.captured.defaultQuality).isEqualTo(85)
    }

    @Test
    fun `setDefaultQuality updates sharing preferences with new value`() = runTest {
        val prefsSlot = slot<SharingPreferences>()
        coEvery { preferencesDataStore.updateSharingPreferences(capture(prefsSlot)) } returns Unit

        repository.setDefaultQuality(95)

        coVerify { preferencesDataStore.updateSharingPreferences(any()) }
        assertThat(prefsSlot.captured.defaultQuality).isEqualTo(95)
    }

    @Test
    fun `setDefaultMaxDimension updates both maxWidth and maxHeight`() = runTest {
        val prefsSlot = slot<SharingPreferences>()
        coEvery { preferencesDataStore.updateSharingPreferences(capture(prefsSlot)) } returns Unit

        repository.setDefaultMaxDimension(2048)

        coVerify { preferencesDataStore.updateSharingPreferences(any()) }
        assertThat(prefsSlot.captured.maxWidth).isEqualTo(2048)
        assertThat(prefsSlot.captured.maxHeight).isEqualTo(2048)
    }

    // endregion

    // region Export/Import Tests

    @Test
    fun `exportPreferences returns valid JSON`() = runTest {
        val json = repository.exportPreferences()

        assertThat(json).contains("\"version\"")
        assertThat(json).contains("\"timestamp\"")
        assertThat(json).contains("\"sharingPreferences\"")
        assertThat(json).contains("\"appPreferences\"")
        assertThat(json).contains("\"darkMode\":\"SYSTEM\"")
        assertThat(json).contains("\"defaultFormat\":\"WEBP\"")
    }

    @Test
    fun `importPreferences with valid JSON succeeds`() = runTest {
        val prefsSlot = slot<SharingPreferences>()
        val appPrefsSlot = slot<AppPreferences>()
        coEvery { preferencesDataStore.updateSharingPreferences(capture(prefsSlot)) } returns Unit
        coEvery { preferencesDataStore.updateAppPreferences(capture(appPrefsSlot)) } returns Unit

        val json = """
            {
                "version": 1,
                "timestamp": 0,
                "sharingPreferences": {
                    "defaultFormat": "PNG",
                    "defaultQuality": 90,
                    "maxWidth": 2048,
                    "maxHeight": 2048,
                    "keepMetadata": false
                },
                "appPreferences": {
                    "darkMode": "DARK",
                    "dynamicColors": false,
                    "enableSemanticSearch": false,
                    "saveSearchHistory": false
                }
            }
        """.trimIndent()

        val result = repository.importPreferences(json)

        assertThat(result.isSuccess).isTrue()
        coVerify { preferencesDataStore.updateSharingPreferences(any()) }
        coVerify { preferencesDataStore.updateAppPreferences(any()) }
        assertThat(prefsSlot.captured.defaultFormat).isEqualTo(ImageFormat.PNG)
    }

    @Test
    fun `importPreferences with invalid JSON returns failure`() = runTest {
        val result = repository.importPreferences("not valid json")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `importPreferences preserves existing values for missing fields`() = runTest {
        val prefsSlot = slot<SharingPreferences>()
        coEvery { preferencesDataStore.updateSharingPreferences(capture(prefsSlot)) } returns Unit

        val json = """
            {
                "version": 1,
                "sharingPreferences": {
                    "defaultFormat": "PNG"
                }
            }
        """.trimIndent()

        repository.importPreferences(json)

        assertThat(prefsSlot.captured.defaultFormat).isEqualTo(ImageFormat.PNG)
        // These should be preserved from current preferences
        assertThat(prefsSlot.captured.defaultQuality).isEqualTo(85)
        assertThat(prefsSlot.captured.maxWidth).isEqualTo(1080)
    }

    // endregion
}
