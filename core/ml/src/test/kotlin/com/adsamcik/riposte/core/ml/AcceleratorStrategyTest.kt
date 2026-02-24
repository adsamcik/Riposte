package com.adsamcik.riposte.core.ml

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.spyk
import org.junit.Test

/**
 * Tests for [AcceleratorStrategy].
 *
 * SoC-dependent methods are tested by spying and overriding [AcceleratorStrategy.detectSocModel]
 * to avoid reliance on [android.os.Build.SOC_MODEL] in unit tests.
 */
class AcceleratorStrategyTest {

    private fun createStrategy(socModel: String = "unknown"): AcceleratorStrategy {
        val strategy = spyk(AcceleratorStrategy())
        every { strategy.detectSocModel() } returns socModel.lowercase()
        return strategy
    }

    // region hasNpuSupport

    @Test
    fun `hasNpuSupport returns true for sm8650`() {
        val strategy = createStrategy("sm8650")

        assertThat(strategy.hasNpuSupport()).isTrue()
    }

    @Test
    fun `hasNpuSupport returns true for sm8750`() {
        val strategy = createStrategy("SM8750")

        assertThat(strategy.hasNpuSupport()).isTrue()
    }

    @Test
    fun `hasNpuSupport returns true for mt6993`() {
        val strategy = createStrategy("mt6993")

        assertThat(strategy.hasNpuSupport()).isTrue()
    }

    @Test
    fun `hasNpuSupport returns true for tensor SoC`() {
        val strategy = createStrategy("tensor")

        assertThat(strategy.hasNpuSupport()).isTrue()
    }

    @Test
    fun `hasNpuSupport returns false for unknown SoC`() {
        val strategy = createStrategy("unknown_soc_123")

        assertThat(strategy.hasNpuSupport()).isFalse()
    }

    // endregion

    // region getNpuVendor

    @Test
    fun `getNpuVendor returns QUALCOMM for sm8650`() {
        val strategy = createStrategy("sm8650")

        assertThat(strategy.getNpuVendor()).isEqualTo(AcceleratorStrategy.NpuVendor.QUALCOMM)
    }

    @Test
    fun `getNpuVendor returns QUALCOMM for sm8550`() {
        val strategy = createStrategy("sm8550")

        assertThat(strategy.getNpuVendor()).isEqualTo(AcceleratorStrategy.NpuVendor.QUALCOMM)
    }

    @Test
    fun `getNpuVendor returns MEDIATEK for mt6993`() {
        val strategy = createStrategy("mt6993")

        assertThat(strategy.getNpuVendor()).isEqualTo(AcceleratorStrategy.NpuVendor.MEDIATEK)
    }

    @Test
    fun `getNpuVendor returns MEDIATEK for mt6991`() {
        val strategy = createStrategy("mt6991")

        assertThat(strategy.getNpuVendor()).isEqualTo(AcceleratorStrategy.NpuVendor.MEDIATEK)
    }

    @Test
    fun `getNpuVendor returns GOOGLE for tensor`() {
        val strategy = createStrategy("tensor")

        assertThat(strategy.getNpuVendor()).isEqualTo(AcceleratorStrategy.NpuVendor.GOOGLE)
    }

    @Test
    fun `getNpuVendor returns null for unknown SoC`() {
        val strategy = createStrategy("unknown_chip")

        assertThat(strategy.getNpuVendor()).isNull()
    }

    // endregion

    // region getAotModelFilename

    @Test
    fun `getAotModelFilename returns correct filename for sm8650`() {
        val strategy = createStrategy("sm8650")

        assertThat(strategy.getAotModelFilename())
            .isEqualTo("embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8650.tflite")
    }

    @Test
    fun `getAotModelFilename returns correct filename for sm8750`() {
        val strategy = createStrategy("sm8750")

        assertThat(strategy.getAotModelFilename())
            .isEqualTo("embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8750.tflite")
    }

    @Test
    fun `getAotModelFilename returns correct filename for mt6993`() {
        val strategy = createStrategy("mt6993")

        assertThat(strategy.getAotModelFilename())
            .isEqualTo("embeddinggemma-300M_seq512_mixed-precision.mediatek.mt6993.tflite")
    }

    @Test
    fun `getAotModelFilename returns null for unknown SoC`() {
        val strategy = createStrategy("unknown_soc")

        assertThat(strategy.getAotModelFilename()).isNull()
    }

    @Test
    fun `getAotModelFilename returns null for tensor SoC without AOT model`() {
        val strategy = createStrategy("tensor")

        // Tensor SoC has NPU support but no AOT model in the map
        assertThat(strategy.getAotModelFilename()).isNull()
    }

    // endregion

    // region NpuVendor enum

    @Test
    fun `NpuVendor enum has all expected values`() {
        val vendors = AcceleratorStrategy.NpuVendor.entries

        assertThat(vendors).containsExactly(
            AcceleratorStrategy.NpuVendor.QUALCOMM,
            AcceleratorStrategy.NpuVendor.MEDIATEK,
            AcceleratorStrategy.NpuVendor.GOOGLE,
        )
    }

    // endregion

    // region Companion helper functions

    @Test
    fun `getNpuVendorForSoc returns correct vendor for Qualcomm SoC string`() {
        assertThat(AcceleratorStrategy.getNpuVendorForSoc("sm8475"))
            .isEqualTo(AcceleratorStrategy.NpuVendor.QUALCOMM)
    }

    @Test
    fun `getNpuVendorForSoc returns correct vendor for MediaTek SoC string`() {
        assertThat(AcceleratorStrategy.getNpuVendorForSoc("mt6989"))
            .isEqualTo(AcceleratorStrategy.NpuVendor.MEDIATEK)
    }

    @Test
    fun `getAotModelFilenameForSoc returns correct filename for sm8550`() {
        assertThat(AcceleratorStrategy.getAotModelFilenameForSoc("sm8550"))
            .isEqualTo("embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8550.tflite")
    }

    @Test
    fun `getAotModelFilenameForSoc returns null for unsupported SoC`() {
        assertThat(AcceleratorStrategy.getAotModelFilenameForSoc("exynos2400"))
            .isNull()
    }

    // endregion
}
