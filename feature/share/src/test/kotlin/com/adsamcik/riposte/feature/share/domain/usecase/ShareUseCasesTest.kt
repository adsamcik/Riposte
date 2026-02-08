package com.adsamcik.riposte.feature.share.domain.usecase

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.ShareConfig
import com.adsamcik.riposte.feature.share.domain.repository.ShareRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ShareUseCasesTest {

    private lateinit var repository: ShareRepository
    private lateinit var shareUseCases: ShareUseCases

    private val testMeme = createTestMeme(1L)
    private val defaultConfig = ShareConfig()
    private val mockUri: Uri = mockk()
    private val mockIntent: Intent = mockk()

    @Before
    fun setup() {
        repository = mockk()
        shareUseCases = ShareUseCases(repository)
    }

    // region getMeme Tests

    @Test
    fun `getMeme returns meme from repository`() = runTest {
        coEvery { repository.getMeme(1L) } returns testMeme

        val result = shareUseCases.getMeme(1L)

        assertThat(result).isEqualTo(testMeme)
        coVerify { repository.getMeme(1L) }
    }

    @Test
    fun `getMeme returns null when meme not found`() = runTest {
        coEvery { repository.getMeme(999L) } returns null

        val result = shareUseCases.getMeme(999L)

        assertThat(result).isNull()
        coVerify { repository.getMeme(999L) }
    }

    @Test
    fun `getMeme with different IDs calls repository with correct ID`() = runTest {
        val meme2 = createTestMeme(2L)
        val meme3 = createTestMeme(3L)

        coEvery { repository.getMeme(2L) } returns meme2
        coEvery { repository.getMeme(3L) } returns meme3

        assertThat(shareUseCases.getMeme(2L)).isEqualTo(meme2)
        assertThat(shareUseCases.getMeme(3L)).isEqualTo(meme3)

        coVerify(exactly = 1) { repository.getMeme(2L) }
        coVerify(exactly = 1) { repository.getMeme(3L) }
    }

    // endregion

    // region getDefaultConfig Tests

    @Test
    fun `getDefaultConfig returns config from repository`() = runTest {
        coEvery { repository.getDefaultShareConfig() } returns defaultConfig

        val result = shareUseCases.getDefaultConfig()

        assertThat(result).isEqualTo(defaultConfig)
        coVerify { repository.getDefaultShareConfig() }
    }

    @Test
    fun `getDefaultConfig returns custom config from repository`() = runTest {
        val customConfig = ShareConfig(
            format = ImageFormat.PNG,
            quality = 100,
            maxWidth = 2000,
            maxHeight = 2000,
            stripMetadata = false,
        )
        coEvery { repository.getDefaultShareConfig() } returns customConfig

        val result = shareUseCases.getDefaultConfig()

        assertThat(result.format).isEqualTo(ImageFormat.PNG)
        assertThat(result.quality).isEqualTo(100)
        assertThat(result.maxWidth).isEqualTo(2000)
        assertThat(result.maxHeight).isEqualTo(2000)
        assertThat(result.stripMetadata).isFalse()
    }

    // endregion

    // region prepareForSharing Tests

    @Test
    fun `prepareForSharing returns success result from repository`() = runTest {
        coEvery { repository.prepareForSharing(testMeme, defaultConfig) } returns Result.success(mockUri)

        val result = shareUseCases.prepareForSharing(testMeme, defaultConfig)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(mockUri)
        coVerify { repository.prepareForSharing(testMeme, defaultConfig) }
    }

    @Test
    fun `prepareForSharing returns failure result from repository`() = runTest {
        val exception = Exception("Sharing failed")
        coEvery { repository.prepareForSharing(testMeme, defaultConfig) } returns Result.failure(exception)

        val result = shareUseCases.prepareForSharing(testMeme, defaultConfig)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isEqualTo(exception)
    }

    @Test
    fun `prepareForSharing passes correct config to repository`() = runTest {
        val customConfig = ShareConfig(
            format = ImageFormat.JPEG,
            quality = 50,
            maxWidth = 500,
            maxHeight = 500,
            stripMetadata = true,
        )
        coEvery { repository.prepareForSharing(testMeme, customConfig) } returns Result.success(mockUri)

        shareUseCases.prepareForSharing(testMeme, customConfig)

        coVerify { repository.prepareForSharing(testMeme, customConfig) }
    }

    // endregion

    // region createShareIntent Tests

    @Test
    fun `createShareIntent returns intent from repository`() {
        every { repository.createShareIntent(mockUri, "image/jpeg") } returns mockIntent

        val result = shareUseCases.createShareIntent(mockUri, "image/jpeg")

        assertThat(result).isEqualTo(mockIntent)
        verify { repository.createShareIntent(mockUri, "image/jpeg") }
    }

    @Test
    fun `createShareIntent passes correct mime type for PNG`() {
        every { repository.createShareIntent(mockUri, "image/png") } returns mockIntent

        shareUseCases.createShareIntent(mockUri, "image/png")

        verify { repository.createShareIntent(mockUri, "image/png") }
    }

    @Test
    fun `createShareIntent passes correct mime type for WEBP`() {
        every { repository.createShareIntent(mockUri, "image/webp") } returns mockIntent

        shareUseCases.createShareIntent(mockUri, "image/webp")

        verify { repository.createShareIntent(mockUri, "image/webp") }
    }

    @Test
    fun `createShareIntent passes correct mime type for GIF`() {
        every { repository.createShareIntent(mockUri, "image/gif") } returns mockIntent

        shareUseCases.createShareIntent(mockUri, "image/gif")

        verify { repository.createShareIntent(mockUri, "image/gif") }
    }

    // endregion

    // region saveToGallery Tests

    @Test
    fun `saveToGallery returns success result from repository`() = runTest {
        coEvery { repository.saveToGallery(testMeme, defaultConfig) } returns Result.success(mockUri)

        val result = shareUseCases.saveToGallery(testMeme, defaultConfig)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(mockUri)
        coVerify { repository.saveToGallery(testMeme, defaultConfig) }
    }

    @Test
    fun `saveToGallery returns failure result from repository`() = runTest {
        val exception = Exception("Gallery save failed")
        coEvery { repository.saveToGallery(testMeme, defaultConfig) } returns Result.failure(exception)

        val result = shareUseCases.saveToGallery(testMeme, defaultConfig)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isEqualTo(exception)
    }

    @Test
    fun `saveToGallery passes correct config to repository`() = runTest {
        val highQualityConfig = ShareConfig(
            format = ImageFormat.PNG,
            quality = 100,
            maxWidth = null,
            maxHeight = null,
            stripMetadata = false,
        )
        coEvery { repository.saveToGallery(testMeme, highQualityConfig) } returns Result.success(mockUri)

        shareUseCases.saveToGallery(testMeme, highQualityConfig)

        coVerify { repository.saveToGallery(testMeme, highQualityConfig) }
    }

    // endregion

    // region estimateFileSize Tests

    @Test
    fun `estimateFileSize returns size from repository`() = runTest {
        coEvery { repository.estimateFileSize(testMeme, defaultConfig) } returns 50_000L

        val result = shareUseCases.estimateFileSize(testMeme, defaultConfig)

        assertThat(result).isEqualTo(50_000L)
        coVerify { repository.estimateFileSize(testMeme, defaultConfig) }
    }

    @Test
    fun `estimateFileSize returns different sizes for different configs`() = runTest {
        val jpegConfig = ShareConfig(format = ImageFormat.JPEG, quality = 80)
        val pngConfig = ShareConfig(format = ImageFormat.PNG, quality = 100)

        coEvery { repository.estimateFileSize(testMeme, jpegConfig) } returns 30_000L
        coEvery { repository.estimateFileSize(testMeme, pngConfig) } returns 100_000L

        val jpegSize = shareUseCases.estimateFileSize(testMeme, jpegConfig)
        val pngSize = shareUseCases.estimateFileSize(testMeme, pngConfig)

        assertThat(jpegSize).isEqualTo(30_000L)
        assertThat(pngSize).isEqualTo(100_000L)
    }

    @Test
    fun `estimateFileSize returns small size for low quality JPEG`() = runTest {
        val lowQualityConfig = ShareConfig(format = ImageFormat.JPEG, quality = 20)
        coEvery { repository.estimateFileSize(testMeme, lowQualityConfig) } returns 10_000L

        val result = shareUseCases.estimateFileSize(testMeme, lowQualityConfig)

        assertThat(result).isEqualTo(10_000L)
    }

    // endregion

    // region Helper Functions

    private fun createTestMeme(id: Long): Meme {
        return Meme(
            id = id,
            filePath = "/test/path/meme_$id.jpg",
            fileName = "meme_$id.jpg",
            mimeType = "image/jpeg",
            width = 1080,
            height = 1920,
            fileSizeBytes = 100_000,
            importedAt = System.currentTimeMillis(),
            emojiTags = listOf(EmojiTag.fromEmoji("😂")),
            title = "Test Meme $id",
            description = "Description for meme $id",
            textContent = null,
            isFavorite = false,
        )
    }

    // endregion
}
