package com.adsamcik.riposte.core.datastore

import android.content.Context
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.adsamcik.riposte.core.model.AppPreferences
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.SharingPreferences
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [PreferencesDataStore].
 *
 * Uses Robolectric for Android Context and DataStore operations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class PreferencesDataStoreTest {

    private lateinit var context: Context
    private lateinit var preferencesDataStore: PreferencesDataStore

    @Before
    fun setup() = runTest {
        context = RuntimeEnvironment.getApplication().applicationContext
        preferencesDataStore = PreferencesDataStore(context)
        // Clear all preferences to ensure test isolation
        preferencesDataStore.clearAll()
    }

    @After
    fun tearDown() = runTest {
        // Clean up after each test
        preferencesDataStore.clearAll()
    }

    // region App Preferences Tests

    @Test
    fun `appPreferences returns defaults when no preferences set`() = runTest {
        preferencesDataStore.appPreferences.test {
            val prefs = awaitItem()

            assertThat(prefs.darkMode).isEqualTo(DarkMode.SYSTEM)
            assertThat(prefs.dynamicColors).isTrue()
            assertThat(prefs.gridColumns).isEqualTo(2)
            assertThat(prefs.showEmojiNames).isFalse()
            assertThat(prefs.enableSemanticSearch).isTrue()
            assertThat(prefs.autoExtractText).isTrue()
            assertThat(prefs.saveSearchHistory).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateAppPreferences stores preferences correctly`() = runTest {
        val customPrefs = AppPreferences(
            darkMode = DarkMode.DARK,
            dynamicColors = false,
            gridColumns = 3,
            showEmojiNames = true,
            enableSemanticSearch = false,
            autoExtractText = false,
            saveSearchHistory = false,
        )

        preferencesDataStore.updateAppPreferences(customPrefs)

        preferencesDataStore.appPreferences.test {
            val prefs = awaitItem()

            assertThat(prefs.darkMode).isEqualTo(DarkMode.DARK)
            assertThat(prefs.dynamicColors).isFalse()
            assertThat(prefs.gridColumns).isEqualTo(3)
            assertThat(prefs.showEmojiNames).isTrue()
            assertThat(prefs.enableSemanticSearch).isFalse()
            assertThat(prefs.autoExtractText).isFalse()
            assertThat(prefs.saveSearchHistory).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDarkMode updates dark mode preference`() = runTest {
        preferencesDataStore.setDarkMode(DarkMode.LIGHT)

        preferencesDataStore.appPreferences.test {
            val prefs = awaitItem()
            assertThat(prefs.darkMode).isEqualTo(DarkMode.LIGHT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setGridColumns clamps value between 2 and 4`() = runTest {
        // Test lower bound
        preferencesDataStore.setGridColumns(1)
        preferencesDataStore.appPreferences.test {
            val prefs = awaitItem()
            assertThat(prefs.gridColumns).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }

        // Test upper bound
        preferencesDataStore.setGridColumns(5)
        preferencesDataStore.appPreferences.test {
            val prefs = awaitItem()
            assertThat(prefs.gridColumns).isEqualTo(4)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region Sharing Preferences Tests

    @Test
    fun `sharingPreferences returns defaults when no preferences set`() = runTest {
        preferencesDataStore.sharingPreferences.test {
            val prefs = awaitItem()

            assertThat(prefs.defaultFormat).isEqualTo(ImageFormat.WEBP)
            assertThat(prefs.defaultQuality).isEqualTo(85)
            assertThat(prefs.maxWidth).isEqualTo(1080)
            assertThat(prefs.maxHeight).isEqualTo(1080)
            assertThat(prefs.stripMetadata).isTrue()
            assertThat(prefs.recentShareTargets).isEmpty()
            assertThat(prefs.favoriteShareTargets).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateSharingPreferences stores preferences correctly`() = runTest {
        val customPrefs = SharingPreferences(
            defaultFormat = ImageFormat.PNG,
            defaultQuality = 90,
            maxWidth = 800,
            maxHeight = 600,
            stripMetadata = false,
            recentShareTargets = listOf("com.example.app1"),
            favoriteShareTargets = listOf("com.example.app2"),
        )

        preferencesDataStore.updateSharingPreferences(customPrefs)

        preferencesDataStore.sharingPreferences.test {
            val prefs = awaitItem()

            assertThat(prefs.defaultFormat).isEqualTo(ImageFormat.PNG)
            assertThat(prefs.defaultQuality).isEqualTo(90)
            assertThat(prefs.maxWidth).isEqualTo(800)
            assertThat(prefs.maxHeight).isEqualTo(600)
            assertThat(prefs.stripMetadata).isFalse()
            assertThat(prefs.recentShareTargets).containsExactly("com.example.app1")
            assertThat(prefs.favoriteShareTargets).containsExactly("com.example.app2")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addRecentShareTarget adds package to front of list`() = runTest {
        preferencesDataStore.addRecentShareTarget("com.app1")
        preferencesDataStore.addRecentShareTarget("com.app2")

        preferencesDataStore.sharingPreferences.test {
            val prefs = awaitItem()
            assertThat(prefs.recentShareTargets.first()).isEqualTo("com.app2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addRecentShareTarget keeps only 10 most recent`() = runTest {
        // Add 12 packages
        repeat(12) { i ->
            preferencesDataStore.addRecentShareTarget("com.app$i")
        }

        preferencesDataStore.sharingPreferences.test {
            val prefs = awaitItem()
            assertThat(prefs.recentShareTargets).hasSize(10)
            // Most recent should be at front
            assertThat(prefs.recentShareTargets.first()).isEqualTo("com.app11")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region Recent Searches Tests

    @Test
    fun `recentSearches returns empty list initially`() = runTest {
        preferencesDataStore.recentSearches.test {
            val searches = awaitItem()
            assertThat(searches).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addRecentSearch adds query to front of list`() = runTest {
        preferencesDataStore.addRecentSearch("query1")
        preferencesDataStore.addRecentSearch("query2")

        preferencesDataStore.recentSearches.test {
            val searches = awaitItem()
            assertThat(searches.first()).isEqualTo("query2")
            assertThat(searches).contains("query1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addRecentSearch moves existing query to front`() = runTest {
        preferencesDataStore.addRecentSearch("query1")
        preferencesDataStore.addRecentSearch("query2")
        preferencesDataStore.addRecentSearch("query1") // Re-add query1

        preferencesDataStore.recentSearches.test {
            val searches = awaitItem()
            // query1 should now be first
            assertThat(searches.first()).isEqualTo("query1")
            // Should not have duplicates
            assertThat(searches.count { it == "query1" }).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteRecentSearch removes specific query`() = runTest {
        preferencesDataStore.addRecentSearch("query1")
        preferencesDataStore.addRecentSearch("query2")

        preferencesDataStore.deleteRecentSearch("query1")

        preferencesDataStore.recentSearches.test {
            val searches = awaitItem()
            assertThat(searches).doesNotContain("query1")
            assertThat(searches).contains("query2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearRecentSearches removes all searches`() = runTest {
        preferencesDataStore.addRecentSearch("query1")
        preferencesDataStore.addRecentSearch("query2")

        preferencesDataStore.clearRecentSearches()

        preferencesDataStore.recentSearches.test {
            val searches = awaitItem()
            assertThat(searches).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addRecentSearch keeps maximum of 20 searches`() = runTest {
        // Add 25 searches
        repeat(25) { i ->
            preferencesDataStore.addRecentSearch("search$i")
        }

        preferencesDataStore.recentSearches.test {
            val searches = awaitItem()
            assertThat(searches).hasSize(20)
            // Most recent should be at front
            assertThat(searches.first()).isEqualTo("search24")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region Onboarding Tips Tests

    @Test
    fun `hasShownEmojiTip returns false by default`() = runTest {
        preferencesDataStore.hasShownEmojiTip.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setEmojiTipShown marks emoji tip as shown`() = runTest {
        preferencesDataStore.setEmojiTipShown()

        preferencesDataStore.hasShownEmojiTip.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hasShownSearchTip returns false by default`() = runTest {
        preferencesDataStore.hasShownSearchTip.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSearchTipShown marks search tip as shown`() = runTest {
        preferencesDataStore.setSearchTipShown()

        preferencesDataStore.hasShownSearchTip.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hasShownShareTip returns false by default`() = runTest {
        preferencesDataStore.hasShownShareTip.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setShareTipShown marks share tip as shown`() = runTest {
        preferencesDataStore.setShareTipShown()

        preferencesDataStore.hasShownShareTip.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tip shown state persists and is not repeated`() = runTest {
        // Initially not shown
        preferencesDataStore.hasShownEmojiTip.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }

        // Mark as shown
        preferencesDataStore.setEmojiTipShown()

        // Should remain true
        preferencesDataStore.hasShownEmojiTip.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }

        // Calling set again should not change anything
        preferencesDataStore.setEmojiTipShown()

        preferencesDataStore.hasShownEmojiTip.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each tip tracks independently`() = runTest {
        preferencesDataStore.setEmojiTipShown()

        preferencesDataStore.hasShownEmojiTip.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        preferencesDataStore.hasShownSearchTip.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        preferencesDataStore.hasShownShareTip.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearAll resets tip shown state`() = runTest {
        preferencesDataStore.setEmojiTipShown()
        preferencesDataStore.setSearchTipShown()
        preferencesDataStore.setShareTipShown()

        preferencesDataStore.clearAll()

        preferencesDataStore.hasShownEmojiTip.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        preferencesDataStore.hasShownSearchTip.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        preferencesDataStore.hasShownShareTip.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion
}
