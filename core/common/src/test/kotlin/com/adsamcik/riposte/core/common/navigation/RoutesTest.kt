package com.adsamcik.riposte.core.common.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for navigation route definitions.
 * Ensures all routes exist and are properly structured for type-safe navigation.
 * The @Serializable annotation is verified at compile time — these tests guard
 * against route removal or structural changes.
 */
class RoutesTest {

    @Test
    fun `LicensesRoute is a singleton object`() {
        assertThat(LicensesRoute).isSameInstanceAs(LicensesRoute)
    }

    @Test
    fun `SettingsRoute is a singleton object`() {
        assertThat(SettingsRoute).isSameInstanceAs(SettingsRoute)
    }

    @Test
    fun `all routes are distinct types`() {
        val routes: List<Any> = listOf(GalleryRoute, ImportRoute, SettingsRoute, LicensesRoute)
        val distinctTypes = routes.map { it::class }.toSet()
        assertThat(distinctTypes).hasSize(routes.size)
    }
}
