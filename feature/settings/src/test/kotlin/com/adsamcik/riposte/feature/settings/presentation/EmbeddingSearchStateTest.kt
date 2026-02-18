package com.adsamcik.riposte.feature.settings.presentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for EmbeddingSearchState data class behavior.
 *
 * These tests guard the no-silent-fallback design decision by verifying
 * that the UI state model correctly represents error conditions and
 * indexing progress. See docs/SEMANTIC_SEARCH.md "Error Handling — No Silent Fallback".
 */
class EmbeddingSearchStateTest {

    // region isFullyIndexed

    @Test
    fun `isFullyIndexed returns true when no pending or regeneration work`() {
        val state = createState(pendingCount = 0, regenerationCount = 0)
        assertThat(state.isFullyIndexed).isTrue()
    }

    @Test
    fun `isFullyIndexed returns false when pending work exists`() {
        val state = createState(pendingCount = 5, regenerationCount = 0)
        assertThat(state.isFullyIndexed).isFalse()
    }

    @Test
    fun `isFullyIndexed returns false when regeneration work exists`() {
        val state = createState(pendingCount = 0, regenerationCount = 3)
        assertThat(state.isFullyIndexed).isFalse()
    }

    @Test
    fun `isFullyIndexed returns false when both pending and regeneration work exist`() {
        val state = createState(pendingCount = 5, regenerationCount = 3)
        assertThat(state.isFullyIndexed).isFalse()
    }

    // endregion

    // region modelError field behavior

    @Test
    fun `modelError defaults to null`() {
        val state = createState()
        assertThat(state.modelError).isNull()
    }

    @Test
    fun `modelError preserves error string`() {
        val state = createState(modelError = "Model not compatible with this device")
        assertThat(state.modelError).isEqualTo("Model not compatible with this device")
    }

    @Test
    fun `modelError preserves all known error types`() {
        val knownErrors = listOf(
            "Model not compatible with this device",
            "Model files not found",
            "Model failed to load",
            "Model initialization failed",
        )

        for (error in knownErrors) {
            val state = createState(modelError = error)
            assertThat(state.modelError).isEqualTo(error)
        }
    }

    @Test
    fun `state with error still reports correct indexing progress`() {
        val state = createState(
            indexedCount = 42,
            totalCount = 100,
            pendingCount = 58,
            modelError = "Model failed to load",
        )

        assertThat(state.modelError).isEqualTo("Model failed to load")
        assertThat(state.indexedCount).isEqualTo(42)
        assertThat(state.totalCount).isEqualTo(100)
        assertThat(state.pendingCount).isEqualTo(58)
        assertThat(state.isFullyIndexed).isFalse()
    }

    @Test
    fun `state with error and zero progress does not hide error behind indexing state`() {
        val state = createState(
            indexedCount = 0,
            totalCount = 50,
            pendingCount = 50,
            modelError = "Model not compatible with this device",
        )

        // Error should be present even when nothing is indexed
        assertThat(state.modelError).isNotNull()
        assertThat(state.indexedCount).isEqualTo(0)
        assertThat(state.isFullyIndexed).isFalse()
    }

    @Test
    fun `state with empty string error is not treated as null`() {
        val state = createState(modelError = "")

        // Empty string is distinct from null
        assertThat(state.modelError).isNotNull()
        assertThat(state.modelError).isEmpty()
    }

    // endregion

    // region Data class equality with errors

    @Test
    fun `states with same error are equal`() {
        val state1 = createState(modelError = "Model failed to load")
        val state2 = createState(modelError = "Model failed to load")
        assertThat(state1).isEqualTo(state2)
    }

    @Test
    fun `states with different errors are not equal`() {
        val state1 = createState(modelError = "Model failed to load")
        val state2 = createState(modelError = "Model not compatible with this device")
        assertThat(state1).isNotEqualTo(state2)
    }

    @Test
    fun `state with error is not equal to state without error`() {
        val stateWithError = createState(modelError = "Model failed to load")
        val stateWithoutError = createState(modelError = null)
        assertThat(stateWithError).isNotEqualTo(stateWithoutError)
    }

    // endregion

    private fun createState(
        indexedCount: Int = 0,
        totalCount: Int = 0,
        pendingCount: Int = 0,
        regenerationCount: Int = 0,
        modelError: String? = null,
    ) = EmbeddingSearchState(
        modelName = "EmbeddingGemma",
        modelVersion = "embeddinggemma:1.0.0",
        dimension = 768,
        indexedCount = indexedCount,
        totalCount = totalCount,
        pendingCount = pendingCount,
        regenerationCount = regenerationCount,
        modelError = modelError,
    )
}
