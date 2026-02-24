package com.adsamcik.riposte.core.ml

import android.app.ActivityManager
import android.content.Context
import com.adsamcik.riposte.core.model.DeviceTier
import com.adsamcik.riposte.core.model.SearchQualityProfile
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Before
import org.junit.Test

class DeviceTierDetectorTest {

    private lateinit var context: Context
    private lateinit var activityManager: ActivityManager

    @Before
    fun setUp() {
        activityManager = mockk()
        context = mockk {
            every { getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        }
    }

    private fun setupRam(ramMb: Long) {
        val memInfoSlot = slot<ActivityManager.MemoryInfo>()
        every { activityManager.getMemoryInfo(capture(memInfoSlot)) } answers {
            memInfoSlot.captured.totalMem = ramMb * 1024L * 1024L
        }
    }

    private fun createDetector(): DeviceTierDetector =
        DeviceTierDetector(context)

    // region Auto-detection

    @Test
    fun `detectTier returns LOW for small RAM`() {
        setupRam(2_000L)
        val detector = createDetector()

        assertThat(detector.detectTier()).isEqualTo(DeviceTier.LOW)
    }

    @Test
    fun `detectTier returns BALANCED for mid RAM`() {
        setupRam(6_000L)
        val detector = createDetector()

        assertThat(detector.detectTier()).isEqualTo(DeviceTier.BALANCED)
    }

    @Test
    fun `detectTier returns BALANCED for high RAM without known SoC`() {
        setupRam(10_000L)
        val detector = createDetector()

        // Without a known high-end SoC, 10GB RAM defaults to BALANCED
        assertThat(detector.detectTier()).isEqualTo(DeviceTier.BALANCED)
    }

    @Test
    fun `detectTier returns LOW for 3GB RAM`() {
        setupRam(3_500L)
        val detector = createDetector()

        assertThat(detector.detectTier()).isEqualTo(DeviceTier.LOW)
    }

    @Test
    fun `detectTier returns BALANCED for exactly 4GB RAM`() {
        setupRam(4_000L)
        val detector = createDetector()

        assertThat(detector.detectTier()).isEqualTo(DeviceTier.BALANCED)
    }

    // endregion

    // region resolveProfile

    @Test
    fun `resolveProfile returns LOW profile for small RAM device`() {
        setupRam(2_000L)
        val detector = createDetector()

        val profile = detector.resolveProfile()

        assertThat(profile.resolvedTier).isEqualTo(DeviceTier.LOW)
        assertThat(profile.useAnn).isFalse()
        assertThat(profile.annIndexDimension).isEqualTo(128)
    }

    @Test
    fun `resolveProfile returns BALANCED profile for mid RAM device`() {
        setupRam(6_000L)
        val detector = createDetector()

        val profile = detector.resolveProfile()

        assertThat(profile.resolvedTier).isEqualTo(DeviceTier.BALANCED)
        assertThat(profile.useAnn).isTrue()
        assertThat(profile.annIndexDimension).isEqualTo(256)
    }

    @Test
    fun `resolveProfile respects user override`() {
        setupRam(2_000L)
        val detector = createDetector()

        val profile = detector.resolveProfile(userOverride = DeviceTier.ULTRA)

        assertThat(profile.resolvedTier).isEqualTo(DeviceTier.ULTRA)
        assertThat(profile.annIndexDimension).isEqualTo(768)
    }

    @Test
    fun `resolveProfile caches auto-detected result`() {
        setupRam(6_000L)
        val detector = createDetector()

        val first = detector.resolveProfile()
        val second = detector.resolveProfile()

        assertThat(first).isSameInstanceAs(second)
    }

    @Test
    fun `resolveProfile does not cache user override`() {
        setupRam(6_000L)
        val detector = createDetector()

        val overrideProfile = detector.resolveProfile(userOverride = DeviceTier.HIGH)
        val autoProfile = detector.resolveProfile()

        assertThat(overrideProfile.resolvedTier).isEqualTo(DeviceTier.HIGH)
        assertThat(autoProfile.resolvedTier).isEqualTo(DeviceTier.BALANCED)
    }

    // endregion

    // region Profile values

    @Test
    fun `forTier LOW disables ANN`() {
        val profile = SearchQualityProfile.forTier(DeviceTier.LOW)

        assertThat(profile.useAnn).isFalse()
        assertThat(profile.annFirstPassK).isEqualTo(0)
    }

    @Test
    fun `forTier BALANCED uses 256d with F16`() {
        val profile = SearchQualityProfile.forTier(DeviceTier.BALANCED)

        assertThat(profile.annIndexDimension).isEqualTo(256)
        assertThat(profile.useF16Index).isTrue()
    }

    @Test
    fun `forTier HIGH uses 384d without F16`() {
        val profile = SearchQualityProfile.forTier(DeviceTier.HIGH)

        assertThat(profile.annIndexDimension).isEqualTo(384)
        assertThat(profile.useF16Index).isFalse()
    }

    @Test
    fun `forTier ULTRA uses full 768d`() {
        val profile = SearchQualityProfile.forTier(DeviceTier.ULTRA)

        assertThat(profile.annIndexDimension).isEqualTo(768)
        assertThat(profile.annFirstPassK).isEqualTo(200)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `forTier AUTO throws IllegalArgumentException`() {
        SearchQualityProfile.forTier(DeviceTier.AUTO)
    }

    // endregion
}
