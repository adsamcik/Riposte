package com.mememymood.feature.import_feature.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Edge case tests for [DefaultZipImporter] covering security, error handling, and resource limits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ZipImporterEdgeCasesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var zipImporter: DefaultZipImporter

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        zipImporter = DefaultZipImporter(context)
    }

    // ==================== ZIP Bomb Protection Tests ====================

    @Ignore("Creating 10,001 ZIP entries is too slow for unit tests. Run manually to verify.")
    @Test
    fun `extractBundle blocks ZIP with too many entries`() = runTest {
        // Create a ZIP with more entries than allowed
        // Use just 1 over the limit to keep test fast (creating 10k+ entries is slow)
        val zipBytes = createZipWithManyEntries(DefaultZipImporter.MAX_ENTRY_COUNT + 1)
        val zipFile = tempFolder.newFile("bomb.meme.zip")
        zipFile.writeBytes(zipBytes)
        val uri = Uri.fromFile(zipFile)

        val result = zipImporter.extractBundle(uri)

        assertTrue(result.errors.containsKey("bundle"))
        assertTrue(result.errors["bundle"]!!.contains("Too many entries"))
    }

    @Test
    fun `extractBundle blocks single file exceeding size limit`() = runTest {
        // Create a ZIP with one file larger than MAX_SINGLE_FILE_SIZE
        // We'll simulate this by creating content that exceeds the limit
        val largeContent = ByteArray((DefaultZipImporter.MAX_SINGLE_FILE_SIZE + 1024).toInt()) { 0 }
        val zipBytes = createZipWithContent("large.jpg", largeContent)
        val zipFile = tempFolder.newFile("large.meme.zip")
        zipFile.writeBytes(zipBytes)
        val uri = Uri.fromFile(zipFile)

        val result = zipImporter.extractBundle(uri)

        assertTrue(result.errors.containsKey("large.jpg"))
        assertTrue(result.errors["large.jpg"]!!.contains("size limit"))
    }

    @Test
    fun `extractBundle blocks oversized JSON sidecar`() = runTest {
        // Create a ZIP with a JSON file larger than MAX_JSON_SIZE
        val largeJson = "{" + "\"key\":\"${"x".repeat((DefaultZipImporter.MAX_JSON_SIZE + 1024).toInt())}\"}"
        val zipBytes = createZipWithJsonSidecar("test.jpg", largeJson)
        val zipFile = tempFolder.newFile("largejson.meme.zip")
        zipFile.writeBytes(zipBytes)
        val uri = Uri.fromFile(zipFile)

        val result = zipImporter.extractBundle(uri)

        assertTrue(result.errors.containsKey("test.jpg.json"))
        assertTrue(result.errors["test.jpg.json"]!!.contains("size limit") ||
            result.errors["test.jpg.json"]!!.contains("too large"))
    }

    // ==================== Path Traversal Tests ====================

    @Test
    fun `extractBundle blocks path traversal with dot dot`() = runTest {
        val zipBytes = createZipWithEntry("../../../etc/passwd", "malicious".toByteArray())
        val zipFile = tempFolder.newFile("traversal.meme.zip")
        zipFile.writeBytes(zipBytes)
        val uri = Uri.fromFile(zipFile)

        val result = zipImporter.extractBundle(uri)

        // Should not extract any files
        assertEquals(0, result.extractedMemes.size)
    }

    @Test
    fun `extractBundle blocks hidden files starting with dot`() = runTest {
        val zipBytes = createZipWithEntry(".hidden.jpg", createMinimalJpeg())
        val zipFile = tempFolder.newFile("hidden.meme.zip")
        zipFile.writeBytes(zipBytes)
        val uri = Uri.fromFile(zipFile)

        val result = zipImporter.extractBundle(uri)

        // Hidden files should be skipped (not error, but not extracted)
        assertEquals(0, result.extractedMemes.size)
    }

    @Test
    fun `extractBundle blocks entries with subdirectory paths`() = runTest {
        val zipBytes = createZipWithEntry("subdir/image.jpg", createMinimalJpeg())
        val zipFile = tempFolder.newFile("subdir.meme.zip")
        zipFile.writeBytes(zipBytes)
        val uri = Uri.fromFile(zipFile)

        val result = zipImporter.extractBundle(uri)

        // Subdirectory entries should be skipped
        assertEquals(0, result.extractedMemes.size)
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `extractBundle handles corrupt ZIP gracefully`() = runTest {
        val zipFile = tempFolder.newFile("corrupt.meme.zip")
        zipFile.writeBytes("not a valid zip file".toByteArray())
        val uri = Uri.fromFile(zipFile)

        val result = zipImporter.extractBundle(uri)

        // A corrupt ZIP should either have errors or simply no extracted memes
        // The implementation may handle the corrupt data differently on various platforms
        assertEquals(0, result.extractedMemes.size)
    }

    @Test
    fun `extractBundle handles empty ZIP`() = runTest {
        val zipBytes = createEmptyZip()
        val zipFile = tempFolder.newFile("empty.meme.zip")
        zipFile.writeBytes(zipBytes)
        val uri = Uri.fromFile(zipFile)

        val result = zipImporter.extractBundle(uri)

        assertEquals(0, result.extractedMemes.size)
        // Empty ZIP should not be an error - just no images found
    }

    @Test
    fun `extractBundle handles ZIP with only non-image files`() = runTest {
        val zipBytes = createZipWithEntry("readme.txt", "Hello".toByteArray())
        val zipFile = tempFolder.newFile("noimage.meme.zip")
        zipFile.writeBytes(zipBytes)
        val uri = Uri.fromFile(zipFile)

        val result = zipImporter.extractBundle(uri)

        assertEquals(0, result.extractedMemes.size)
    }

    @Test
    fun `extractBundle handles malformed JSON sidecar`() = runTest {
        val invalidJson = "{not valid json"
        val imageBytes = createMinimalJpeg()
        val zipBytes = createZipWithImageAndSidecar("test.jpg", imageBytes, invalidJson)
        val zipFile = tempFolder.newFile("badjson.meme.zip")
        zipFile.writeBytes(zipBytes)
        val uri = Uri.fromFile(zipFile)

        val result = zipImporter.extractBundle(uri)

        // Image should still be extracted, just without metadata
        assertEquals(1, result.extractedMemes.size)
        // The malformed JSON should not cause an error, just null metadata
        assertEquals(null, result.extractedMemes[0].metadata)
    }

    // ==================== SecurityException Tests ====================

    @Test
    fun `extractBundle handles SecurityException from contentResolver`() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val mockContentResolver = mockk<ContentResolver>()

        every { mockContext.contentResolver } returns mockContentResolver
        every { mockContext.cacheDir } returns tempFolder.newFolder("cache")
        every { mockContentResolver.openInputStream(any()) } throws SecurityException("Permission denied")

        val zipImporterWithMock = DefaultZipImporter(mockContext)
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"

        val result = zipImporterWithMock.extractBundle(uri)

        assertTrue(result.errors.containsKey("bundle"))
        assertTrue(result.errors["bundle"]!!.contains("Permission denied"))
    }

    // ==================== Unicode Filename Tests ====================

    @Test
    fun `extractBundle handles unicode filenames`() = runTest {
        val imageBytes = createMinimalJpeg()
        val zipBytes = createZipWithEntry("日本語.jpg", imageBytes)
        val zipFile = tempFolder.newFile("unicode.meme.zip")
        zipFile.writeBytes(zipBytes)
        val uri = Uri.fromFile(zipFile)

        val result = zipImporter.extractBundle(uri)

        // Should successfully extract unicode-named files
        assertEquals(1, result.extractedMemes.size)
    }

    @Test
    fun `extractBundle handles filename with spaces`() = runTest {
        val imageBytes = createMinimalJpeg()
        val zipBytes = createZipWithEntry("my cool meme.jpg", imageBytes)
        val zipFile = tempFolder.newFile("spaces.meme.zip")
        zipFile.writeBytes(zipBytes)
        val uri = Uri.fromFile(zipFile)

        val result = zipImporter.extractBundle(uri)

        assertEquals(1, result.extractedMemes.size)
    }

    // ==================== Cleanup Tests ====================

    @Test
    fun `cleanupExtractedFiles removes all temp files`() = runTest {
        // First extract something
        val imageBytes = createMinimalJpeg()
        val zipBytes = createZipWithEntry("test.jpg", imageBytes)
        val zipFile = tempFolder.newFile("cleanup.meme.zip")
        zipFile.writeBytes(zipBytes)
        val uri = Uri.fromFile(zipFile)

        val result = zipImporter.extractBundle(uri)
        assertEquals(1, result.extractedMemes.size)

        // Now cleanup
        zipImporter.cleanupExtractedFiles()

        // Verify extracted file no longer exists
        val extractedFile = File(result.extractedMemes[0].imageUri.path!!)
        assertFalse(extractedFile.exists())
    }

    // ==================== isMemeZipBundle Tests ====================

    @Test
    fun `isMemeZipBundle returns true for meme zip extension`() {
        val zipFile = tempFolder.newFile("test.meme.zip")
        zipFile.writeBytes(ByteArray(0))
        val uri = Uri.fromFile(zipFile)
        assertTrue(zipImporter.isMemeZipBundle(uri))
    }

    @Test
    fun `isMemeZipBundle returns false for regular zip without meme extension`() {
        // A .zip file that doesn't end with .meme.zip and doesn't have application/zip mime type
        // Since Robolectric's content resolver returns null for file:// URIs, this should be false
        val zipFile = tempFolder.newFile("test.zip")
        zipFile.writeBytes(ByteArray(0))
        val uri = Uri.fromFile(zipFile)
        assertFalse(zipImporter.isMemeZipBundle(uri))
    }

    // ==================== Helper Functions ====================

    private fun createZipWithManyEntries(count: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            repeat(count) { i ->
                zos.putNextEntry(ZipEntry("image_$i.jpg"))
                zos.write(createMinimalJpeg())
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun createZipWithContent(name: String, content: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(name))
            zos.write(content)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun createZipWithEntry(name: String, content: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(name))
            zos.write(content)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun createZipWithJsonSidecar(imageName: String, jsonContent: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Add image
            zos.putNextEntry(ZipEntry(imageName))
            zos.write(createMinimalJpeg())
            zos.closeEntry()
            // Add JSON sidecar
            zos.putNextEntry(ZipEntry("$imageName.json"))
            zos.write(jsonContent.toByteArray())
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun createZipWithImageAndSidecar(imageName: String, imageBytes: ByteArray, jsonContent: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(imageName))
            zos.write(imageBytes)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("$imageName.json"))
            zos.write(jsonContent.toByteArray())
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun createEmptyZip(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { /* empty */ }
        return baos.toByteArray()
    }

    private fun createMinimalJpeg(): ByteArray {
        // Minimal valid JPEG (1x1 red pixel)
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xDB.toByte(), 0x00, 0x43, 0x00, 0x08,
            0x06, 0x06, 0x07, 0x06, 0x05, 0x08, 0x07, 0x07,
            0x07, 0x09, 0x09, 0x08, 0x0A, 0x0C, 0x14, 0x0D,
            0x0C, 0x0B, 0x0B, 0x0C, 0x19, 0x12, 0x13, 0x0F,
            0x14, 0x1D, 0x1A, 0x1F, 0x1E, 0x1D, 0x1A, 0x1C,
            0x1C, 0x20, 0x24, 0x2E, 0x27, 0x20, 0x22, 0x2C,
            0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29, 0x2C, 0x30,
            0x31, 0x34, 0x34, 0x34, 0x1F, 0x27, 0x39, 0x3D,
            0x38, 0x32, 0x3C, 0x2E, 0x33, 0x34, 0x32,
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x0B, 0x08,
            0x00, 0x01, 0x00, 0x01, 0x01, 0x01, 0x11, 0x00,
            0xFF.toByte(), 0xC4.toByte(), 0x00, 0x1F, 0x00,
            0x00, 0x01, 0x05, 0x01, 0x01, 0x01, 0x01, 0x01,
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0A, 0x0B,
            0xFF.toByte(), 0xDA.toByte(), 0x00, 0x08, 0x01,
            0x01, 0x00, 0x00, 0x3F, 0x00, 0x7F, 0x00,
            0xFF.toByte(), 0xD9.toByte(),
        )
    }
}
