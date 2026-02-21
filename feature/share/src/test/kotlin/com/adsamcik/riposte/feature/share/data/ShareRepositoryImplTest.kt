package com.adsamcik.riposte.feature.share.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.ml.XmpMetadataHandler
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.ShareConfig
import com.adsamcik.riposte.core.model.SharingPreferences
import com.adsamcik.riposte.feature.share.R
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Unit tests for ShareRepositoryImpl.
 * Uses Robolectric for Android framework mocking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ShareRepositoryImplTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var memeDao: MemeDao
    private lateinit var preferencesDataStore: PreferencesDataStore
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var xmpMetadataHandler: XmpMetadataHandler
    private lateinit var repository: ShareRepositoryImpl

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        memeDao = mockk()
        preferencesDataStore = mockk()
        imageProcessor = mockk()
        xmpMetadataHandler = mockk(relaxed = true)

        every { context.cacheDir } returns tempFolder.root
        every { context.getString(R.string.share_chooser_title) } returns "Send this meme"

        mockkStatic(FileProvider::class)

        repository =
            ShareRepositoryImpl(
                context = context,
                memeDao = memeDao,
                preferencesDataStore = preferencesDataStore,
                imageProcessor = imageProcessor,
                xmpMetadataHandler = xmpMetadataHandler,
            )
    }

    @After
    fun tearDown() {
        unmockkStatic(FileProvider::class)
    }

    // region getMeme Tests

    @Test
    fun `getMeme returns meme when entity exists`() =
        runTest {
            val entity = createTestMemeEntity(1L)
            coEvery { memeDao.getMemeById(1L) } returns entity

            val result = repository.getMeme(1L)

            assertThat(result).isNotNull()
            assertThat(result?.id).isEqualTo(1L)
            assertThat(result?.fileName).isEqualTo("meme_1.jpg")
        }

    @Test
    fun `getMeme returns null when entity does not exist`() =
        runTest {
            coEvery { memeDao.getMemeById(999L) } returns null

            val result = repository.getMeme(999L)

            assertThat(result).isNull()
        }

    // endregion

    // region getDefaultShareConfig Tests

    @Test
    fun `getDefaultShareConfig returns preferences from data store`() =
        runTest {
            val preferences =
                SharingPreferences(
                    defaultFormat = ImageFormat.PNG,
                    defaultQuality = 85,
                    stripMetadata = true,
                )
            every { preferencesDataStore.sharingPreferences } returns flowOf(preferences)

            val result = repository.getDefaultShareConfig()

            assertThat(result.format).isEqualTo(ImageFormat.PNG)
            assertThat(result.quality).isEqualTo(85)
            assertThat(result.stripMetadata).isTrue()
        }

    @Test
    fun `getDefaultShareConfig uses defaults when datastore is empty`() =
        runTest {
            val preferences = SharingPreferences()
            every { preferencesDataStore.sharingPreferences } returns flowOf(preferences)

            val result = repository.getDefaultShareConfig()

            assertThat(result.format).isEqualTo(ImageFormat.JPEG)
            assertThat(result.quality).isEqualTo(85)
        }

    // endregion

    // region createShareIntent Tests

    @Test
    fun `createShareIntent returns chooser intent`() {
        val uri = Uri.parse("content://test/image.jpg")

        val intent = repository.createShareIntent(uri, "image/jpeg")

        assertThat(intent.action).isEqualTo(Intent.ACTION_CHOOSER)
    }

    @Test
    fun `createShareIntent includes localized chooser title`() {
        val uri = Uri.parse("content://test/image.jpg")

        val chooser = repository.createShareIntent(uri, "image/jpeg")

        assertThat(chooser.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString()).isEqualTo("Send this meme")
    }

    @Test
    fun `createShareIntent wraps ACTION_SEND intent with correct type`() {
        val uri = Uri.parse("content://test/image.jpg")

        val chooser = repository.createShareIntent(uri, "image/jpeg")
        val wrapped = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

        assertThat(wrapped.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(wrapped.type).isEqualTo("image/jpeg")
    }

    @Test
    fun `createShareIntent includes URI as extra stream in wrapped intent`() {
        val uri = Uri.parse("content://test/image.png")

        val chooser = repository.createShareIntent(uri, "image/png")
        val wrapped = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

        assertThat(wrapped.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)).isEqualTo(uri)
    }

    @Test
    fun `createShareIntent adds read URI permission flag in wrapped intent`() {
        val uri = Uri.parse("content://test/image.webp")

        val chooser = repository.createShareIntent(uri, "image/webp")
        val wrapped = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

        assertThat(wrapped.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
    }

    // endregion

    // region prepareForSharing Tests

    @Test
    fun `prepareForSharing with valid meme returns success`() =
        runTest {
            val meme = createTestMeme(1L)
            val config = ShareConfig(stripMetadata = false)
            val expectedUri = Uri.parse("content://com.adsamcik.riposte.fileprovider/share.jpg")

            every { imageProcessor.processImage(any(), any(), any()) } returns
                ImageProcessor.ProcessResult.Success(
                    file = File("dummy"),
                    width = 1080,
                    height = 1920,
                    fileSize = 50_000,
                )
            every { FileProvider.getUriForFile(any(), any(), any()) } returns expectedUri

            val result = repository.prepareForSharing(meme, config)

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo(expectedUri)
        }

    @Test
    fun `prepareForSharing when imageProcessor returns Error returns failure`() =
        runTest {
            val meme = createTestMeme(1L)
            val config = ShareConfig.DEFAULT

            every { imageProcessor.processImage(any(), any(), any()) } returns
                ImageProcessor.ProcessResult.Error("Failed to load image")

            val result = repository.prepareForSharing(meme, config)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("Failed to load image")
        }

    @Test
    fun `prepareForSharing with stripMetadata true skips XMP write`() =
        runTest {
            val meme = createTestMeme(1L)
            val config = ShareConfig(stripMetadata = true)
            val expectedUri = Uri.parse("content://com.adsamcik.riposte.fileprovider/share.jpg")

            every { imageProcessor.processImage(any(), any(), any()) } returns
                ImageProcessor.ProcessResult.Success(
                    file = File("dummy"),
                    width = 1080,
                    height = 1920,
                    fileSize = 50_000,
                )
            every { FileProvider.getUriForFile(any(), any(), any()) } returns expectedUri

            val result = repository.prepareForSharing(meme, config)

            assertThat(result.isSuccess).isTrue()
            verify(exactly = 0) { xmpMetadataHandler.writeMetadata(any(), any()) }
        }

    @Test
    fun `prepareForSharing when source file is missing returns failure`() =
        runTest {
            val meme = createTestMeme(1L)
            val config = ShareConfig.DEFAULT

            every { imageProcessor.processImage(any(), any(), any()) } throws
                IOException("Source file not found")

            val result = repository.prepareForSharing(meme, config)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
        }

    // endregion

    // region saveToGallery Tests

    @Test
    fun `saveToGallery with valid meme processes and returns success`() =
        runTest {
            val meme = createTestMeme(1L)
            val config = ShareConfig.DEFAULT
            val galleryUri = Uri.parse("content://media/external/images/media/42")
            val mockResolver = mockk<ContentResolver>()

            every { context.contentResolver } returns mockResolver
            every { mockResolver.insert(any(), any()) } returns galleryUri
            every { mockResolver.openOutputStream(galleryUri) } returns ByteArrayOutputStream()
            every { imageProcessor.processImage(any(), any(), any()) } answers {
                val outputFile = thirdArg<File>()
                outputFile.parentFile?.mkdirs()
                outputFile.writeBytes(byteArrayOf(1, 2, 3))
                ImageProcessor.ProcessResult.Success(
                    file = outputFile,
                    width = 1080,
                    height = 1920,
                    fileSize = 3L,
                )
            }

            val result = repository.saveToGallery(meme, config)

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo(galleryUri)
        }

    @Test
    fun `saveToGallery when MediaStore insert fails returns failure`() =
        runTest {
            val meme = createTestMeme(1L)
            val config = ShareConfig.DEFAULT
            val mockResolver = mockk<ContentResolver>()

            every { context.contentResolver } returns mockResolver
            every { mockResolver.insert(any(), any()) } returns null

            val result = repository.saveToGallery(meme, config)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("Failed to create media entry")
        }

    // endregion

    // region Helper Functions

    private fun createTestMemeEntity(
        id: Long,
        width: Int = 1080,
        height: Int = 1920,
    ): MemeEntity {
        return MemeEntity(
            id = id,
            filePath = "/test/path/meme_$id.jpg",
            fileName = "meme_$id.jpg",
            mimeType = "image/jpeg",
            width = width,
            height = height,
            fileSizeBytes = 100_000,
            importedAt = System.currentTimeMillis(),
            emojiTagsJson = """[{"emoji":"😂","name":"joy"}]""",
            title = "Test Meme $id",
            description = "Description for meme $id",
            textContent = null,
            embedding = null,
            isFavorite = false,
        )
    }

    private fun createTestMeme(
        id: Long,
        width: Int = 1080,
        height: Int = 1920,
    ): Meme {
        return Meme(
            id = id,
            filePath = "/test/path/meme_$id.jpg",
            fileName = "meme_$id.jpg",
            mimeType = "image/jpeg",
            width = width,
            height = height,
            fileSizeBytes = 100_000,
            importedAt = System.currentTimeMillis(),
            emojiTags = listOf(EmojiTag.fromEmoji("😂")),
            title = "Test Meme $id",
            description = "Description for meme $id",
        )
    }

    // endregion
}
