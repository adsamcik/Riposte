package com.adsamcik.riposte.core.model

/**
 * Search quality parameters derived from device tier.
 */
data class SearchQualityProfile(
    /** Embedding dimension for ANN index (128, 256, 384, or 768). */
    val annIndexDimension: Int,
    /** Whether to use ANN index for fast retrieval. */
    val useAnn: Boolean,
    /** Number of ANN first-pass candidates before reranking. */
    val annFirstPassK: Int,
    /** Whether to use F16 quantization for ANN index (2x storage reduction). */
    val useF16Index: Boolean,
    /** Maximum number of search results to return. */
    val maxSearchResults: Int,
    /** The resolved device tier (never AUTO). */
    val resolvedTier: DeviceTier,
) {
    companion object {
        fun forTier(tier: DeviceTier): SearchQualityProfile = when (tier) {
            DeviceTier.AUTO -> throw IllegalArgumentException("AUTO must be resolved first")
            DeviceTier.LOW -> SearchQualityProfile(
                annIndexDimension = 128,
                useAnn = false,
                annFirstPassK = 0,
                useF16Index = false,
                maxSearchResults = 20,
                resolvedTier = DeviceTier.LOW,
            )
            DeviceTier.BALANCED -> SearchQualityProfile(
                annIndexDimension = 256,
                useAnn = true,
                annFirstPassK = 50,
                useF16Index = true,
                maxSearchResults = 30,
                resolvedTier = DeviceTier.BALANCED,
            )
            DeviceTier.HIGH -> SearchQualityProfile(
                annIndexDimension = 384,
                useAnn = true,
                annFirstPassK = 100,
                useF16Index = false,
                maxSearchResults = 50,
                resolvedTier = DeviceTier.HIGH,
            )
            DeviceTier.ULTRA -> SearchQualityProfile(
                annIndexDimension = 768,
                useAnn = true,
                annFirstPassK = 200,
                useF16Index = false,
                maxSearchResults = 100,
                resolvedTier = DeviceTier.ULTRA,
            )
        }
    }
}
