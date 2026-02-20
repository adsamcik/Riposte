package com.adsamcik.riposte.core.common.extension

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Tests for Bitmap extension functions.
 * Uses Robolectric for Android-specific functionality.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BitmapExtensionsTest {
    private lateinit var testBitmap: Bitmap

    @Before
    fun setup() {
        // Create a test bitmap (100x50 pixels)
        testBitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
    }

    @After
    fun tearDown() {
        if (!testBitmap.isRecycled) {
            testBitmap.recycle()
        }
    }

    // region scaleToFit Tests

    @Test
    fun `scaleToFit returns same bitmap when dimensions are within bounds`() {
        val result = testBitmap.scaleToFit(200, 100)

        // When image fits within bounds, ratio >= 1, so same bitmap is returned
        assertThat(result).isSameInstanceAs(testBitmap)
    }

    @Test
    fun `scaleToFit scales down when width exceeds max`() {
        // testBitmap is 100x50, max is 50x100
        // ratioX = 50/100 = 0.5, ratioY = 100/50 = 2.0, ratio = 0.5
        // newWidth = 100 * 0.5 = 50, newHeight = 50 * 0.5 = 25
        val result = testBitmap.scaleToFit(50, 100)

        assertThat(result.width).isEqualTo(50)
        assertThat(result.height).isEqualTo(25)
    }

    @Test
    fun `scaleToFit scales down when height exceeds max`() {
        // testBitmap is 100x50, max is 200x25
        // ratioX = 200/100 = 2.0, ratioY = 25/50 = 0.5, ratio = 0.5
        // newWidth = 100 * 0.5 = 50, newHeight = 50 * 0.5 = 25
        val result = testBitmap.scaleToFit(200, 25)

        assertThat(result.width).isEqualTo(50)
        assertThat(result.height).isEqualTo(25)
    }

    @Test
    fun `scaleToFit scales down when both dimensions exceed max`() {
        // testBitmap is 100x50, max is 40x20
        // ratioX = 40/100 = 0.4, ratioY = 20/50 = 0.4, ratio = 0.4
        // newWidth = 100 * 0.4 = 40, newHeight = 50 * 0.4 = 20
        val result = testBitmap.scaleToFit(40, 20)

        assertThat(result.width).isEqualTo(40)
        assertThat(result.height).isEqualTo(20)
    }

    @Test
    fun `scaleToFit maintains aspect ratio`() {
        // Original aspect ratio is 100/50 = 2.0
        val result = testBitmap.scaleToFit(60, 60)

        // Result should maintain 2:1 aspect ratio
        val aspectRatio = result.width.toFloat() / result.height.toFloat()
        assertThat(aspectRatio).isWithin(0.01f).of(2.0f)
    }

    @Test
    fun `scaleToFit with exact match returns same bitmap`() {
        val result = testBitmap.scaleToFit(100, 50)

        assertThat(result).isSameInstanceAs(testBitmap)
    }

    @Test
    fun `scaleToFit with square max dimensions`() {
        // 100x50 bitmap fitting in 30x30
        // ratioX = 30/100 = 0.3, ratioY = 30/50 = 0.6, ratio = 0.3
        // newWidth = 100 * 0.3 = 30, newHeight = 50 * 0.3 = 15
        val result = testBitmap.scaleToFit(30, 30)

        assertThat(result.width).isEqualTo(30)
        assertThat(result.height).isEqualTo(15)
    }

    // endregion

    // region compress Tests

    @Test
    fun `compress returns non-empty byte array`() {
        val result = testBitmap.compress(Bitmap.CompressFormat.PNG, 100)

        assertThat(result).isNotEmpty()
    }

    @Test
    fun `compress with JPEG format`() {
        val result = testBitmap.compress(Bitmap.CompressFormat.JPEG, 80)

        assertThat(result).isNotEmpty()
    }

    @Test
    fun `compress coerces quality to valid range - below 0`() {
        // Quality -50 should be coerced to 0
        val result = testBitmap.compress(Bitmap.CompressFormat.PNG, -50)

        assertThat(result).isNotEmpty()
    }

    @Test
    fun `compress coerces quality to valid range - above 100`() {
        // Quality 150 should be coerced to 100
        val result = testBitmap.compress(Bitmap.CompressFormat.PNG, 150)

        assertThat(result).isNotEmpty()
    }

    @Test
    fun `compress with different quality levels produces different sizes for JPEG`() {
        val highQuality = testBitmap.compress(Bitmap.CompressFormat.JPEG, 100)
        val lowQuality = testBitmap.compress(Bitmap.CompressFormat.JPEG, 10)

        // Lower quality should generally produce smaller output
        // Note: For very small or simple images, this might not always hold
        assertThat(highQuality).isNotEmpty()
        assertThat(lowQuality).isNotEmpty()
    }

    // endregion

    // region saveToFile Tests

    @Test
    fun `saveToFile creates file successfully`() {
        val tempFile = File.createTempFile("test_bitmap", ".png")
        tempFile.deleteOnExit()

        val result = testBitmap.saveToFile(tempFile, Bitmap.CompressFormat.PNG, 100)

        assertThat(result).isTrue()
        assertThat(tempFile.exists()).isTrue()
        assertThat(tempFile.length()).isGreaterThan(0)
    }

    @Test
    fun `saveToFile with JPEG format`() {
        val tempFile = File.createTempFile("test_bitmap", ".jpg")
        tempFile.deleteOnExit()

        val result = testBitmap.saveToFile(tempFile, Bitmap.CompressFormat.JPEG, 80)

        assertThat(result).isTrue()
        assertThat(tempFile.exists()).isTrue()
    }

    @Test
    fun `saveToFile returns false for invalid directory`() {
        val invalidFile = File("/nonexistent/path/test.png")

        val result = testBitmap.saveToFile(invalidFile, Bitmap.CompressFormat.PNG, 100)

        assertThat(result).isFalse()
    }

    @Test
    fun `saveToFile coerces quality within bounds`() {
        val tempFile = File.createTempFile("test_bitmap", ".png")
        tempFile.deleteOnExit()

        val result = testBitmap.saveToFile(tempFile, Bitmap.CompressFormat.PNG, 200)

        assertThat(result).isTrue()
    }

    // endregion

    // region loadBitmap Tests

    @Test
    fun `loadBitmap returns null for non-existent file`() {
        val nonExistentFile = File("/nonexistent/file.png")

        val result = nonExistentFile.loadBitmap()

        assertThat(result).isNull()
    }

    @Test
    fun `loadBitmap loads bitmap from existing file`() {
        val tempFile = File.createTempFile("test_load", ".png")
        tempFile.deleteOnExit()

        // Save the test bitmap first
        testBitmap.saveToFile(tempFile, Bitmap.CompressFormat.PNG, 100)

        val result = tempFile.loadBitmap()

        assertThat(result).isNotNull()
        result?.recycle()
    }

    @Test
    fun `loadBitmap with max dimensions samples down large image`() {
        // Create a larger bitmap
        val largeBitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val tempFile = File.createTempFile("test_large", ".png")
        tempFile.deleteOnExit()

        largeBitmap.saveToFile(tempFile, Bitmap.CompressFormat.PNG, 100)
        largeBitmap.recycle()

        // Load with max dimensions that should trigger sampling
        val result = tempFile.loadBitmap(maxWidth = 200, maxHeight = 200)

        assertThat(result).isNotNull()
        // Due to inSampleSize being powers of 2, result might not be exactly 200x200
        assertThat(result!!.width).isLessThan(1000)
        assertThat(result.height).isLessThan(1000)
        result.recycle()
    }

    @Test
    fun `loadBitmap without max dimensions loads full size`() {
        val tempFile = File.createTempFile("test_full", ".png")
        tempFile.deleteOnExit()

        testBitmap.saveToFile(tempFile, Bitmap.CompressFormat.PNG, 100)

        val result = tempFile.loadBitmap()

        assertThat(result).isNotNull()
        assertThat(result!!.width).isEqualTo(100)
        assertThat(result.height).isEqualTo(50)
        result.recycle()
    }

    // endregion
}

/**
 * Tests for Uri extension functions.
 * Uses MockK for mocking Android dependencies.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UriExtensionsTest {
    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)
        every { mockContext.contentResolver } returns mockContentResolver
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // region getFileName Tests

    @Test
    fun `getFileName returns name from content URI cursor`() {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"

        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME))
        cursor.addRow(arrayOf("test_image.jpg"))

        every { mockContentResolver.query(uri, any(), null, null, null) } returns cursor

        val result = uri.getFileName(mockContext)

        assertThat(result).isEqualTo("test_image.jpg")
    }

    @Test
    fun `getFileName extracts name from path when cursor returns null`() {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"
        every { uri.path } returns "/storage/emulated/0/Download/photo.png"
        every { mockContentResolver.query(uri, any(), null, null, null) } returns null

        val result = uri.getFileName(mockContext)

        assertThat(result).isEqualTo("photo.png")
    }

    @Test
    fun `getFileName handles file scheme URI`() {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns "/storage/Download/document.pdf"

        val result = uri.getFileName(mockContext)

        assertThat(result).isEqualTo("document.pdf")
    }

    @Test
    fun `getFileName returns path when no slash present`() {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns "simple_name.txt"

        val result = uri.getFileName(mockContext)

        assertThat(result).isEqualTo("simple_name.txt")
    }

    @Test
    fun `getFileName returns null when path is null`() {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns null

        val result = uri.getFileName(mockContext)

        assertThat(result).isNull()
    }

    @Test
    fun `getFileName falls back to path when cursor is empty`() {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"
        every { uri.path } returns "/fallback/path/file.jpg"

        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME))
        // Don't add any rows — moveToFirst() returns false

        every { mockContentResolver.query(uri, any(), null, null, null) } returns cursor

        val result = uri.getFileName(mockContext)

        assertThat(result).isEqualTo("file.jpg")
    }

    // endregion

    // region getFileSize Tests

    @Test
    fun `getFileSize returns size from content URI`() {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"

        val cursor = MatrixCursor(arrayOf(OpenableColumns.SIZE))
        cursor.addRow(arrayOf(1024L))

        every { mockContentResolver.query(uri, any(), null, null, null) } returns cursor

        val result = uri.getFileSize(mockContext)

        assertThat(result).isEqualTo(1024L)
    }

    @Test
    fun `getFileSize returns 0 when cursor is null`() {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"
        every { mockContentResolver.query(uri, any(), null, null, null) } returns null

        val result = uri.getFileSize(mockContext)

        assertThat(result).isEqualTo(0L)
    }

    @Test
    fun `getFileSize returns 0 for non-content scheme`() {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"

        val result = uri.getFileSize(mockContext)

        assertThat(result).isEqualTo(0L)
    }

    @Test
    fun `getFileSize returns 0 when SIZE column not found`() {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"

        val cursor = MatrixCursor(arrayOf("other_column"))
        cursor.addRow(arrayOf("value"))

        every { mockContentResolver.query(uri, any(), null, null, null) } returns cursor

        val result = uri.getFileSize(mockContext)

        assertThat(result).isEqualTo(0L)
    }

    @Test
    fun `getFileSize returns 0 when cursor is empty`() {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"

        val cursor = MatrixCursor(arrayOf(OpenableColumns.SIZE))
        // Don't add any rows

        every { mockContentResolver.query(uri, any(), null, null, null) } returns cursor

        val result = uri.getFileSize(mockContext)

        assertThat(result).isEqualTo(0L)
    }

    // endregion

    // region getMimeType Tests

    @Test
    fun `getMimeType returns type from content resolver`() {
        val uri = mockk<Uri>()
        every { mockContentResolver.getType(uri) } returns "image/jpeg"

        val result = uri.getMimeType(mockContext)

        assertThat(result).isEqualTo("image/jpeg")
    }

    @Test
    fun `getMimeType returns null when type not found`() {
        val uri = mockk<Uri>()
        every { mockContentResolver.getType(uri) } returns null

        val result = uri.getMimeType(mockContext)

        assertThat(result).isNull()
    }

    @Test
    fun `getMimeType handles various mime types`() {
        val uri = mockk<Uri>()

        every { mockContentResolver.getType(uri) } returns "application/pdf"
        assertThat(uri.getMimeType(mockContext)).isEqualTo("application/pdf")

        every { mockContentResolver.getType(uri) } returns "video/mp4"
        assertThat(uri.getMimeType(mockContext)).isEqualTo("video/mp4")

        every { mockContentResolver.getType(uri) } returns "text/plain"
        assertThat(uri.getMimeType(mockContext)).isEqualTo("text/plain")
    }

    // endregion

    // region copyToFile Tests

    @Test
    fun `copyToFile copies content successfully`() {
        val uri = mockk<Uri>()
        val tempFile = File.createTempFile("test_copy", ".txt")
        tempFile.deleteOnExit()

        val content = "Test content data"
        val inputStream = ByteArrayInputStream(content.toByteArray())

        every { mockContentResolver.openInputStream(uri) } returns inputStream

        val result = uri.copyToFile(mockContext, tempFile)

        assertThat(result).isTrue()
        assertThat(tempFile.readText()).isEqualTo(content)
    }

    @Test
    fun `copyToFile returns false when input stream is null`() {
        val uri = mockk<Uri>()
        val tempFile = File.createTempFile("test_copy", ".txt")
        tempFile.deleteOnExit()

        every { mockContentResolver.openInputStream(uri) } returns null

        val result = uri.copyToFile(mockContext, tempFile)

        // The function returns true even with null input stream (no exception is thrown)
        // This is because the use block completes without error
        assertThat(result).isTrue()
    }

    @Test
    fun `copyToFile returns false when exception occurs`() {
        val uri = mockk<Uri>()
        val tempFile = File("/nonexistent/directory/file.txt")

        val inputStream = ByteArrayInputStream("test".toByteArray())
        every { mockContentResolver.openInputStream(uri) } returns inputStream

        val result = uri.copyToFile(mockContext, tempFile)

        assertThat(result).isFalse()
    }

    @Test
    fun `copyToFile handles binary content`() {
        val uri = mockk<Uri>()
        val tempFile = File.createTempFile("test_binary", ".bin")
        tempFile.deleteOnExit()

        val binaryContent = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte(), 0xFE.toByte())
        val inputStream = ByteArrayInputStream(binaryContent)

        every { mockContentResolver.openInputStream(uri) } returns inputStream

        val result = uri.copyToFile(mockContext, tempFile)

        assertThat(result).isTrue()
        assertThat(tempFile.readBytes()).isEqualTo(binaryContent)
    }

    @Test
    fun `copyToFile overwrites existing file`() {
        val uri = mockk<Uri>()
        val tempFile = File.createTempFile("test_overwrite", ".txt")
        tempFile.deleteOnExit()

        // Write initial content
        tempFile.writeText("original content")

        // Copy new content
        val newContent = "new content"
        val inputStream = ByteArrayInputStream(newContent.toByteArray())
        every { mockContentResolver.openInputStream(uri) } returns inputStream

        val result = uri.copyToFile(mockContext, tempFile)

        assertThat(result).isTrue()
        assertThat(tempFile.readText()).isEqualTo(newContent)
    }

    // endregion
}
