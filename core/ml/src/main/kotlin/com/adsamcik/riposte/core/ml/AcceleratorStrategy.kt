package com.adsamcik.riposte.core.ml

import android.os.Build
import com.google.ai.edge.litert.Accelerator
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Determines the best LiteRT hardware accelerator for the current device.
 *
 * Priority order:
 * 1. NPU (if SoC has known NPU support and model is AOT-compiled)
 * 2. GPU (if OpenCL is available)
 * 3. CPU (fallback)
 *
 * NPU support requires:
 * - A known SoC with NPU capability
 * - A pre-compiled model for that specific SoC (AOT compilation)
 * - The sm8650 or similar build flavor that includes SoC-specific models
 */
@Singleton
class AcceleratorStrategy @Inject constructor() {

    /**
     * Returns the preferred accelerator for inference.
     * Currently returns GPU or CPU. NPU support is infrastructure-only
     * and will be enabled when AOT-compiled models are validated on-device.
     */
    fun getBestAccelerator(): Accelerator {
        // NPU detection is available but disabled pending AOT model validation.
        // Uncomment when SoC-specific models are tested:
        // if (hasNpuSupport()) return Accelerator.GPU // LiteRT maps GPU to NPU via delegate

        return if (isGpuAvailable()) {
            Timber.d("Using GPU accelerator")
            Accelerator.GPU
        } else {
            Timber.d("Using CPU accelerator (GPU not available)")
            Accelerator.CPU
        }
    }

    /**
     * Checks if the current device has a known NPU-capable SoC.
     */
    fun hasNpuSupport(): Boolean {
        val socModel = detectSocModel()
        return NPU_CAPABLE_SOCS.any { socModel.contains(it) }
    }

    /**
     * Returns the NPU vendor for the current SoC, if known.
     */
    fun getNpuVendor(): NpuVendor? {
        val socModel = detectSocModel()
        return getNpuVendorForSoc(socModel)
    }

    /**
     * Returns the AOT model filename for the current device's NPU, if available.
     * Returns null if no NPU-specific model exists.
     */
    fun getAotModelFilename(): String? {
        val socModel = detectSocModel()
        return getAotModelFilenameForSoc(socModel)
    }

    /**
     * Returns the lowercase SoC model identifier.
     * Extracted for testability.
     */
    internal fun detectSocModel(): String =
        @Suppress("UNNECESSARY_SAFE_CALL")
        Build.SOC_MODEL?.lowercase().orEmpty()

    /** Checks if OpenCL is available for GPU acceleration. */
    private fun isGpuAvailable(): Boolean =
        try {
            System.loadLibrary("OpenCL")
            true
        } catch (e: UnsatisfiedLinkError) {
            Timber.d(e, "OpenCL not available on this device")
            false
        }

    /** Known NPU vendors. */
    enum class NpuVendor {
        /** Qualcomm AI Engine Direct (Hexagon DSP). */
        QUALCOMM,
        /** MediaTek NeuroPilot (APU). */
        MEDIATEK,
        /** Google Tensor (EdgeTPU). */
        GOOGLE,
    }

    companion object {
        /** Qualcomm SoCs with Hexagon NPU. */
        private val QUALCOMM_NPU_SOCS = listOf(
            "sm8650", // 8 Gen 3
            "sm8750", // 8 Gen 5
            "sm8550", // 8 Gen 2
            "sm8475", // 8+ Gen 1
        )

        /** MediaTek SoCs with APU NPU. */
        private val MEDIATEK_NPU_SOCS = listOf(
            "mt6993", // Dimensity 9400
            "mt6991", // Dimensity 9300
            "mt6989", // Dimensity 9200
        )

        /** Google Tensor SoCs with EdgeTPU. */
        private val GOOGLE_NPU_SOCS = listOf(
            "tensor", // Google Tensor (all generations)
        )

        /** All NPU-capable SoCs. */
        private val NPU_CAPABLE_SOCS = QUALCOMM_NPU_SOCS + MEDIATEK_NPU_SOCS + GOOGLE_NPU_SOCS

        /**
         * Map from SoC model to AOT-compiled TFLite model filename.
         * These models are pre-compiled for the specific NPU and skip
         * JIT compilation at runtime.
         */
        private val AOT_MODEL_MAP = mapOf(
            "sm8650" to "embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8650.tflite",
            "sm8750" to "embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8750.tflite",
            "sm8550" to "embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8550.tflite",
            "mt6993" to "embeddinggemma-300M_seq512_mixed-precision.mediatek.mt6993.tflite",
            "mt6991" to "embeddinggemma-300M_seq512_mixed-precision.mediatek.mt6991.tflite",
        )

        /**
         * Determines the NPU vendor for a given SoC model string.
         */
        internal fun getNpuVendorForSoc(socModel: String): NpuVendor? =
            when {
                QUALCOMM_NPU_SOCS.any { socModel.contains(it) } -> NpuVendor.QUALCOMM
                MEDIATEK_NPU_SOCS.any { socModel.contains(it) } -> NpuVendor.MEDIATEK
                GOOGLE_NPU_SOCS.any { socModel.contains(it) } -> NpuVendor.GOOGLE
                else -> null
            }

        /**
         * Returns the AOT model filename for a given SoC model string.
         */
        internal fun getAotModelFilenameForSoc(socModel: String): String? =
            AOT_MODEL_MAP.entries.firstOrNull { (soc, _) ->
                socModel.contains(soc)
            }?.value
    }
}
