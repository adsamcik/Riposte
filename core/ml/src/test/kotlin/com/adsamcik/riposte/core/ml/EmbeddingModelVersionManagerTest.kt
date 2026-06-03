package com.adsamcik.riposte.core.ml

import android.content.Context
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EmbeddingModelVersionManagerTest {
    private lateinit var context: Context
    private lateinit var versionManager: EmbeddingModelVersionManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        versionManager = EmbeddingModelVersionManager(context)
    }

    // region currentModelVersion

    @Test
    fun `currentModelVersion returns current embeddinggemma version`() {
        assertThat(versionManager.currentModelVersion).isEqualTo(EmbeddingModelVersionManager.CURRENT_VERSION)
        assertThat(versionManager.currentModelVersion).startsWith("embeddinggemma")
    }

    // endregion

    // region getCurrentEmbeddingDimension

    @Test
    fun `getCurrentEmbeddingDimension returns 768`() {
        assertThat(versionManager.getCurrentEmbeddingDimension()).isEqualTo(768)
    }

    // endregion

    // region getModelInfo

    @Test
    fun `getModelInfo returns correct info for current model`() {
        val info = versionManager.getModelInfo()

        assertThat(info.version).isEqualTo(EmbeddingModelVersionManager.CURRENT_VERSION)
        assertThat(info.name).startsWith("embeddinggemma")
        assertThat(info.dimension).isEqualTo(768)
        assertThat(info.description).contains("EmbeddingGemma")
        assertThat(info.description).contains("768")
    }

    // endregion

    // region isVersionCompatible

    @Test
    fun `isVersionCompatible returns true for same model and major version`() {
        // Build a same-model, different-minor version off CURRENT_VERSION so this
        // test survives future version bumps without churn.
        val modelName = EmbeddingModelVersionManager.CURRENT_VERSION.substringBefore(":")
        val currentMajor = EmbeddingModelVersionManager.CURRENT_VERSION
            .substringAfter(":")
            .substringBefore(".")
        assertThat(versionManager.isVersionCompatible("$modelName:$currentMajor.0.0")).isTrue()
        assertThat(versionManager.isVersionCompatible("$modelName:$currentMajor.5.0")).isTrue()
        assertThat(versionManager.isVersionCompatible("$modelName:$currentMajor.99.99")).isTrue()
    }

    @Test
    fun `isVersionCompatible returns false for different major version`() {
        val modelName = EmbeddingModelVersionManager.CURRENT_VERSION.substringBefore(":")
        val currentMajor = EmbeddingModelVersionManager.CURRENT_VERSION
            .substringAfter(":")
            .substringBefore(".")
            .toInt()
        assertThat(versionManager.isVersionCompatible("$modelName:${currentMajor + 1}.0.0")).isFalse()
        assertThat(versionManager.isVersionCompatible("$modelName:0.1.0")).isFalse()
    }

    @Test
    fun `isVersionCompatible returns false for different model name`() {
        assertThat(versionManager.isVersionCompatible("othermodel:1.3.0")).isFalse()
    }

    @Test
    fun `isVersionCompatible handles malformed version gracefully`() {
        // Malformed versions fall back to major version 0
        assertThat(versionManager.isVersionCompatible("noversion")).isFalse()
        assertThat(versionManager.isVersionCompatible("bad:abc")).isFalse()
    }

    // endregion

    // region DataStore: updateToCurrentVersion / getLastUsedVersion / hasModelBeenUpgraded

    @Test
    fun `getLastUsedVersion returns null on fresh install`() =
        runTest {
            assertThat(versionManager.getLastUsedVersion()).isNull()
        }

    @Test
    fun `hasModelBeenUpgraded returns false on fresh install`() =
        runTest {
            // null lastVersion means first run, not an upgrade
            assertThat(versionManager.hasModelBeenUpgraded()).isFalse()
        }

    @Test
    fun `updateToCurrentVersion persists the version`() =
        runTest {
            versionManager.updateToCurrentVersion()

            assertThat(versionManager.getLastUsedVersion())
                .isEqualTo(EmbeddingModelVersionManager.CURRENT_VERSION)
        }

    @Test
    fun `hasModelBeenUpgraded returns false after updateToCurrentVersion`() =
        runTest {
            versionManager.updateToCurrentVersion()

            assertThat(versionManager.hasModelBeenUpgraded()).isFalse()
        }

    @Test
    fun `observeLastUsedVersion emits null then current after update`() =
        runTest {
            versionManager.observeLastUsedVersion().test {
                assertThat(awaitItem()).isNull()

                versionManager.updateToCurrentVersion()
                assertThat(awaitItem()).isEqualTo(EmbeddingModelVersionManager.CURRENT_VERSION)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // endregion

    // region clearInitializationFailure

    @Test
    fun `clearInitializationFailure does not crash on fresh install`() =
        runTest {
            // Should not throw even when no error keys exist
            versionManager.clearInitializationFailure()
        }

    // endregion
}
