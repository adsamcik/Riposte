package com.adsamcik.riposte.core.model

import kotlinx.serialization.Serializable

/**
 * Device capability tier for adaptive search quality.
 * AUTO detects the tier from device RAM and SoC.
 */
@Serializable
enum class DeviceTier {
    /** Auto-detect based on device capabilities. */
    AUTO,
    /** Budget device: <4GB RAM or unknown SoC. FTS-only, 128d. */
    LOW,
    /** Mid-range: 4-8GB RAM. ANN with 256d. */
    BALANCED,
    /** Flagship: 8-12GB RAM, known high-end SoC. ANN with 384d. */
    HIGH,
    /** Ultra-premium: 12GB+ RAM, latest flagship SoC. Full 768d two-stage Matryoshka. */
    ULTRA,
}
