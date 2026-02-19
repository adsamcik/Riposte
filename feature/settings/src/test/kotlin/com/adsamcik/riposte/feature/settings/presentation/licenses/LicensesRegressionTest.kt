package com.adsamcik.riposte.feature.settings.presentation.licenses

import com.adsamcik.riposte.feature.settings.presentation.SettingsEffect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regression tests for the licenses screen integration.
 * Ensures the licenses feature contract is maintained across changes.
 */
class LicensesRegressionTest {

    @Test
    fun `NavigateToLicenses effect is a data object`() {
        val effect = SettingsEffect.NavigateToLicenses
        assertThat(effect).isSameInstanceAs(SettingsEffect.NavigateToLicenses)
    }

    @Test
    fun `NavigateToLicenses is not ShowSnackbar`() {
        val effect: SettingsEffect = SettingsEffect.NavigateToLicenses
        assertThat(effect).isNotInstanceOf(SettingsEffect.ShowSnackbar::class.java)
    }

    @Test
    fun `all SettingsEffect subtypes are handled`() {
        // Exhaustive when expression — compile-time guard against adding a new
        // SettingsEffect subtype without handling it. If a new subtype is added,
        // this test will fail to compile until the new branch is added here.
        val effects: List<SettingsEffect> = listOf(
            SettingsEffect.ShowSnackbar("test"),
            SettingsEffect.NavigateToLicenses,
            SettingsEffect.OpenUrl("https://example.com"),
            SettingsEffect.LaunchExportPicker,
            SettingsEffect.LaunchImportPicker,
            SettingsEffect.ExportComplete("/path"),
            SettingsEffect.ImportComplete(5),
            SettingsEffect.ShowError("error"),
            SettingsEffect.ShareText("text", "title"),
        )

        effects.forEach { effect ->
            val handled = when (effect) {
                is SettingsEffect.ShowSnackbar -> true
                is SettingsEffect.NavigateToLicenses -> true
                is SettingsEffect.NavigateToFunStats -> true
                is SettingsEffect.OpenUrl -> true
                is SettingsEffect.LaunchExportPicker -> true
                is SettingsEffect.LaunchImportPicker -> true
                is SettingsEffect.ExportComplete -> true
                is SettingsEffect.ImportComplete -> true
                is SettingsEffect.ShowError -> true
                is SettingsEffect.ShareText -> true
            }
            assertThat(handled).isTrue()
        }
    }

    @Test
    fun `NavigateToLicenses equals itself`() {
        assertThat(SettingsEffect.NavigateToLicenses)
            .isEqualTo(SettingsEffect.NavigateToLicenses)
    }
}
