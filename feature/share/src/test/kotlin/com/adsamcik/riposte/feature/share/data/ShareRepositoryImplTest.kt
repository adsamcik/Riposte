package com.adsamcik.riposte.feature.share.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.ml.XmpMetadataHandler
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.ShareConfig
import com.adsamcik.riposte.core.model.SharingPreferences
import com.adsamcik.riposte.core.testing.TestDataFactory
import com.adsamcik.riposte.feature.share.R
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
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
    private lateinit var contentResolver: ContentResolver
    private lateinit var memeDao: MemeDao
    private lateinit var preferencesDataStore: PreferencesDataStore
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var xmpMetadataHandler: XmpMetadataHandler
    private lateinit var repository: ShareRepositoryImpl

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        memeDao = mockk()
        preferencesDataStore = mockk()
        imageProcessor = mockk()
        xmpMetadataHandler = mockk(relaxed = true)

        every { context.cacheDir } returns tempFolder.root
        every { context.contentResolver } returns contentResolver
        every { context.getString(R.string.share_chooser_title) } returns "Send this meme"

        repository =
            ShareRepositoryImpl(
                context = context,
                memeDao = memeDao,
                preferencesDataStore = preferencesDataStore,
                imageProcessor = imageProcessor,
                xmpMetadataHandler = xmpMetadataHandler,
            )
    }

    /**
     * Stub ImageProcessor to actually write bytes to the temp output file so the
     * MediaStore copy stream has something to read. Without this, the inputStream
     * call on a non-existent file throws FileNotFoundException.
     */
    private fun stubProcessImageSuccess() {
        every { imageProcessor.processImage(any(), any(), any()) } answers {
            val outputFile = thirdArg<File>()
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(byteArrayOf(1, 2, 3, 4))
            ImageProcessor.ProcessResult.Success(
                file = outputFile,
                width = 1080,
                height = 1920,
                fileSize = 4L,
            )
        }
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

    @Test
    fun `createShareIntent adds write URI permission flag in wrapped intent`() {
        // Required by Discord's React Native ShareActivity (it re-grants the URI to its
        // upload workers). Without WRITE permission the re-grant throws SecurityException.
        val uri = Uri.parse("content://test/image.webp")

        val chooser = repository.createShareIntent(uri, "image/webp")
        val wrapped = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

        assertThat(wrapped.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION).isNotEqualTo(0)
    }

    @Test
    fun `createShareIntent adds persistable URI permission flag in wrapped intent`() {
        // Lets receivers takePersistableUriPermission so grants survive their process
        // restarts (e.g. during upload retries).
        val uri = Uri.parse("content://test/image.webp")

        val chooser = repository.createShareIntent(uri, "image/webp")
        val wrapped = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

        assertThat(wrapped.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION).isNotEqualTo(0)
    }

    @Test
    fun `createShareIntent propagates URI permission flags onto chooser intent`() {
        // Some launchers / chooser implementations don't propagate flags from the wrapped
        // intent — they must be set on the chooser itself too.
        val uri = Uri.parse("content://test/image.webp")

        val chooser = repository.createShareIntent(uri, "image/webp")

        assertThat(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(chooser.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION).isNotEqualTo(0)
        assertThat(chooser.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION).isNotEqualTo(0)
    }

    // endregion

    // region createMultipleShareIntent Tests

    @Test
    fun `createMultipleShareIntent wraps ACTION_SEND_MULTIPLE intent`() {
        val uris = listOf(Uri.parse("content://test/a.jpg"), Uri.parse("content://test/b.jpg"))

        val chooser = repository.createMultipleShareIntent(uris, "image/*")
        val wrapped = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(wrapped.action).isEqualTo(Intent.ACTION_SEND_MULTIPLE)
        assertThat(wrapped.type).isEqualTo("image/*")
    }

    @Test
    fun `createMultipleShareIntent passes URI list via EXTRA_STREAM array`() {
        // Discord's ShareProps reads via getParcelableArrayListExtra — must be ArrayList.
        val uris = listOf(Uri.parse("content://test/a.jpg"), Uri.parse("content://test/b.jpg"))

        val chooser = repository.createMultipleShareIntent(uris, "image/*")
        val wrapped = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

        val extras = wrapped.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        assertThat(extras).containsExactlyElementsIn(uris).inOrder()
    }

    @Test
    fun `createMultipleShareIntent sets all three URI permission flags`() {
        val uris = listOf(Uri.parse("content://test/a.jpg"))

        val chooser = repository.createMultipleShareIntent(uris, "image/*")
        val wrapped = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

        val expected =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        assertThat(wrapped.flags and expected).isEqualTo(expected)
        assertThat(chooser.flags and expected).isEqualTo(expected)
    }

    // endregion

    // region prepareForSharing Tests (MediaStore-backed)

    @Test
    fun `prepareForSharing with valid meme returns MediaStore content URI`() =
        runTest {
            val meme = TestDataFactory.createMeme(id = 1L)
            val config = ShareConfig(stripMetadata = false)
            val mediaUri = Uri.parse("content://media/external/images/media/42")
            every { contentResolver.insert(any(), any()) } returns mediaUri
            every { contentResolver.openOutputStream(mediaUri) } returns ByteArrayOutputStream()
            every { contentResolver.update(eq(mediaUri), any(), null, null) } returns 1
            every { contentResolver.delete(any(), any(), any()) } returns 0
            stubProcessImageSuccess()

            val result = repository.prepareForSharing(meme, config)

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo(mediaUri)
        }

    @Test
    fun `prepareForSharing inserts under hidden riposte-share relative path`() =
        runTest {
            // The leading "." in .riposte-share keeps the directory hidden from
            // well-behaved gallery apps and file managers.
            val meme = TestDataFactory.createMeme(id = 1L)
            val mediaUri = Uri.parse("content://media/external/images/media/42")
            val insertedValues = slot<ContentValues>()
            every { contentResolver.insert(any(), capture(insertedValues)) } returns mediaUri
            every { contentResolver.openOutputStream(mediaUri) } returns ByteArrayOutputStream()
            every { contentResolver.update(eq(mediaUri), any(), null, null) } returns 1
            every { contentResolver.delete(any(), any(), any()) } returns 0
            stubProcessImageSuccess()

            repository.prepareForSharing(meme, ShareConfig.DEFAULT)

            assertThat(insertedValues.captured.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
                .isEqualTo(ShareRepositoryImpl.SHARE_RELATIVE_PATH)
        }

    @Test
    fun `prepareForSharing inserts with IS_PENDING=1 then publishes via IS_PENDING=0`() =
        runTest {
            // While IS_PENDING=1 the entry is hidden from other apps' MediaStore queries.
            // We flip to 0 only after writing bytes so the receiver never sees a partial file.
            val meme = TestDataFactory.createMeme(id = 1L)
            val mediaUri = Uri.parse("content://media/external/images/media/42")
            val insertedValues = slot<ContentValues>()
            val updatedValues = slot<ContentValues>()
            every { contentResolver.insert(any(), capture(insertedValues)) } returns mediaUri
            every { contentResolver.openOutputStream(mediaUri) } returns ByteArrayOutputStream()
            every { contentResolver.update(eq(mediaUri), capture(updatedValues), null, null) } returns 1
            every { contentResolver.delete(any(), any(), any()) } returns 0
            stubProcessImageSuccess()

            repository.prepareForSharing(meme, ShareConfig.DEFAULT)

            assertThat(insertedValues.captured.getAsInteger(MediaStore.Images.Media.IS_PENDING)).isEqualTo(1)
            assertThat(updatedValues.captured.getAsInteger(MediaStore.Images.Media.IS_PENDING)).isEqualTo(0)
        }

    @Test
    fun `prepareForSharing cleans stale shares before inserting`() =
        runTest {
            // Defense-in-depth: previous shares are dropped before each new one so the
            // MediaStore footprint stays bounded even if app-start cleanup didn't run.
            val meme = TestDataFactory.createMeme(id = 1L)
            val mediaUri = Uri.parse("content://media/external/images/media/42")
            every { contentResolver.insert(any(), any()) } returns mediaUri
            every { contentResolver.openOutputStream(mediaUri) } returns ByteArrayOutputStream()
            every { contentResolver.update(eq(mediaUri), any(), null, null) } returns 1
            every {
                contentResolver.delete(
                    eq(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                    any(),
                    any(),
                )
            } returns 0
            stubProcessImageSuccess()

            repository.prepareForSharing(meme, ShareConfig.DEFAULT)

            verify {
                contentResolver.delete(
                    eq(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                    match<String> { it.contains(MediaStore.MediaColumns.RELATIVE_PATH) },
                    any(),
                )
            }
        }

    @Test
    fun `prepareForSharing when imageProcessor returns Error returns failure`() =
        runTest {
            val meme = TestDataFactory.createMeme(id = 1L)
            val config = ShareConfig.DEFAULT
            every { contentResolver.delete(any(), any(), any()) } returns 0
            every { imageProcessor.processImage(any(), any(), any()) } returns
                ImageProcessor.ProcessResult.Error("Failed to load image")

            val result = repository.prepareForSharing(meme, config)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("Failed to load image")
        }

    @Test
    fun `prepareForSharing when MediaStore insert returns null returns failure`() =
        runTest {
            val meme = TestDataFactory.createMeme(id = 1L)
            every { contentResolver.delete(any(), any(), any()) } returns 0
            every { contentResolver.insert(any(), any()) } returns null
            stubProcessImageSuccess()

            val result = repository.prepareForSharing(meme, ShareConfig.DEFAULT)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("MediaStore")
        }

    @Test
    fun `prepareForSharing deletes MediaStore entry when write stream fails`() =
        runTest {
            // If we can't open the output stream, we must roll back the half-created
            // MediaStore entry — otherwise it lingers as an empty placeholder.
            val meme = TestDataFactory.createMeme(id = 1L)
            val mediaUri = Uri.parse("content://media/external/images/media/42")
            every { contentResolver.insert(any(), any()) } returns mediaUri
            every { contentResolver.openOutputStream(mediaUri) } throws IOException("disk full")
            every { contentResolver.delete(any(), any(), any()) } returns 0
            every { contentResolver.delete(eq(mediaUri), null, null) } returns 1
            stubProcessImageSuccess()

            val result = repository.prepareForSharing(meme, ShareConfig.DEFAULT)

            assertThat(result.isFailure).isTrue()
            verify { contentResolver.delete(eq(mediaUri), null, null) }
        }

    @Test
    fun `prepareForSharing when source file is missing returns failure`() =
        runTest {
            val meme = TestDataFactory.createMeme(id = 1L)
            every { imageProcessor.processImage(any(), any(), any()) } throws
                IOException("Source file not found")
            every { contentResolver.delete(any(), any(), any()) } returns 0

            val result = repository.prepareForSharing(meme, ShareConfig.DEFAULT)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
        }

    // endregion

    // region prepareMultipleForSharing Tests

    @Test
    fun `prepareMultipleForSharing returns one URI per input meme in order`() =
        runTest {
            val memes = listOf(TestDataFactory.createMeme(id = 1L), TestDataFactory.createMeme(id = 2L))
            val uriA = Uri.parse("content://media/external/images/media/100")
            val uriB = Uri.parse("content://media/external/images/media/101")
            every { contentResolver.insert(any(), any()) } returnsMany listOf(uriA, uriB)
            every { contentResolver.openOutputStream(any<Uri>()) } returns ByteArrayOutputStream()
            every { contentResolver.update(any(), any(), null, null) } returns 1
            every { contentResolver.delete(any(), any(), any()) } returns 0
            stubProcessImageSuccess()

            val result = repository.prepareMultipleForSharing(memes, ShareConfig.DEFAULT)

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).containsExactly(uriA, uriB).inOrder()
        }

    @Test
    fun `prepareMultipleForSharing rolls back already-published entries on partial failure`() =
        runTest {
            // The first meme succeeds; the second fails. The first URI must be cleaned up
            // so we don't leave a half-published batch behind in MediaStore.
            val memes = listOf(TestDataFactory.createMeme(id = 1L), TestDataFactory.createMeme(id = 2L))
            val uriA = Uri.parse("content://media/external/images/media/100")
            val uriB = Uri.parse("content://media/external/images/media/101")
            every { contentResolver.insert(any(), any()) } returnsMany listOf(uriA, uriB)
            every { contentResolver.openOutputStream(uriA) } returns ByteArrayOutputStream()
            every { contentResolver.openOutputStream(uriB) } throws IOException("disk full")
            every { contentResolver.update(eq(uriA), any(), null, null) } returns 1
            every { contentResolver.delete(any(), any(), any()) } returns 0
            every { contentResolver.delete(eq(uriA), null, null) } returns 1
            every { contentResolver.delete(eq(uriB), null, null) } returns 1
            stubProcessImageSuccess()

            val result = repository.prepareMultipleForSharing(memes, ShareConfig.DEFAULT)

            assertThat(result.isFailure).isTrue()
            verify { contentResolver.delete(eq(uriA), null, null) }
        }

    @Test
    fun `prepareMultipleForSharing with empty input returns empty success`() =
        runTest {
            val result = repository.prepareMultipleForSharing(emptyList(), ShareConfig.DEFAULT)

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEmpty()
            // Nothing should have been inserted
            verify(exactly = 0) { contentResolver.insert(any(), any()) }
        }

    // endregion

    // region cleanupStaleShares Tests

    @Test
    fun `cleanupStaleShares deletes all entries under SHARE_RELATIVE_PATH`() =
        runTest {
            every {
                contentResolver.delete(
                    eq(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                    any(),
                    any(),
                )
            } returns 3

            val removed = repository.cleanupStaleShares()

            assertThat(removed).isEqualTo(3)
            verify {
                contentResolver.delete(
                    eq(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                    match<String> { it.contains(MediaStore.MediaColumns.RELATIVE_PATH) },
                    match<Array<String>> { it.singleOrNull()?.startsWith(ShareRepositoryImpl.SHARE_RELATIVE_PATH) == true },
                )
            }
        }

    @Test
    fun `cleanupStaleShares returns 0 when MediaStore delete throws`() =
        runTest {
            // Cleanup must never crash the caller — it's best-effort.
            every { contentResolver.delete(any(), any(), any()) } throws RuntimeException("provider gone")

            val removed = repository.cleanupStaleShares()

            assertThat(removed).isEqualTo(0)
        }

    // endregion

    // region saveToGallery Tests

    @Test
    fun `saveToGallery with valid meme processes and returns success`() =
        runTest {
            val meme = TestDataFactory.createMeme(id = 1L)
            val config = ShareConfig.DEFAULT
            val galleryUri = Uri.parse("content://media/external/images/media/42")

            every { contentResolver.insert(any(), any()) } returns galleryUri
            every { contentResolver.openOutputStream(galleryUri) } returns ByteArrayOutputStream()
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
            val meme = TestDataFactory.createMeme(id = 1L)
            val config = ShareConfig.DEFAULT

            every { contentResolver.insert(any(), any()) } returns null

            val result = repository.saveToGallery(meme, config)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("Failed to create media entry")
        }

    @Test
    fun `saveToGallery cleans up temp file when processImage fails`() =
        runTest {
            val meme = TestDataFactory.createMeme(id = 1L)
            val config = ShareConfig.DEFAULT
            val galleryUri = Uri.parse("content://media/external/images/media/42")

            every { contentResolver.insert(any(), any()) } returns galleryUri
            every { contentResolver.openOutputStream(galleryUri) } returns ByteArrayOutputStream()
            every { contentResolver.delete(any(), null, null) } returns 1
            every { imageProcessor.processImage(any(), any(), any()) } answers {
                val outputFile = thirdArg<File>()
                outputFile.parentFile?.mkdirs()
                outputFile.writeBytes(byteArrayOf(1, 2, 3))
                ImageProcessor.ProcessResult.Error("Decode failed")
            }

            val result = repository.saveToGallery(meme, config)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("Decode failed")

            // Verify temp file was cleaned up
            val shareCacheDir = File(tempFolder.root, "share_cache")
            val remainingTempFiles =
                shareCacheDir.listFiles()?.filter { it.name.startsWith("temp_gallery_") } ?: emptyList()
            assertThat(remainingTempFiles).isEmpty()

            // Verify MediaStore entry was also cleaned up
            verify { contentResolver.delete(galleryUri, null, null) }
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

    // endregion
}
