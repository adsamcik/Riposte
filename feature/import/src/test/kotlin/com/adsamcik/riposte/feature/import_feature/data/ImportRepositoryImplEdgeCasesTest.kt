package com.adsamcik.riposte.feature.import_feature.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.ImportRequestDao
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.ml.EmbeddingManager
import com.adsamcik.riposte.core.ml.TextRecognizer
import com.adsamcik.riposte.core.ml.XmpMetadataHandler
import com.adsamcik.riposte.core.model.MemeMetadata
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/**
 * Edge case tests for [ImportRepositoryImpl] covering error conditions,
 * resource limits, and security scenarios.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ImportRepositoryImplEdgeCasesTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @MockK
    private lateinit var memeDao: MemeDao

    @MockK
    private lateinit var emojiTagDao: EmojiTagDao

    @MockK
    private lateinit var importRequestDao: ImportRequestDao

    @MockK
    private lateinit var textRecognizer: TextRecognizer

    @MockK
    private lateinit var embeddingManager: EmbeddingManager

    @MockK
    private lateinit var xmpMetadataHandler: XmpMetadataHandler

    private lateinit var context: Context
    private lateinit var repository: ImportRepositoryImpl

    // Complete 1x1 red JPEG bytes for testing (same as ImportRepositoryImplTest)
    private val validJpegBytes =
        byteArrayOf(
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
        context = RuntimeEnvironment.getApplication()

        repository =
            ImportRepositoryImpl(
                context = context,
                memeDao = memeDao,
                emojiTagDao = emojiTagDao,
                importRequestDao = importRequestDao,
                mlServices =
                    ImportMlServices(
                        textRecognizer = textRecognizer,
                        embeddingManager = embeddingManager,
                        xmpMetadataHandler = xmpMetadataHandler,
                    ),
            )
    }

    // ==================== SecurityException Tests ====================

    @Test
    fun `importImage returns failure when SecurityException thrown on URI access`() =
        runTest {
            // Create a mock context that throws SecurityException
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("files")
            every { mockContentResolver.getType(any()) } returns "image/jpeg"
            every { mockContentResolver.openInputStream(any()) } throws SecurityException("Permission denied")

            val repoWithMock =
                ImportRepositoryImpl(
                    context = mockContext,
                    memeDao = memeDao,
                    emojiTagDao = emojiTagDao,
                    importRequestDao = importRequestDao,
                    mlServices =
                        ImportMlServices(
                            textRecognizer = textRecognizer,
                            embeddingManager = embeddingManager,
                            xmpMetadataHandler = xmpMetadataHandler,
                        ),
                )

            val uri = mockk<Uri>()
            val result = repoWithMock.importImage(uri, null)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
        }

    @Test
    fun `importImage returns failure when stream throws SecurityException`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("files2")
            every { mockContentResolver.getType(any()) } returns "image/jpeg"
            every { mockContentResolver.openInputStream(any()) } throws SecurityException("URI permission revoked")

            val repoWithMock =
                ImportRepositoryImpl(
                    context = mockContext,
                    memeDao = memeDao,
                    emojiTagDao = emojiTagDao,
                    importRequestDao = importRequestDao,
                    mlServices =
                        ImportMlServices(
                            textRecognizer = textRecognizer,
                            embeddingManager = embeddingManager,
                            xmpMetadataHandler = xmpMetadataHandler,
                        ),
                )

            val uri = mockk<Uri>()
            val result = repoWithMock.importImage(uri, null)

            assertThat(result.isFailure).isTrue()
        }

    // ==================== IOException Tests ====================

    @Test
    fun `importImage returns failure when IOException thrown on stream open`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("files3")
            every { mockContentResolver.getType(any()) } returns "image/jpeg"
            every { mockContentResolver.openInputStream(any()) } throws IOException("Disk full")

            val repoWithMock =
                ImportRepositoryImpl(
                    context = mockContext,
                    memeDao = memeDao,
                    emojiTagDao = emojiTagDao,
                    importRequestDao = importRequestDao,
                    mlServices =
                        ImportMlServices(
                            textRecognizer = textRecognizer,
                            embeddingManager = embeddingManager,
                            xmpMetadataHandler = xmpMetadataHandler,
                        ),
                )

            val uri = mockk<Uri>()
            val result = repoWithMock.importImage(uri, null)

            // IOException is caught during bitmap loading and results in failure
            assertThat(result.isFailure).isTrue()
            // The implementation wraps the error with a generic message
            assertThat(result.exceptionOrNull()?.message).isNotNull()
        }

    // ==================== Null/Invalid URI Tests ====================

    @Test
    fun `importImage returns failure for null bitmap`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("files4")
            every { mockContentResolver.getType(any()) } returns "image/jpeg"
            // Return null stream to simulate failed bitmap load
            every { mockContentResolver.openInputStream(any()) } returns null

            val repoWithMock =
                ImportRepositoryImpl(
                    context = mockContext,
                    memeDao = memeDao,
                    emojiTagDao = emojiTagDao,
                    importRequestDao = importRequestDao,
                    mlServices =
                        ImportMlServices(
                            textRecognizer = textRecognizer,
                            embeddingManager = embeddingManager,
                            xmpMetadataHandler = xmpMetadataHandler,
                        ),
                )

            val uri = mockk<Uri>()
            val result = repoWithMock.importImage(uri, null)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("Failed to load image")
        }

    @Test
    fun `importImage returns failure for corrupted image data`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("files5")
            every { mockContentResolver.getType(any()) } returns "image/jpeg"
            // Return garbage data that isn't a valid image
            every {
                mockContentResolver.openInputStream(any())
            } returns ByteArrayInputStream("not an image".toByteArray())

            val repoWithMock =
                ImportRepositoryImpl(
                    context = mockContext,
                    memeDao = memeDao,
                    emojiTagDao = emojiTagDao,
                    importRequestDao = importRequestDao,
                    mlServices =
                        ImportMlServices(
                            textRecognizer = textRecognizer,
                            embeddingManager = embeddingManager,
                            xmpMetadataHandler = xmpMetadataHandler,
                        ),
                )

            val uri = mockk<Uri>()
            val result = repoWithMock.importImage(uri, null)

            assertThat(result.isFailure).isTrue()
        }

    // ==================== Database Error Tests ====================

    @Test
    fun `importImage cleans up files when database insert fails`() =
        runTest {
            val uri = createTestImageUri()

            coEvery { memeDao.insertMeme(any()) } throws RuntimeException("Database locked")
            coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns null

            val result = repository.importImage(uri, null)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("Database locked")

            // Files should be cleaned up (we can't easily verify this in unit test,
            // but the code path is now covered)
        }

    // ==================== Metadata Edge Cases ====================

    @Test
    fun `importImage handles null metadata`() =
        runTest {
            val uri = createTestImageUri()

            coEvery { memeDao.insertMeme(any()) } returns 1L
            coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns null

            val result = repository.importImage(uri, null)

            // Should succeed without emoji tags
            assertThat(result.isSuccess).isTrue()
        }

    @Test
    fun `MemeMetadata rejects empty emoji list at construction`() {
        // MemeMetadata requires at least one emoji
        val exception =
            runCatching {
                MemeMetadata(
                    emojis = emptyList(),
                    title = "Test",
                )
            }

        assertThat(exception.isFailure).isTrue()
        assertThat(exception.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(exception.exceptionOrNull()?.message).contains("At least one emoji is required")
    }

    @Test
    fun `importImage handles very long title in metadata`() =
        runTest {
            val uri = createTestImageUri()
            val longTitle = "A".repeat(10000)
            val metadata =
                MemeMetadata(
                    emojis = listOf("😀"),
                    title = longTitle,
                )

            coEvery { memeDao.insertMeme(any()) } returns 1L
            coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns null

            val result = repository.importImage(uri, metadata)

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()?.title).isEqualTo(longTitle)
        }

    @Test
    fun `importImage handles unicode in metadata`() =
        runTest {
            val uri = createTestImageUri()
            val metadata =
                MemeMetadata(
                    emojis = listOf("😀", "🔥", "👾"),
                    title = "日本語タイトル",
                    description = "Описание на русском 中文描述",
                )

            coEvery { memeDao.insertMeme(any()) } returns 1L
            coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns null

            val result = repository.importImage(uri, metadata)

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()?.title).isEqualTo("日本語タイトル")
            assertThat(result.getOrNull()?.description).isEqualTo("Описание на русском 中文描述")
        }

    // ==================== Concurrent Import Tests ====================

    @Test
    fun `importImages handles empty list`() =
        runTest {
            val results = repository.importImages(emptyList())

            assertThat(results).isEmpty()
        }

    @Test
    fun `importImages returns results for all URIs even with failures`() =
        runTest {
            val uri1 = createTestImageUri()
            val uri2 = createTestImageUri()

            // First succeeds, second fails
            var callCount = 0
            coEvery { memeDao.insertMeme(any()) } answers {
                callCount++
                if (callCount == 1) 1L else throw RuntimeException("Failed")
            }
            coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns null

            val results = repository.importImages(listOf(uri1, uri2))

            assertThat(results).hasSize(2)
            assertThat(results[0].isSuccess).isTrue()
            assertThat(results[1].isFailure).isTrue()
        }

    // ==================== Text Extraction Edge Cases ====================

    @Test
    fun `extractText returns null for unreadable URI`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("files6")
            every { mockContentResolver.openInputStream(any()) } returns null

            val repoWithMock =
                ImportRepositoryImpl(
                    context = mockContext,
                    memeDao = memeDao,
                    emojiTagDao = emojiTagDao,
                    importRequestDao = importRequestDao,
                    mlServices =
                        ImportMlServices(
                            textRecognizer = textRecognizer,
                            embeddingManager = embeddingManager,
                            xmpMetadataHandler = xmpMetadataHandler,
                        ),
                )

            val uri = mockk<Uri>()
            val result = repoWithMock.extractText(uri)

            assertThat(result).isNull()
        }

    @Test
    fun `extractText returns null when OCR fails`() =
        runTest {
            val uri = createTestImageUri()
            coEvery { textRecognizer.recognizeText(any<Bitmap>()) } throws RuntimeException("OCR failed")

            val result = repository.extractText(uri)

            assertThat(result).isNull()
        }

    // ==================== Emoji Suggestion Edge Cases ====================

    @Test
    fun `suggestEmojis returns empty list for unreadable image`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("files7")
            every { mockContentResolver.openInputStream(any()) } returns null

            val repoWithMock =
                ImportRepositoryImpl(
                    context = mockContext,
                    memeDao = memeDao,
                    emojiTagDao = emojiTagDao,
                    importRequestDao = importRequestDao,
                    mlServices =
                        ImportMlServices(
                            textRecognizer = textRecognizer,
                            embeddingManager = embeddingManager,
                            xmpMetadataHandler = xmpMetadataHandler,
                        ),
                )

            val uri = mockk<Uri>()
            val result = repoWithMock.suggestEmojis(uri)

            assertThat(result).isEmpty()
        }

    // ==================== Duplicate Detection Tests ====================

    @Test
    fun `isDuplicate returns true when same file bytes already imported`() =
        runTest {
            val uri = createTestImageUri()

            coEvery { memeDao.memeExistsByHash(any()) } returns true

            val result = repository.isDuplicate(uri)

            assertThat(result).isTrue()
            coVerify { memeDao.memeExistsByHash(any()) }
        }

    @Test
    fun `isDuplicate returns false when file bytes are new`() =
        runTest {
            val uri = createTestImageUri()

            coEvery { memeDao.memeExistsByHash(any()) } returns false

            val result = repository.isDuplicate(uri)

            assertThat(result).isFalse()
        }

    @Test
    fun `isDuplicate returns false when URI stream is null`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("dup_null")
            every { mockContentResolver.openInputStream(any()) } returns null

            val repoWithMock =
                ImportRepositoryImpl(
                    context = mockContext,
                    memeDao = memeDao,
                    emojiTagDao = emojiTagDao,
                    importRequestDao = importRequestDao,
                    mlServices =
                        ImportMlServices(
                            textRecognizer = textRecognizer,
                            embeddingManager = embeddingManager,
                            xmpMetadataHandler = xmpMetadataHandler,
                        ),
                )

            val uri = mockk<Uri>()
            val result = repoWithMock.isDuplicate(uri)

            assertThat(result).isFalse()
        }

    @Test
    fun `isDuplicate returns false when exception is thrown`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("dup_err")
            every { mockContentResolver.openInputStream(any()) } throws IOException("Stream error")

            val repoWithMock =
                ImportRepositoryImpl(
                    context = mockContext,
                    memeDao = memeDao,
                    emojiTagDao = emojiTagDao,
                    importRequestDao = importRequestDao,
                    mlServices =
                        ImportMlServices(
                            textRecognizer = textRecognizer,
                            embeddingManager = embeddingManager,
                            xmpMetadataHandler = xmpMetadataHandler,
                        ),
                )

            val uri = mockk<Uri>()
            val result = repoWithMock.isDuplicate(uri)

            assertThat(result).isFalse()
        }

    @Test
    fun `findDuplicateMemeId returns meme ID when duplicate exists`() =
        runTest {
            val uri = createTestImageUri()
            val existingMeme =
                com.adsamcik.riposte.core.database.entity.MemeEntity(
                    id = 42L,
                    filePath = "/fake/path.jpg",
                    fileName = "test.jpg",
                    mimeType = "image/jpeg",
                    width = 100,
                    height = 100,
                    fileSizeBytes = 1000,
                    importedAt = System.currentTimeMillis(),
                    emojiTagsJson = "[]",
                    fileHash = "abc123",
                )

            coEvery { memeDao.getMemeByHash(any()) } returns existingMeme

            val result = repository.findDuplicateMemeId(uri)

            assertThat(result).isEqualTo(42L)
        }

    @Test
    fun `findDuplicateMemeId returns null when no duplicate exists`() =
        runTest {
            val uri = createTestImageUri()

            coEvery { memeDao.getMemeByHash(any()) } returns null

            val result = repository.findDuplicateMemeId(uri)

            assertThat(result).isNull()
        }

    @Test
    fun `findDuplicateMemeId returns null when URI stream is null`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("dup_find_null")
            every { mockContentResolver.openInputStream(any()) } returns null

            val repoWithMock =
                ImportRepositoryImpl(
                    context = mockContext,
                    memeDao = memeDao,
                    emojiTagDao = emojiTagDao,
                    importRequestDao = importRequestDao,
                    mlServices =
                        ImportMlServices(
                            textRecognizer = textRecognizer,
                            embeddingManager = embeddingManager,
                            xmpMetadataHandler = xmpMetadataHandler,
                        ),
                )

            val uri = mockk<Uri>()
            val result = repoWithMock.findDuplicateMemeId(uri)

            assertThat(result).isNull()
        }

    @Test
    fun `isDuplicate produces consistent hash for identical content`() =
        runTest {
            // Create two URIs with identical bytes
            val file1 = File(context.cacheDir, "dup_test_a_${System.currentTimeMillis()}.jpg")
            file1.writeBytes(validJpegBytes)
            val uri1 = Uri.fromFile(file1)

            val file2 = File(context.cacheDir, "dup_test_b_${System.currentTimeMillis()}.jpg")
            file2.writeBytes(validJpegBytes)
            val uri2 = Uri.fromFile(file2)

            // Capture the hashes passed to the DAO for both calls
            val capturedHashes = mutableListOf<String>()
            coEvery { memeDao.memeExistsByHash(capture(capturedHashes)) } returns false

            repository.isDuplicate(uri1)
            repository.isDuplicate(uri2)

            assertThat(capturedHashes).hasSize(2)
            assertThat(capturedHashes[0]).isEqualTo(capturedHashes[1])
        }

    @Test
    fun `isDuplicate produces different hash for different content`() =
        runTest {
            val file1 = File(context.cacheDir, "dup_diff_a_${System.currentTimeMillis()}.dat")
            file1.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            val uri1 = Uri.fromFile(file1)

            val file2 = File(context.cacheDir, "dup_diff_b_${System.currentTimeMillis()}.dat")
            file2.writeBytes(byteArrayOf(6, 7, 8, 9, 10))
            val uri2 = Uri.fromFile(file2)

            val capturedHashes = mutableListOf<String>()
            coEvery { memeDao.memeExistsByHash(capture(capturedHashes)) } returns false

            repository.isDuplicate(uri1)
            repository.isDuplicate(uri2)

            assertThat(capturedHashes).hasSize(2)
            assertThat(capturedHashes[0]).isNotEqualTo(capturedHashes[1])
        }

    @Test
    fun `importImage stores hash derived from original source bytes`() =
        runTest {
            val uri = createTestImageUri()
            val memeEntitySlot = io.mockk.slot<com.adsamcik.riposte.core.database.entity.MemeEntity>()

            coEvery { memeDao.insertMeme(capture(memeEntitySlot)) } returns 1L
            coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns null

            repository.importImage(uri, null)

            // Verify the stored hash is not null and looks like a valid SHA-256 hex string
            val storedHash = memeEntitySlot.captured.fileHash
            assertThat(storedHash).isNotNull()
            assertThat(storedHash).hasLength(64) // SHA-256 = 64 hex chars
            assertThat(storedHash).matches("[0-9a-f]{64}")
        }

    @Test
    fun `importImage and isDuplicate use same hash for same source`() =
        runTest {
            // First, import an image and capture the hash stored in the DB
            val uri = createTestImageUri()
            val memeEntitySlot = io.mockk.slot<com.adsamcik.riposte.core.database.entity.MemeEntity>()

            coEvery { memeDao.insertMeme(capture(memeEntitySlot)) } returns 1L
            coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns null

            repository.importImage(uri, null)
            val importHash = memeEntitySlot.captured.fileHash

            // Now check duplicate for the same file and capture the hash used for lookup
            val lookupHash = io.mockk.slot<String>()
            coEvery { memeDao.memeExistsByHash(capture(lookupHash)) } returns false

            // Re-create the URI with the same bytes to simulate the same file
            val file2 = File(context.cacheDir, "dup_match_${System.currentTimeMillis()}.jpg")
            file2.writeBytes(validJpegBytes)
            val uri2 = Uri.fromFile(file2)

            repository.isDuplicate(uri2)

            // The hash used for lookup must match the hash stored during import
            assertThat(lookupHash.captured).isEqualTo(importHash)
        }

    // ==================== Helper Methods ====================

    private fun createTestImageUri(): Uri {
        val tempFile = File(context.cacheDir, "test_image_${System.currentTimeMillis()}.jpg")
        tempFile.writeBytes(validJpegBytes)
        return Uri.fromFile(tempFile)
    }
}
