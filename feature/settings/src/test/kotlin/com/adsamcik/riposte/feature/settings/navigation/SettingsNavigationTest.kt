package com.adsamcik.riposte.feature.settings.navigation

import com.adsamcik.riposte.core.common.navigation.LicensesRoute
import com.adsamcik.riposte.core.common.navigation.SettingsRoute
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests verifying the settings navigation contract.
 * Guards against accidental removal of navigation routes or functions.
 */
class SettingsNavigationTest {

    @Test
    fun `LicensesRoute exists and is distinct from SettingsRoute`() {
        assertThat(LicensesRoute).isNotSameInstanceAs(SettingsRoute)
        assertThat(LicensesRoute::class).isNotEqualTo(SettingsRoute::class)
    }

    @Test
    fun `LicensesRoute is an object singleton`() {
        val first = LicensesRoute
        val second = LicensesRoute
        assertThat(first).isSameInstanceAs(second)
    }
}
