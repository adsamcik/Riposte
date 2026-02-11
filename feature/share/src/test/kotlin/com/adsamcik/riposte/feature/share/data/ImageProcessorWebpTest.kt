package com.adsamcik.riposte.feature.share.data

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], manifest = Config.NONE)
class ImageProcessorWebpTest {
    @Test
    fun `WEBP format uses WEBP_LOSSY on API 31`() {
        // After minSdk 31 bump, WEBP_LOSSY should be used directly without version check
        assertThat(Bitmap.CompressFormat.WEBP_LOSSY).isNotNull()
    }
}
