package com.mememymood.feature.import_feature.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.database.dao.EmojiTagDao
import com.mememymood.core.database.dao.MemeDao
import com.mememymood.core.database.entity.MemeEntity
import com.mememymood.core.ml.EmbeddingManager
import com.mememymood.core.ml.TextRecognizer
import com.mememymood.core.ml.XmpMetadataHandler
import com.mememymood.core.model.MemeMetadata
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Unit tests for [ImportRepositoryImpl].
 *
 * Uses Robolectric for Android Context and file system operations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ImportRepositoryImplTest {

    @MockK
    private lateinit var memeDao: MemeDao

    @MockK
    private lateinit var emojiTagDao: EmojiTagDao

    @MockK
    private lateinit var textRecognizer: TextRecognizer

    @MockK
    private lateinit var embeddingManager: EmbeddingManager

    @MockK
    private lateinit var xmpMetadataHandler: XmpMetadataHandler

    private lateinit var context: Context
    private lateinit var repository: ImportRepositoryImpl

    // 1x1 red JPEG bytes for testing image operations
    private val testJpegBytes = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46, 0x49,
        0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0xFF.toByte(),
        0xDB.toByte(), 0x00, 0x43, 0x00, 0x08, 0x06, 0x06, 0x07, 0x06, 0x05, 0x08, 0x07, 0x07,
        0x07, 0x09, 0x09, 0x08, 0x0A, 0x0C, 0x14, 0x0D, 0x0C, 0x0B, 0x0B, 0x0C, 0x19, 0x12,
        0x13, 0x0F, 0x14, 0x1D, 0x1A, 0x1F, 0x1E, 0x1D, 0x1A, 0x1C, 0x1C, 0x20, 0x24, 0x2E,
        0x27, 0x20, 0x22, 0x2C, 0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29, 0x2C, 0x30, 0x31, 0x34,
        0x34, 0x34, 0x1F, 0x27, 0x39, 0x3D, 0x38, 0x32, 0x3C, 0x2E, 0x33, 0x34, 0x32,
        0xFF.toByte(), 0xC0.toByte(), 0x00, 0x0B, 0x08, 0x00, 0x01, 0x00, 0x01, 0x01, 0x01, 0x11,
        0x00, 0xFF.toByte(), 0xC4.toByte(), 0x00, 0x1F, 0x00, 0x00, 0x01, 0x05, 0x01, 0x01, 0x01,
        0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04,
        0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0xFF.toByte(), 0xC4.toByte(), 0x00, 0xB5.toByte(),
        0x10, 0x00, 0x02, 0x01, 0x03, 0x03, 0x02, 0x04, 0x03, 0x05, 0x05, 0x04, 0x04, 0x00, 0x00,
        0x01, 0x7D, 0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13,
        0x51, 0x61, 0x07, 0x22, 0x71, 0x14, 0x32, 0x81.toByte(), 0x91.toByte(), 0xA1.toByte(), 0x08,
        0x23, 0x42, 0xB1.toByte(), 0xC1.toByte(), 0x15, 0x52, 0xD1.toByte(), 0xF0.toByte(), 0x24, 0x33,
        0x62, 0x72, 0x82.toByte(), 0x09, 0x0A, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x25, 0x26, 0x27, 0x28,
        0x29, 0x2A, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
        0x49, 0x4A, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x63, 0x64, 0x65, 0x66, 0x67,
        0x68, 0x69, 0x6A, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x83.toByte(),
        0x84.toByte(), 0x85.toByte(), 0x86.toByte(), 0x87.toByte(), 0x88.toByte(), 0x89.toByte(),
        0x8A.toByte(), 0x92.toByte(), 0x93.toByte(), 0x94.toByte(), 0x95.toByte(), 0x96.toByte(),
        0x97.toByte(), 0x98.toByte(), 0x99.toByte(), 0x9A.toByte(), 0xA2.toByte(), 0xA3.toByte(),
        0xA4.toByte(), 0xA5.toByte(), 0xA6.toByte(), 0xA7.toByte(), 0xA8.toByte(), 0xA9.toByte(),
        0xAA.toByte(), 0xB2.toByte(), 0xB3.toByte(), 0xB4.toByte(), 0xB5.toByte(), 0xB6.toByte(),
        0xB7.toByte(), 0xB8.toByte(), 0xB9.toByte(), 0xBA.toByte(), 0xC2.toByte(), 0xC3.toByte(),
        0xC4.toByte(), 0xC5.toByte(), 0xC6.toByte(), 0xC7.toByte(), 0xC8.toByte(), 0xC9.toByte(),
        0xCA.toByte(), 0xD2.toByte(), 0xD3.toByte(), 0xD4.toByte(), 0xD5.toByte(), 0xD6.toByte(),
        0xD7.toByte(), 0xD8.toByte(), 0xD9.toByte(), 0xDA.toByte(), 0xE1.toByte(), 0xE2.toByte(),
        0xE3.toByte(), 0xE4.toByte(), 0xE5.toByte(), 0xE6.toByte(), 0xE7.toByte(), 0xE8.toByte(),
        0xE9.toByte(), 0xEA.toByte(), 0xF1.toByte(), 0xF2.toByte(), 0xF3.toByte(), 0xF4.toByte(),
        0xF5.toByte(), 0xF6.toByte(), 0xF7.toByte(), 0xF8.toByte(), 0xF9.toByte(), 0xFA.toByte(),
        0xFF.toByte(), 0xDA.toByte(), 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F, 0x00,
        0xFB.toByte(), 0xD5.toByte(), 0xDB.toByte(), 0x00, 0x31, 0xFF.toByte(), 0xD9.toByte(),
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        context = RuntimeEnvironment.getApplication().applicationContext

        repository = ImportRepositoryImpl(
            context = context,
            memeDao = memeDao,
            emojiTagDao = emojiTagDao,
            textRecognizer = textRecognizer,
            embeddingManager = embeddingManager,
            xmpMetadataHandler = xmpMetadataHandler,
        )
    }

    // region Import Tests

    @Test
    fun `importImage returns success when import succeeds`() = runTest {
        // Arrange
        val uri = createTestImageUri()
        val memeId = 1L
        val memeEntitySlot = slot<MemeEntity>()

        coEvery { memeDao.insertMeme(capture(memeEntitySlot)) } returns memeId
        coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns "Sample text"
        coEvery { embeddingManager.generateAndStoreEmbedding(any(), any()) } returns true

        // Act
        val result = repository.importImage(uri, null)

        // Assert
        assertThat(result.isSuccess).isTrue()
        val meme = result.getOrNull()
        assertThat(meme).isNotNull()
        assertThat(meme?.id).isEqualTo(memeId)

        coVerify { memeDao.insertMeme(any()) }
    }

    @Test
    fun `importImage uses metadata when provided`() = runTest {
        // Arrange
        val uri = createTestImageUri()
        val metadata = MemeMetadata(
            schemaVersion = "1.1",
            emojis = listOf("😂", "🔥"),
            title = "Test Meme",
            description = "A test description",
            tags = listOf("funny", "test"),
            textContent = "Pre-extracted text",
        )
        val memeId = 2L
        val memeEntitySlot = slot<MemeEntity>()

        coEvery { memeDao.insertMeme(capture(memeEntitySlot)) } returns memeId

        // Act
        val result = repository.importImage(uri, metadata)

        // Assert
        assertThat(result.isSuccess).isTrue()

        val capturedEntity = memeEntitySlot.captured
        assertThat(capturedEntity.title).isEqualTo("Test Meme")
        assertThat(capturedEntity.description).isEqualTo("A test description")
        assertThat(capturedEntity.textContent).isEqualTo("Pre-extracted text")
        assertThat(capturedEntity.emojiTagsJson).contains("😂")
        assertThat(capturedEntity.emojiTagsJson).contains("🔥")

        coVerify { emojiTagDao.insertEmojiTags(any()) }
    }

    @Test
    fun `importImage generates embedding when text is available`() = runTest {
        // Arrange
        val uri = createTestImageUri()
        val memeId = 3L

        coEvery { memeDao.insertMeme(any()) } returns memeId
        coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns "Extracted text from image"

        // Act
        val result = repository.importImage(uri, null)

        // Assert
        assertThat(result.isSuccess).isTrue()
        coVerify { embeddingManager.generateAndStoreEmbedding(memeId, any()) }
    }

    @Test
    fun `importImage returns failure when database insert fails`() = runTest {
        // Arrange
        val uri = createTestImageUri()
        val exception = RuntimeException("Database error")

        coEvery { memeDao.insertMeme(any()) } throws exception

        // Act
        val result = repository.importImage(uri, null)

        // Assert
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun `importImages returns results for all uris`() = runTest {
        // Arrange
        val uri1 = createTestImageUri()
        val uri2 = createTestImageUri()

        coEvery { memeDao.insertMeme(any()) } returnsMany listOf(1L, 2L)

        // Act
        val results = repository.importImages(listOf(uri1, uri2))

        // Assert
        assertThat(results).hasSize(2)
        assertThat(results[0].isSuccess).isTrue()
        assertThat(results[1].isSuccess).isTrue()
    }

    // endregion

    // region Metadata Extraction Tests

    @Test
    fun `extractMetadata delegates to xmpMetadataHandler`() = runTest {
        // Arrange
        val uri = mockk<Uri>()
        val expectedMetadata = MemeMetadata(
            schemaVersion = "1.1",
            emojis = listOf("😎"),
        )

        coEvery { xmpMetadataHandler.readMetadata(uri) } returns expectedMetadata

        // Act
        val result = repository.extractMetadata(uri)

        // Assert
        assertThat(result).isEqualTo(expectedMetadata)
        coVerify { xmpMetadataHandler.readMetadata(uri) }
    }

    @Test
    fun `extractMetadata returns null when no metadata exists`() = runTest {
        // Arrange
        val uri = mockk<Uri>()
        coEvery { xmpMetadataHandler.readMetadata(uri) } returns null

        // Act
        val result = repository.extractMetadata(uri)

        // Assert
        assertThat(result).isNull()
    }

    // endregion

    // region Text Extraction Tests

    @Test
    fun `extractText uses textRecognizer`() = runTest {
        // Arrange
        val uri = createTestImageUri()
        coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns "Detected text"

        // Act
        val result = repository.extractText(uri)

        // Assert
        assertThat(result).isEqualTo("Detected text")
    }

    @Test
    fun `extractText returns null on error`() = runTest {
        // Arrange
        val uri = mockk<Uri>()
        val contentResolver = mockk<ContentResolver>()

        // Mock a Uri that will fail to load
        every { uri.scheme } returns "content"
        coEvery { textRecognizer.recognizeText(any<Bitmap>()) } throws RuntimeException("OCR failed")

        // Act - use a Uri that will cause null bitmap
        val result = repository.extractText(uri)

        // Assert
        assertThat(result).isNull()
    }

    // endregion

    // region Emoji Suggestion Tests

    @Test
    fun `suggestEmojis returns non-empty list`() = runTest {
        // Arrange
        val uri = createTestImageUri()
        // Return text containing keywords that match emoji names (e.g., "joy" matches "face_with_tears_of_joy")
        coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns "joy laughing fire"

        // Act
        val result = repository.suggestEmojis(uri)

        // Assert
        assertThat(result).isNotEmpty()
    }

    // endregion

    // region Helper Methods

    /**
     * Creates a test image URI by writing test JPEG bytes to a temp file.
     *
     * Note: This creates a file:// URI for testing. In production,
     * content:// URIs would be used.
     */
    private fun createTestImageUri(): Uri {
        val tempFile = File(context.cacheDir, "test_image_${System.currentTimeMillis()}.jpg")
        tempFile.writeBytes(testJpegBytes)
        return Uri.fromFile(tempFile)
    }

    // endregion
}
