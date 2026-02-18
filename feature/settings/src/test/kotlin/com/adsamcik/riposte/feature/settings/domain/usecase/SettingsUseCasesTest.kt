package com.adsamcik.riposte.feature.settings.domain.usecase

import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.UserDensityPreference
import com.adsamcik.riposte.feature.settings.domain.repository.SettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SettingsUseCasesTest {
    private lateinit var repository: SettingsRepository

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
    }

    // region SetSortEmojisByUsageUseCase Tests

    @Test
    fun `SetSortEmojisByUsageUseCase delegates true to repository`() =
        runTest {
            val useCase = SetSortEmojisByUsageUseCase(repository)

            useCase(true)

            coVerify { repository.setSortEmojisByUsage(true) }
        }

    @Test
    fun `SetSortEmojisByUsageUseCase delegates false to repository`() =
        runTest {
            val useCase = SetSortEmojisByUsageUseCase(repository)

            useCase(false)

            coVerify { repository.setSortEmojisByUsage(false) }
        }

    @Test
    fun `SetSortEmojisByUsageUseCase calls repository exactly once`() =
        runTest {
            val useCase = SetSortEmojisByUsageUseCase(repository)

            useCase(true)

            coVerify(exactly = 1) { repository.setSortEmojisByUsage(any()) }
        }

    // endregion

    // region SetSaveSearchHistoryUseCase Tests

    @Test
    fun `SetSaveSearchHistoryUseCase delegates to repository`() =
        runTest {
            val useCase = SetSaveSearchHistoryUseCase(repository)

            useCase(false)

            coVerify { repository.setSaveSearchHistory(false) }
        }

    // endregion

    // region SetDarkModeUseCase Tests

    @Test
    fun `SetDarkModeUseCase delegates to repository`() =
        runTest {
            val useCase = SetDarkModeUseCase(repository)

            useCase(DarkMode.DARK)

            coVerify { repository.setDarkMode(DarkMode.DARK) }
        }

    // endregion

    // region SetDynamicColorsUseCase Tests

    @Test
    fun `SetDynamicColorsUseCase delegates to repository`() =
        runTest {
            val useCase = SetDynamicColorsUseCase(repository)

            useCase(false)

            coVerify { repository.setDynamicColors(false) }
        }

    // endregion

    // region ExportPreferencesUseCase Tests

    @Test
    fun `ExportPreferencesUseCase returns JSON from repository`() =
        runTest {
            val expectedJson = """{"version":1}"""
            coEvery { repository.exportPreferences() } returns expectedJson
            val useCase = ExportPreferencesUseCase(repository)

            val result = useCase()

            assertThat(result).isEqualTo(expectedJson)
            coVerify { repository.exportPreferences() }
        }

    // endregion

    // region ImportPreferencesUseCase Tests

    @Test
    fun `ImportPreferencesUseCase delegates JSON to repository`() =
        runTest {
            val json = """{"version":1}"""
            coEvery { repository.importPreferences(json) } returns Result.success(Unit)
            val useCase = ImportPreferencesUseCase(repository)

            val result = useCase(json)

            assertThat(result.isSuccess).isTrue()
            coVerify { repository.importPreferences(json) }
        }

    @Test
    fun `ImportPreferencesUseCase propagates failure`() =
        runTest {
            val json = "invalid"
            coEvery { repository.importPreferences(json) } returns Result.failure(Exception("Parse error"))
            val useCase = ImportPreferencesUseCase(repository)

            val result = useCase(json)

            assertThat(result.isFailure).isTrue()
        }

    // endregion
}
