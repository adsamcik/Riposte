package com.adsamcik.riposte.core.common.share

import android.content.Intent
import android.net.Uri
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.ShareConfig
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ShareMemeUseCaseTest {
    private lateinit var repository: ShareRepository
    private lateinit var useCase: ShareMemeUseCase

    private val testMeme =
        Meme(
            id = 1L,
            filePath = "/test/meme.jpg",
            fileName = "meme.jpg",
            mimeType = "image/jpeg",
            width = 1080,
            height = 1080,
            fileSizeBytes = 102400L,
            importedAt = System.currentTimeMillis(),
            emojiTags = emptyList(),
        )

    private val testConfig =
        ShareConfig(
            format = ImageFormat.JPEG,
            quality = 85,
            maxWidth = 1080,
            maxHeight = 1080,
            stripMetadata = true,
        )

    private val testUri: Uri = mockk()
    private val testIntent: Intent = Intent()

    @Before
    fun setup() {
        repository = mockk()
        useCase = ShareMemeUseCase(repository)

        coEvery { repository.getMeme(1L) } returns testMeme
        coEvery { repository.getDefaultShareConfig() } returns testConfig
        coEvery { repository.prepareForSharing(testMeme, testConfig) } returns Result.success(testUri)
        every { repository.createShareIntent(testUri, "image/jpeg") } returns testIntent
    }

    @Test
    fun `returns share intent on success`() =
        runTest {
            val result = useCase(1L)

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo(testIntent)
        }

    @Test
    fun `calls repository methods in correct order`() =
        runTest {
            useCase(1L)

            coVerify(ordering = io.mockk.Ordering.ORDERED) {
                repository.getMeme(1L)
                repository.getDefaultShareConfig()
                repository.prepareForSharing(testMeme, testConfig)
                repository.createShareIntent(testUri, "image/jpeg")
            }
        }

    @Test
    fun `returns failure when meme not found`() =
        runTest {
            coEvery { repository.getMeme(99L) } returns null

            val result = useCase(99L)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
            assertThat(result.exceptionOrNull()?.message).contains("Meme not found: 99")
        }

    @Test
    fun `returns failure when image processing fails`() =
        runTest {
            val error = RuntimeException("Processing failed")
            coEvery { repository.prepareForSharing(testMeme, testConfig) } returns Result.failure(error)

            val result = useCase(1L)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("Processing failed")
        }

    @Test
    fun `does not call createShareIntent when processing fails`() =
        runTest {
            coEvery { repository.prepareForSharing(testMeme, testConfig) } returns
                Result.failure(RuntimeException("fail"))

            useCase(1L)

            coVerify(exactly = 0) { repository.createShareIntent(any(), any()) }
        }

    @Test
    fun `uses mime type from share config format`() =
        runTest {
            val pngConfig = testConfig.copy(format = ImageFormat.PNG)
            coEvery { repository.getDefaultShareConfig() } returns pngConfig
            coEvery { repository.prepareForSharing(testMeme, pngConfig) } returns Result.success(testUri)
            every { repository.createShareIntent(testUri, "image/png") } returns testIntent

            useCase(1L)

            coVerify { repository.createShareIntent(testUri, "image/png") }
        }
}
