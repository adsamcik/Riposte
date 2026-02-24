package com.adsamcik.riposte.core.ml

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.adsamcik.riposte.core.model.DeviceTier
import com.adsamcik.riposte.core.model.SearchQualityProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects device capabilities and resolves the appropriate search quality tier.
 */
@Singleton
class DeviceTierDetector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Cached resolved profile, computed once. */
    @Volatile
    private var cachedProfile: SearchQualityProfile? = null

    /**
     * Resolves the [SearchQualityProfile] for the current device.
     *
     * If [userOverride] is [DeviceTier.AUTO], auto-detects based on RAM and SoC.
     * Otherwise uses the user's explicit choice.
     */
    fun resolveProfile(userOverride: DeviceTier = DeviceTier.AUTO): SearchQualityProfile {
        if (userOverride != DeviceTier.AUTO) {
            return SearchQualityProfile.forTier(userOverride)
        }

        cachedProfile?.let { return it }

        val detected = detectTier()
        val profile = SearchQualityProfile.forTier(detected)
        cachedProfile = profile
        Timber.i(
            "Device tier: %s (RAM: %dMB, SoC: %s)",
            detected.name,
            getTotalRamMb(),
            getSocModel().ifEmpty { "unknown" },
        )
        return profile
    }

    /**
     * Returns the auto-detected [DeviceTier] (ignoring user override).
     */
    fun detectTier(): DeviceTier {
        val ramMb = getTotalRamMb()
        val socModel = getSocModel()

        return when {
            ramMb >= ULTRA_RAM_THRESHOLD_MB && isUltraSoc(socModel) -> DeviceTier.ULTRA
            ramMb >= HIGH_RAM_THRESHOLD_MB && isHighEndSoc(socModel) -> DeviceTier.HIGH
            ramMb >= BALANCED_RAM_THRESHOLD_MB -> DeviceTier.BALANCED
            else -> DeviceTier.LOW
        }
    }

    private fun getTotalRamMb(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / BYTES_PER_MB
    }

    private fun isUltraSoc(socModel: String): Boolean =
        ULTRA_SOCS.any { socModel.contains(it) }

    private fun isHighEndSoc(socModel: String): Boolean =
        HIGH_END_SOCS.any { socModel.contains(it) } || isUltraSoc(socModel)

    @Suppress("UNNECESSARY_SAFE_CALL")
    private fun getSocModel(): String =
        Build.SOC_MODEL?.lowercase().orEmpty()

    companion object {
        private const val BYTES_PER_MB = 1024L * 1024L
        private const val ULTRA_RAM_THRESHOLD_MB = 12_000L
        private const val HIGH_RAM_THRESHOLD_MB = 8_000L
        private const val BALANCED_RAM_THRESHOLD_MB = 4_000L

        /** Latest flagship SoCs (2024-2025+). */
        private val ULTRA_SOCS = listOf(
            "sm8750", // Snapdragon 8 Gen 5
            "sm8650", // Snapdragon 8 Gen 3
            "mt6993", // Dimensity 9400
        )

        /** High-end SoCs (2022-2024). */
        private val HIGH_END_SOCS = listOf(
            "sm8550", // Snapdragon 8 Gen 2
            "sm8475", // Snapdragon 8+ Gen 1
            "mt6991", // Dimensity 9300
            "mt6989", // Dimensity 9200
            "s5e9945", // Exynos 2400
            "s5e9925", // Exynos 2200
            "tensor", // Google Tensor
        )
    }
}
