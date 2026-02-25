package com.adsamcik.riposte.feature.import_feature.data

import android.content.Context
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Security-focused tests for [DefaultZipImporter] covering path traversal variants,
 * ZIP slip defenses, size limit boundaries, and entry count limits.
 *
 * These tests exercise the private security methods ([getSafeFileName], [getSafeOutputFile],
 * [copyWithLimit]) through the public [extractBundle] API with crafted malicious ZIPs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ZipImporterSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var zipImporter: DefaultZipImporter

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        zipImporter = DefaultZipImporter(context)
    }

    // ==================== getSafeFileName: Double-Dot Rejection ====================

    @Test
    fun `extractBundle rejects filename with embedded double dots`() =
        runTest {
            // "image..jpg" bypasses the top-level filter (no "/" and doesn't start with ".")
            // but getSafeFileName blocks it because it contains ".."
            val zipBytes = createZipWithEntry("image..jpg", createMinimalJpeg())
            val zipFile = tempFolder.newFile("doubledot.meme.zip")
            zipFile.writeBytes(zipBytes)

            val result = zipImporter.extractBundle(Uri.fromFile(zipFile))

            assertThat(result.extractedMemes).isEmpty()
            assertThat(result.errors).containsKey("image..jpg")
            assertThat(result.errors["image..jpg"]).contains("Path traversal")
        }

    @Test
    fun `extractBundle rejects JSON sidecar with double dot in base name`() =
        runTest {
            // "data..test.jpg.json" → base name "data..test.jpg" contains ".."
            // getSafeFileName returns null, so the sidecar metadata is silently dropped
            val jsonContent = """{"schemaVersion":"1.0","emojis":["😂"]}"""
            val zipBytes = createZipWithEntry("data..test.jpg.json", jsonContent.toByteArray())
            val zipFile = tempFolder.newFile("doubledot-json.meme.zip")
            zipFile.writeBytes(zipBytes)

            val result = zipImporter.extractBundle(Uri.fromFile(zipFile))

            assertThat(result.extractedMemes).isEmpty()
        }

    @Test
    fun `extractBundle rejects filename with triple dots`() =
        runTest {
            // "evil...jpg" contains ".." substring
            val zipBytes = createZipWithEntry("evil...jpg", createMinimalJpeg())
            val zipFile = tempFolder.newFile("tripledot.meme.zip")
            zipFile.writeBytes(zipBytes)

            val result = zipImporter.extractBundle(Uri.fromFile(zipFile))

            assertThat(result.extractedMemes).isEmpty()
            assertThat(result.errors).containsKey("evil...jpg")
        }

    // ==================== Top-Level Filter: Dot-Prefixed & Slashes ====================

    @Test
    fun `extractBundle rejects entry named dot dot`() =
        runTest {
            // ".." starts with "." → skipped at top-level filter
            val zipBytes = createZipWithEntry("..", "malicious".toByteArray())
            val zipFile = tempFolder.newFile("dotdot.meme.zip")
            zipFile.writeBytes(zipBytes)

            val result = zipImporter.extractBundle(Uri.fromFile(zipFile))

            assertThat(result.extractedMemes).isEmpty()
        }

    @Test
    fun `extractBundle rejects entry named single dot`() =
        runTest {
            // "." starts with "." → skipped at top-level filter
            val zipBytes = createZipWithEntry(".", "malicious".toByteArray())
            val zipFile = tempFolder.newFile("singledot.meme.zip")
            zipFile.writeBytes(zipBytes)

            val result = zipImporter.extractBundle(Uri.fromFile(zipFile))

            assertThat(result.extractedMemes).isEmpty()
        }

    @Test
    fun `extractBundle rejects absolute Unix path entry`() =
        runTest {
            // "/tmp/evil.jpg" contains "/" → skipped at top-level filter
            val zipBytes = createZipWithEntry("/tmp/evil.jpg", createMinimalJpeg())
            val zipFile = tempFolder.newFile("absolute.meme.zip")
            zipFile.writeBytes(zipBytes)

            val result = zipImporter.extractBundle(Uri.fromFile(zipFile))

            assertThat(result.extractedMemes).isEmpty()
        }

    @Test
    fun `extractBundle rejects deeply nested traversal entry`() =
        runTest {
            val zipBytes = createZipWithEntry(
                "a/b/c/../../../etc/passwd.jpg",
                createMinimalJpeg(),
            )
            val zipFile = tempFolder.newFile("deeptraversal.meme.zip")
            zipFile.writeBytes(zipBytes)

            val result = zipImporter.extractBundle(Uri.fromFile(zipFile))

            assertThat(result.extractedMemes).isEmpty()
        }

    // ==================== Comprehensive: Mixed Valid and Malicious ====================

    @Test
    fun `extractBundle extracts valid entries while rejecting malicious ones`() =
        runTest {
            val imageBytes = createMinimalJpeg()
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                // Malicious: path traversal (starts with ".")
                zos.putNextEntry(ZipEntry("../../evil.jpg"))
                zos.write(imageBytes)
                zos.closeEntry()
                // Malicious: hidden file (starts with ".")
                zos.putNextEntry(ZipEntry(".hidden.jpg"))
                zos.write(imageBytes)
                zos.closeEntry()
                // Malicious: subdirectory (contains "/")
                zos.putNextEntry(ZipEntry("sub/nested.jpg"))
                zos.write(imageBytes)
                zos.closeEntry()
                // Malicious: double dots in filename (blocked by getSafeFileName)
                zos.putNextEntry(ZipEntry("evil..jpg"))
                zos.write(imageBytes)
                zos.closeEntry()
                // Valid entry
                zos.putNextEntry(ZipEntry("valid.jpg"))
                zos.write(imageBytes)
                zos.closeEntry()
            }

            val zipFile = tempFolder.newFile("mixed.meme.zip")
            zipFile.writeBytes(baos.toByteArray())

            val result = zipImporter.extractBundle(Uri.fromFile(zipFile))

            assertThat(result.extractedMemes).hasSize(1)
        }

    @Test
    fun `extractBundle pairs metadata correctly alongside rejected entries`() =
        runTest {
            val imageBytes = createMinimalJpeg()
            val validJson = """{"schemaVersion":"1.0","emojis":["🔥"],"title":"Safe Meme"}"""
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                // Malicious entry (skipped)
                zos.putNextEntry(ZipEntry("../attack.jpg"))
                zos.write(imageBytes)
                zos.closeEntry()
                // Valid JSON sidecar
                zos.putNextEntry(ZipEntry("safe.jpg.json"))
                zos.write(validJson.toByteArray())
                zos.closeEntry()
                // Valid image
                zos.putNextEntry(ZipEntry("safe.jpg"))
                zos.write(imageBytes)
                zos.closeEntry()
            }

            val zipFile = tempFolder.newFile("mixed-meta.meme.zip")
            zipFile.writeBytes(baos.toByteArray())

            val result = zipImporter.extractBundle(Uri.fromFile(zipFile))

            assertThat(result.extractedMemes).hasSize(1)
            assertThat(result.extractedMemes[0].metadata).isNotNull()
            assertThat(result.extractedMemes[0].metadata!!.title).isEqualTo("Safe Meme")
        }

    // ==================== Entry Count Boundary ====================

    @Test
    fun `extractBundle succeeds at exactly MAX_ENTRY_COUNT entries`() =
        runTest {
            // Exactly at the limit — no error should be produced.
            // Uses non-image .dat entries: counted but not processed, so this is fast.
            val zipBytes = createZipWithEmptyEntries(DefaultZipImporter.MAX_ENTRY_COUNT)
            val zipFile = tempFolder.newFile("maxentries.meme.zip")
            zipFile.writeBytes(zipBytes)

            val result = zipImporter.extractBundle(Uri.fromFile(zipFile))

            assertThat(result.errors).doesNotContainKey("bundle")
        }

    // ==================== Robustness ====================

    @Test
    fun `extractBundle handles very long filename without crashing`() =
        runTest {
            // 500-char filename — may fail at filesystem level but must not throw
            val longName = "a".repeat(500) + ".jpg"
            val zipBytes = createZipWithEntry(longName, createMinimalJpeg())
            val zipFile = tempFolder.newFile("longname.meme.zip")
            zipFile.writeBytes(zipBytes)

            val result = zipImporter.extractBundle(Uri.fromFile(zipFile))

            // The file may or may not extract depending on OS filename limits,
            // but the importer must not crash — it should return a result either way.
            assertThat(result).isNotNull()
        }

    @Test
    fun `extractBundle handles filename with only extension`() =
        runTest {
            // ".jpg" starts with "." → skipped at top-level filter
            val zipBytes = createZipWithEntry(".jpg", createMinimalJpeg())
            val zipFile = tempFolder.newFile("onlyext.meme.zip")
            zipFile.writeBytes(zipBytes)

            val result = zipImporter.extractBundle(Uri.fromFile(zipFile))

            assertThat(result.extractedMemes).isEmpty()
        }

    // ==================== Helper Functions ====================

    private fun createZipWithEntry(
        name: String,
        content: ByteArray,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(name))
            zos.write(content)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    /**
     * Creates a ZIP with [count] empty .dat entries for fast entry-counting tests.
     * Non-image, non-JSON entries are counted toward the limit but skip processing.
     */
    private fun createZipWithEmptyEntries(count: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            repeat(count) { i ->
                zos.putNextEntry(ZipEntry("entry_$i.dat"))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Suppress("MagicNumber")
    private fun createMinimalJpeg(): ByteArray =
        byteArrayOf(
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
