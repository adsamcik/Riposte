package com.adsamcik.riposte.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchQualityProfileTest {

    @Test
    fun `forTier LOW disables ANN`() {
        val profile = SearchQualityProfile.forTier(DeviceTier.LOW)

        assertThat(profile.useAnn).isFalse()
        assertThat(profile.annIndexDimension).isEqualTo(128)
        assertThat(profile.annFirstPassK).isEqualTo(0)
        assertThat(profile.useF16Index).isFalse()
        assertThat(profile.maxSearchResults).isEqualTo(20)
        assertThat(profile.resolvedTier).isEqualTo(DeviceTier.LOW)
    }

    @Test
    fun `forTier BALANCED uses 256d with F16`() {
        val profile = SearchQualityProfile.forTier(DeviceTier.BALANCED)

        assertThat(profile.useAnn).isTrue()
        assertThat(profile.annIndexDimension).isEqualTo(256)
        assertThat(profile.annFirstPassK).isEqualTo(50)
        assertThat(profile.useF16Index).isTrue()
        assertThat(profile.maxSearchResults).isEqualTo(30)
        assertThat(profile.resolvedTier).isEqualTo(DeviceTier.BALANCED)
    }

    @Test
    fun `forTier HIGH uses 384d without F16`() {
        val profile = SearchQualityProfile.forTier(DeviceTier.HIGH)

        assertThat(profile.useAnn).isTrue()
        assertThat(profile.annIndexDimension).isEqualTo(384)
        assertThat(profile.annFirstPassK).isEqualTo(100)
        assertThat(profile.useF16Index).isFalse()
        assertThat(profile.maxSearchResults).isEqualTo(50)
        assertThat(profile.resolvedTier).isEqualTo(DeviceTier.HIGH)
    }

    @Test
    fun `forTier ULTRA uses full 768d`() {
        val profile = SearchQualityProfile.forTier(DeviceTier.ULTRA)

        assertThat(profile.useAnn).isTrue()
        assertThat(profile.annIndexDimension).isEqualTo(768)
        assertThat(profile.annFirstPassK).isEqualTo(200)
        assertThat(profile.useF16Index).isFalse()
        assertThat(profile.maxSearchResults).isEqualTo(100)
        assertThat(profile.resolvedTier).isEqualTo(DeviceTier.ULTRA)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `forTier AUTO throws IllegalArgumentException`() {
        SearchQualityProfile.forTier(DeviceTier.AUTO)
    }

    @Test
    fun `resolved tier is never AUTO`() {
        val nonAutoTiers = listOf(DeviceTier.LOW, DeviceTier.BALANCED, DeviceTier.HIGH, DeviceTier.ULTRA)

        nonAutoTiers.forEach { tier ->
            val profile = SearchQualityProfile.forTier(tier)
            assertThat(profile.resolvedTier).isNotEqualTo(DeviceTier.AUTO)
        }
    }

    @Test
    fun `annIndexDimension increases with tier`() {
        val low = SearchQualityProfile.forTier(DeviceTier.LOW)
        val balanced = SearchQualityProfile.forTier(DeviceTier.BALANCED)
        val high = SearchQualityProfile.forTier(DeviceTier.HIGH)
        val ultra = SearchQualityProfile.forTier(DeviceTier.ULTRA)

        assertThat(low.annIndexDimension).isLessThan(balanced.annIndexDimension)
        assertThat(balanced.annIndexDimension).isLessThan(high.annIndexDimension)
        assertThat(high.annIndexDimension).isLessThan(ultra.annIndexDimension)
    }
}
