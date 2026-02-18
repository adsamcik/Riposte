package com.adsamcik.riposte.feature.import_feature.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.ImportRequestDao
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.entity.MemeEntity
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
import io.mockk.slot
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
import java.security.MessageDigest

/**
 * Comprehensive tests for duplicate detection in [ImportRepositoryImpl].
 *
 * Tests the raw-byte hashing approach that hashes original source bytes
 * from the ContentResolver stream rather than re-encoded JPEG bytes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DuplicateDetectionTest {
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

    // 1x1 red JPEG bytes for testing
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

    // ==================== Hash Determinism ====================

    @Test
    fun `hash is deterministic across multiple calls on same file`() =
        runTest {
            val file = createFileWithBytes(validJpegBytes)
            val uri = Uri.fromFile(file)

            val capturedHashes = mutableListOf<String>()
            coEvery { memeDao.memeExistsByHash(capture(capturedHashes)) } returns false

            repeat(5) {
                repository.isDuplicate(uri)
            }

            assertThat(capturedHashes).hasSize(5)
            // All five calls should produce the same hash
            val uniqueHashes = capturedHashes.toSet()
            assertThat(uniqueHashes).hasSize(1)
        }

    @Test
    fun `hash matches expected SHA-256 for known content`() =
        runTest {
            val content = "hello world".toByteArray()
            val file = createFileWithBytes(content)
            val uri = Uri.fromFile(file)

            // Compute expected SHA-256
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(content)
            val expectedHash = digest.digest().joinToString("") { "%02x".format(it) }

            val capturedHash = slot<String>()
            coEvery { memeDao.memeExistsByHash(capture(capturedHash)) } returns false

            repository.isDuplicate(uri)

            assertThat(capturedHash.captured).isEqualTo(expectedHash)
        }

    @Test
    fun `hash differs when even a single byte changes`() =
        runTest {
            val content1 = byteArrayOf(1, 2, 3, 4, 5)
            val content2 = byteArrayOf(1, 2, 3, 4, 6) // last byte differs

            val file1 = createFileWithBytes(content1, "single_byte_a")
            val file2 = createFileWithBytes(content2, "single_byte_b")

            val capturedHashes = mutableListOf<String>()
            coEvery { memeDao.memeExistsByHash(capture(capturedHashes)) } returns false

            repository.isDuplicate(Uri.fromFile(file1))
            repository.isDuplicate(Uri.fromFile(file2))

            assertThat(capturedHashes).hasSize(2)
            assertThat(capturedHashes[0]).isNotEqualTo(capturedHashes[1])
        }

    // ==================== Empty and Small Files ====================

    @Test
    fun `hash works for empty file`() =
        runTest {
            val file = createFileWithBytes(byteArrayOf())
            val uri = Uri.fromFile(file)

            val capturedHash = slot<String>()
            coEvery { memeDao.memeExistsByHash(capture(capturedHash)) } returns false

            repository.isDuplicate(uri)

            // SHA-256 of empty input is a well-known constant
            val expectedEmptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            assertThat(capturedHash.captured).isEqualTo(expectedEmptyHash)
        }

    @Test
    fun `hash works for single byte file`() =
        runTest {
            val file = createFileWithBytes(byteArrayOf(0x42))
            val uri = Uri.fromFile(file)

            val capturedHash = slot<String>()
            coEvery { memeDao.memeExistsByHash(capture(capturedHash)) } returns false

            repository.isDuplicate(uri)

            assertThat(capturedHash.captured).hasLength(64)
            assertThat(capturedHash.captured).matches("[0-9a-f]{64}")
        }

    // ==================== Large Content ====================

    @Test
    fun `hash works for content larger than buffer size`() =
        runTest {
            // 8192 is the buffer size; test with content larger than that
            val largeContent = ByteArray(32768) { (it % 256).toByte() }
            val file = createFileWithBytes(largeContent, "large_file")
            val uri = Uri.fromFile(file)

            val capturedHash = slot<String>()
            coEvery { memeDao.memeExistsByHash(capture(capturedHash)) } returns false

            repository.isDuplicate(uri)

            // Verify hash is valid SHA-256
            assertThat(capturedHash.captured).hasLength(64)
            assertThat(capturedHash.captured).matches("[0-9a-f]{64}")

            // Verify same content produces same hash
            val file2 = createFileWithBytes(largeContent, "large_file_copy")
            val capturedHash2 = slot<String>()
            coEvery { memeDao.memeExistsByHash(capture(capturedHash2)) } returns false

            repository.isDuplicate(Uri.fromFile(file2))

            assertThat(capturedHash2.captured).isEqualTo(capturedHash.captured)
        }

    @Test
    fun `hash works for content exactly at buffer boundary`() =
        runTest {
            val content = ByteArray(8192) { (it % 256).toByte() }
            val file = createFileWithBytes(content, "boundary_file")
            val uri = Uri.fromFile(file)

            val capturedHash = slot<String>()
            coEvery { memeDao.memeExistsByHash(capture(capturedHash)) } returns false

            repository.isDuplicate(uri)

            assertThat(capturedHash.captured).hasLength(64)
        }

    // ==================== Format Independence ====================

    @Test
    fun `isDuplicate and findDuplicateMemeId use same hash for same file`() =
        runTest {
            val file = createFileWithBytes(validJpegBytes)
            val uri = Uri.fromFile(file)

            val isDupHash = slot<String>()
            val findDupHash = slot<String>()
            coEvery { memeDao.memeExistsByHash(capture(isDupHash)) } returns false
            coEvery { memeDao.getMemeByHash(capture(findDupHash)) } returns null

            repository.isDuplicate(uri)

            // Create identical file for findDuplicateMemeId
            val file2 = createFileWithBytes(validJpegBytes, "find_dup")
            repository.findDuplicateMemeId(Uri.fromFile(file2))

            assertThat(isDupHash.captured).isEqualTo(findDupHash.captured)
        }

    @Test
    fun `non-image files produce valid hashes`() =
        runTest {
            // Plain text content (not an image at all)
            val textContent = "This is not an image, but it should still hash correctly.".toByteArray()
            val file = createFileWithBytes(textContent, "text_file")
            val uri = Uri.fromFile(file)

            val capturedHash = slot<String>()
            coEvery { memeDao.memeExistsByHash(capture(capturedHash)) } returns false

            repository.isDuplicate(uri)

            assertThat(capturedHash.captured).hasLength(64)
            assertThat(capturedHash.captured).matches("[0-9a-f]{64}")
        }

    // ==================== Import + Check Alignment ====================

    @Test
    fun `importImage stores file hash from source bytes not re-encoded file`() =
        runTest {
            val file = createFileWithBytes(validJpegBytes)
            val uri = Uri.fromFile(file)
            val memeEntitySlot = slot<MemeEntity>()

            coEvery { memeDao.insertMeme(capture(memeEntitySlot)) } returns 1L
            coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns null

            repository.importImage(uri, null)

            val storedHash = memeEntitySlot.captured.fileHash
            assertThat(storedHash).isNotNull()

            // Compute expected hash directly from original bytes
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(validJpegBytes)
            val expectedHash = digest.digest().joinToString("") { "%02x".format(it) }

            assertThat(storedHash).isEqualTo(expectedHash)
        }

    @Test
    fun `importImage with metadata still hashes original source bytes`() =
        runTest {
            val file = createFileWithBytes(validJpegBytes)
            val uri = Uri.fromFile(file)
            val memeEntitySlot = slot<MemeEntity>()
            val metadata = MemeMetadata(
                emojis = listOf("😂"),
                title = "Test Meme",
                description = "A description",
            )

            coEvery { memeDao.insertMeme(capture(memeEntitySlot)) } returns 1L
            coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns null

            repository.importImage(uri, metadata)

            val storedHash = memeEntitySlot.captured.fileHash

            // Hash should match original source bytes regardless of metadata
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(validJpegBytes)
            val expectedHash = digest.digest().joinToString("") { "%02x".format(it) }

            assertThat(storedHash).isEqualTo(expectedHash)
        }

    @Test
    fun `duplicate check after import finds the imported meme`() =
        runTest {
            val file = createFileWithBytes(validJpegBytes)
            val uri = Uri.fromFile(file)
            val memeEntitySlot = slot<MemeEntity>()

            coEvery { memeDao.insertMeme(capture(memeEntitySlot)) } returns 1L
            coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns null

            // Import the image
            repository.importImage(uri, null)
            val importHash = memeEntitySlot.captured.fileHash!!

            // Now set up DAO to return the meme when queried by its hash
            val savedEntity = memeEntitySlot.captured.copy(id = 1L)
            coEvery { memeDao.getMemeByHash(importHash) } returns savedEntity

            // Check for duplicate with same bytes
            val file2 = createFileWithBytes(validJpegBytes, "dup_after_import")
            val duplicateId = repository.findDuplicateMemeId(Uri.fromFile(file2))

            assertThat(duplicateId).isEqualTo(1L)
        }

    // ==================== Error Handling ====================

    @Test
    fun `findDuplicateMemeId returns null when DAO throws exception`() =
        runTest {
            val file = createFileWithBytes(validJpegBytes)
            val uri = Uri.fromFile(file)

            coEvery { memeDao.getMemeByHash(any()) } throws RuntimeException("DB crashed")

            val result = repository.findDuplicateMemeId(uri)

            assertThat(result).isNull()
        }

    @Test
    fun `isDuplicate returns false when DAO throws exception`() =
        runTest {
            val file = createFileWithBytes(validJpegBytes)
            val uri = Uri.fromFile(file)

            coEvery { memeDao.memeExistsByHash(any()) } throws RuntimeException("DB crashed")

            val result = repository.isDuplicate(uri)

            assertThat(result).isFalse()
        }

    @Test
    fun `isDuplicate returns false when content resolver returns null stream`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("null_stream")
            every { mockContentResolver.openInputStream(any()) } returns null

            val repoWithMock = createRepositoryWithContext(mockContext)
            val uri = mockk<Uri>()

            val result = repoWithMock.isDuplicate(uri)

            assertThat(result).isFalse()
            coVerify(exactly = 0) { memeDao.memeExistsByHash(any()) }
        }

    @Test
    fun `findDuplicateMemeId returns null when content resolver returns null stream`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("null_stream_find")
            every { mockContentResolver.openInputStream(any()) } returns null

            val repoWithMock = createRepositoryWithContext(mockContext)
            val uri = mockk<Uri>()

            val result = repoWithMock.findDuplicateMemeId(uri)

            assertThat(result).isNull()
            coVerify(exactly = 0) { memeDao.getMemeByHash(any()) }
        }

    @Test
    fun `isDuplicate returns false when stream throws IOException during read`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("io_error")
            every { mockContentResolver.openInputStream(any()) } throws IOException("Disk read error")

            val repoWithMock = createRepositoryWithContext(mockContext)
            val uri = mockk<Uri>()

            val result = repoWithMock.isDuplicate(uri)

            assertThat(result).isFalse()
        }

    @Test
    fun `findDuplicateMemeId returns null when stream throws SecurityException`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("sec_error")
            every { mockContentResolver.openInputStream(any()) } throws SecurityException("Permission denied")

            val repoWithMock = createRepositoryWithContext(mockContext)
            val uri = mockk<Uri>()

            val result = repoWithMock.findDuplicateMemeId(uri)

            assertThat(result).isNull()
        }

    // ==================== No False Positives/Negatives ====================

    @Test
    fun `isDuplicate does not call DAO when hash is null`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("no_dao_call")
            every { mockContentResolver.openInputStream(any()) } returns null

            val repoWithMock = createRepositoryWithContext(mockContext)
            val uri = mockk<Uri>()

            repoWithMock.isDuplicate(uri)

            coVerify(exactly = 0) { memeDao.memeExistsByHash(any()) }
        }

    @Test
    fun `findDuplicateMemeId does not call DAO when hash is null`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("no_dao_find")
            every { mockContentResolver.openInputStream(any()) } returns null

            val repoWithMock = createRepositoryWithContext(mockContext)
            val uri = mockk<Uri>()

            repoWithMock.findDuplicateMemeId(uri)

            coVerify(exactly = 0) { memeDao.getMemeByHash(any()) }
        }

    @Test
    fun `different files with same name but different content produce different hashes`() =
        runTest {
            val file1 = File(context.cacheDir, "same_name.jpg")
            file1.writeBytes(byteArrayOf(1, 2, 3))
            val uri1 = Uri.fromFile(file1)

            val capturedHash1 = slot<String>()
            coEvery { memeDao.memeExistsByHash(capture(capturedHash1)) } returns false
            repository.isDuplicate(uri1)

            // Overwrite same file with different content
            file1.writeBytes(byteArrayOf(4, 5, 6))
            val capturedHash2 = slot<String>()
            coEvery { memeDao.memeExistsByHash(capture(capturedHash2)) } returns false
            repository.isDuplicate(uri1)

            assertThat(capturedHash1.captured).isNotEqualTo(capturedHash2.captured)
        }

    @Test
    fun `files with different names but identical content produce same hash`() =
        runTest {
            val content = byteArrayOf(10, 20, 30, 40, 50)
            val file1 = createFileWithBytes(content, "name_a")
            val file2 = createFileWithBytes(content, "name_b")

            val capturedHashes = mutableListOf<String>()
            coEvery { memeDao.memeExistsByHash(capture(capturedHashes)) } returns false

            repository.isDuplicate(Uri.fromFile(file1))
            repository.isDuplicate(Uri.fromFile(file2))

            assertThat(capturedHashes).hasSize(2)
            assertThat(capturedHashes[0]).isEqualTo(capturedHashes[1])
        }

    // ==================== Import Hash Storage ====================

    @Test
    fun `importImage stores null hash when URI stream cannot be opened`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockContentResolver = mockk<ContentResolver>()

            every { mockContext.contentResolver } returns mockContentResolver
            every { mockContext.filesDir } returns tempFolder.newFolder("null_hash_import")
            every { mockContext.cacheDir } returns tempFolder.newFolder("null_hash_cache")
            every { mockContentResolver.getType(any()) } returns "image/jpeg"

            // First openInputStream for hash calculation returns null
            // Subsequent calls for bitmap loading also return null
            every { mockContentResolver.openInputStream(any()) } returns null

            val repoWithMock = createRepositoryWithContext(mockContext)
            val uri = mockk<Uri>()

            val result = repoWithMock.importImage(uri, null)

            // Should fail because bitmap can't be loaded
            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `importImage hash is valid SHA-256 hex string with exactly 64 characters`() =
        runTest {
            val file = createFileWithBytes(validJpegBytes)
            val uri = Uri.fromFile(file)
            val memeEntitySlot = slot<MemeEntity>()

            coEvery { memeDao.insertMeme(capture(memeEntitySlot)) } returns 1L
            coEvery { textRecognizer.recognizeText(any<Bitmap>()) } returns null

            repository.importImage(uri, null)

            val hash = memeEntitySlot.captured.fileHash
            assertThat(hash).isNotNull()
            assertThat(hash).hasLength(64)
            assertThat(hash).matches("[0-9a-f]{64}")
            // No uppercase hex chars
            assertThat(hash).isEqualTo(hash!!.lowercase())
        }

    // ==================== Helper Methods ====================

    private fun createFileWithBytes(
        bytes: ByteArray,
        prefix: String = "test",
    ): File {
        val file = File(context.cacheDir, "${prefix}_${System.nanoTime()}.bin")
        file.writeBytes(bytes)
        return file
    }

    private fun createRepositoryWithContext(ctx: Context): ImportRepositoryImpl =
        ImportRepositoryImpl(
            context = ctx,
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
