package com.adsamcik.riposte.feature.share.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.common.truth.Truth.assertThat
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.ShareConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ImageProcessorTest {

    private lateinit var context: Context
    private lateinit var imageProcessor: ImageProcessor

    private val mockBitmap: Bitmap = mockk(relaxed = true)

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        imageProcessor = ImageProcessor(context)
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    // region resizeBitmap Tests

    @Test
    fun `resizeBitmap returns same bitmap when within max dimensions`() {
        every { mockBitmap.width } returns 500
        every { mockBitmap.height } returns 500

        val result = imageProcessor.resizeBitmap(mockBitmap, 1000, 1000)

        assertThat(result).isSameInstanceAs(mockBitmap)
    }

    @Test
    fun `resizeBitmap scales down when width exceeds max`() {
        val originalBitmap = createRealBitmap(2000, 1000)

        val result = imageProcessor.resizeBitmap(originalBitmap, 1000, 1000)

        assertThat(result.width).isEqualTo(1000)
        assertThat(result.height).isEqualTo(500)
        
        // Cleanup
        if (result != originalBitmap) {
            result.recycle()
        }
        originalBitmap.recycle()
    }

    @Test
    fun `resizeBitmap scales down when height exceeds max`() {
        val originalBitmap = createRealBitmap(1000, 2000)

        val result = imageProcessor.resizeBitmap(originalBitmap, 1000, 1000)

        assertThat(result.width).isEqualTo(500)
        assertThat(result.height).isEqualTo(1000)
        
        // Cleanup
        if (result != originalBitmap) {
            result.recycle()
        }
        originalBitmap.recycle()
    }

    @Test
    fun `resizeBitmap maintains aspect ratio`() {
        val originalBitmap = createRealBitmap(1920, 1080)

        val result = imageProcessor.resizeBitmap(originalBitmap, 800, 800)

        val expectedWidth = 800
        val expectedHeight = (1080 * (800f / 1920f)).toInt()

        assertThat(result.width).isEqualTo(expectedWidth)
        assertThat(result.height).isEqualTo(expectedHeight)
        
        // Cleanup
        if (result != originalBitmap) {
            result.recycle()
        }
        originalBitmap.recycle()
    }

    @Test
    fun `resizeBitmap handles square images`() {
        val originalBitmap = createRealBitmap(1500, 1500)

        val result = imageProcessor.resizeBitmap(originalBitmap, 1000, 1000)

        assertThat(result.width).isEqualTo(1000)
        assertThat(result.height).isEqualTo(1000)
        
        // Cleanup
        if (result != originalBitmap) {
            result.recycle()
        }
        originalBitmap.recycle()
    }

    // endregion

    // region estimateFileSize Tests

    @Test
    fun `estimateFileSize returns larger size for PNG than WEBP`() {
        val pngSize = imageProcessor.estimateFileSize(1000, 1000, ImageFormat.PNG, 100)
        val webpSize = imageProcessor.estimateFileSize(1000, 1000, ImageFormat.WEBP, 100)

        assertThat(pngSize).isGreaterThan(webpSize)
    }

    @Test
    fun `estimateFileSize returns larger size for higher quality JPEG`() {
        val highQuality = imageProcessor.estimateFileSize(1000, 1000, ImageFormat.JPEG, 100)
        val lowQuality = imageProcessor.estimateFileSize(1000, 1000, ImageFormat.JPEG, 50)

        assertThat(highQuality).isGreaterThan(lowQuality)
    }

    @Test
    fun `estimateFileSize returns larger size for larger dimensions`() {
        val largeImage = imageProcessor.estimateFileSize(2000, 2000, ImageFormat.JPEG, 80)
        val smallImage = imageProcessor.estimateFileSize(500, 500, ImageFormat.JPEG, 80)

        assertThat(largeImage).isGreaterThan(smallImage)
    }

    @Test
    fun `estimateFileSize returns at least 1024 bytes`() {
        val result = imageProcessor.estimateFileSize(1, 1, ImageFormat.JPEG, 1)

        assertThat(result).isAtLeast(1024L)
    }

    @Test
    fun `estimateFileSize handles GIF format`() {
        val result = imageProcessor.estimateFileSize(500, 500, ImageFormat.GIF, 100)

        assertThat(result).isGreaterThan(0L)
    }

    @Test
    fun `estimateFileSize handles WEBP format`() {
        val result = imageProcessor.estimateFileSize(1000, 1000, ImageFormat.WEBP, 85)

        assertThat(result).isGreaterThan(0L)
    }

    // endregion

    // region processImage Tests

    @Test
    fun `processImage returns error when source file cannot be decoded`() {
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeFile(any()) } returns null

        val outputFile = File.createTempFile("test", ".jpg")
        val config = ShareConfig()

        val result = imageProcessor.processImage(
            sourcePath = "/nonexistent/path.jpg",
            config = config,
            outputFile = outputFile,
        )

        assertThat(result).isInstanceOf(ImageProcessor.ProcessResult.Error::class.java)
        assertThat((result as ImageProcessor.ProcessResult.Error).message)
            .isEqualTo("Failed to load image")

        unmockkStatic(BitmapFactory::class)
        outputFile.delete()
    }

    @Test
    fun `processImage returns success with correct dimensions`() {
        val inputBitmap = createRealBitmap(1000, 1000)
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeFile(any()) } returns inputBitmap

        val outputFile = File.createTempFile("test", ".jpg")
        val config = ShareConfig(
            format = ImageFormat.JPEG,
            quality = 80,
            maxWidth = 500,
            maxHeight = 500,
        )

        val result = imageProcessor.processImage(
            sourcePath = "/test/path.jpg",
            config = config,
            outputFile = outputFile,
        )

        assertThat(result).isInstanceOf(ImageProcessor.ProcessResult.Success::class.java)
        val success = result as ImageProcessor.ProcessResult.Success
        assertThat(success.width).isEqualTo(500)
        assertThat(success.height).isEqualTo(500)
        assertThat(success.file).isEqualTo(outputFile)

        unmockkStatic(BitmapFactory::class)
        outputFile.delete()
        inputBitmap.recycle()
    }

    @Test
    fun `processImage handles PNG format`() {
        val inputBitmap = createRealBitmap(500, 500)
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeFile(any()) } returns inputBitmap

        val outputFile = File.createTempFile("test", ".png")
        val config = ShareConfig(
            format = ImageFormat.PNG,
            quality = 100,
        )

        val result = imageProcessor.processImage(
            sourcePath = "/test/path.png",
            config = config,
            outputFile = outputFile,
        )

        assertThat(result).isInstanceOf(ImageProcessor.ProcessResult.Success::class.java)

        unmockkStatic(BitmapFactory::class)
        outputFile.delete()
        inputBitmap.recycle()
    }

    @Test
    fun `processImage handles WEBP format`() {
        val inputBitmap = createRealBitmap(500, 500)
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeFile(any()) } returns inputBitmap

        val outputFile = File.createTempFile("test", ".webp")
        val config = ShareConfig(
            format = ImageFormat.WEBP,
            quality = 85,
        )

        val result = imageProcessor.processImage(
            sourcePath = "/test/path.webp",
            config = config,
            outputFile = outputFile,
        )

        assertThat(result).isInstanceOf(ImageProcessor.ProcessResult.Success::class.java)

        unmockkStatic(BitmapFactory::class)
        outputFile.delete()
        inputBitmap.recycle()
    }

    @Test
    fun `processImage captures dimensions before bitmap recycle when resizing`() {
        // Regression test: simulates real Android behavior where accessing
        // a recycled bitmap's properties throws IllegalStateException.
        var sourceRecycled = false
        val sourceBitmap = mockk<Bitmap> {
            every { width } answers {
                if (sourceRecycled) throw IllegalStateException("recycled bitmap")
                2000
            }
            every { height } answers {
                if (sourceRecycled) throw IllegalStateException("recycled bitmap")
                1000
            }
            every { recycle() } answers { sourceRecycled = true }
        }

        var resizedRecycled = false
        val resizedBitmap = mockk<Bitmap> {
            every { width } answers {
                if (resizedRecycled) throw IllegalStateException("recycled bitmap")
                500
            }
            every { height } answers {
                if (resizedRecycled) throw IllegalStateException("recycled bitmap")
                250
            }
            every { compress(any(), any(), any()) } returns true
            every { recycle() } answers { resizedRecycled = true }
        }

        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeFile(any()) } returns sourceBitmap

        val spyProcessor = spyk(imageProcessor)
        every { spyProcessor.resizeBitmap(any(), any(), any()) } returns resizedBitmap

        val outputFile = File.createTempFile("test", ".jpg")
        val config = ShareConfig(
            format = ImageFormat.JPEG,
            quality = 80,
            maxWidth = 500,
            maxHeight = 500,
            stripMetadata = true,
        )

        val result = spyProcessor.processImage("/test/path.jpg", config, outputFile)

        assertThat(result).isInstanceOf(ImageProcessor.ProcessResult.Success::class.java)
        val success = result as ImageProcessor.ProcessResult.Success
        assertThat(success.width).isEqualTo(500)
        assertThat(success.height).isEqualTo(250)

        unmockkStatic(BitmapFactory::class)
        outputFile.delete()
    }

    @Test
    fun `processImage captures dimensions before bitmap recycle without resizing`() {
        // Regression test: when image is within max bounds, the same bitmap is returned
        // from resizeBitmap and then recycled. Dimensions must be captured before recycle.
        var recycled = false
        val sourceBitmap = mockk<Bitmap> {
            every { width } answers {
                if (recycled) throw IllegalStateException("recycled bitmap")
                500
            }
            every { height } answers {
                if (recycled) throw IllegalStateException("recycled bitmap")
                500
            }
            every { compress(any(), any(), any()) } returns true
            every { recycle() } answers { recycled = true }
        }

        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeFile(any()) } returns sourceBitmap

        val outputFile = File.createTempFile("test", ".jpg")
        val config = ShareConfig(
            format = ImageFormat.JPEG,
            quality = 80,
            maxWidth = 1000,
            maxHeight = 1000,
            stripMetadata = true,
        )

        val result = imageProcessor.processImage("/test/path.jpg", config, outputFile)

        assertThat(result).isInstanceOf(ImageProcessor.ProcessResult.Success::class.java)
        val success = result as ImageProcessor.ProcessResult.Success
        assertThat(success.width).isEqualTo(500)
        assertThat(success.height).isEqualTo(500)

        unmockkStatic(BitmapFactory::class)
        outputFile.delete()
    }

    @Test
    fun `processImage with null max dimensions uses original size`() {
        val inputBitmap = createRealBitmap(800, 600)
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeFile(any()) } returns inputBitmap

        val outputFile = File.createTempFile("test", ".jpg")
        val config = ShareConfig(
            format = ImageFormat.JPEG,
            quality = 80,
            maxWidth = null,
            maxHeight = null,
        )

        val result = imageProcessor.processImage("/test/path.jpg", config, outputFile)

        assertThat(result).isInstanceOf(ImageProcessor.ProcessResult.Success::class.java)
        val success = result as ImageProcessor.ProcessResult.Success
        assertThat(success.width).isEqualTo(800)
        assertThat(success.height).isEqualTo(600)

        unmockkStatic(BitmapFactory::class)
        outputFile.delete()
        inputBitmap.recycle()
    }

    @Test
    fun `processImage preserves aspect ratio in result dimensions`() {
        val inputBitmap = createRealBitmap(1920, 1080)
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeFile(any()) } returns inputBitmap

        val outputFile = File.createTempFile("test", ".jpg")
        val config = ShareConfig(
            format = ImageFormat.JPEG,
            quality = 80,
            maxWidth = 800,
            maxHeight = 800,
        )

        val result = imageProcessor.processImage("/test/path.jpg", config, outputFile)

        assertThat(result).isInstanceOf(ImageProcessor.ProcessResult.Success::class.java)
        val success = result as ImageProcessor.ProcessResult.Success
        assertThat(success.width).isEqualTo(800)
        assertThat(success.height).isEqualTo(450)

        unmockkStatic(BitmapFactory::class)
        outputFile.delete()
    }

    @Test
    fun `processImage returns positive file size on success`() {
        val inputBitmap = createRealBitmap(100, 100)
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeFile(any()) } returns inputBitmap

        val outputFile = File.createTempFile("test", ".jpg")
        val config = ShareConfig(
            format = ImageFormat.JPEG,
            quality = 80,
        )

        val result = imageProcessor.processImage("/test/path.jpg", config, outputFile)

        assertThat(result).isInstanceOf(ImageProcessor.ProcessResult.Success::class.java)
        val success = result as ImageProcessor.ProcessResult.Success
        assertThat(success.fileSize).isGreaterThan(0L)

        unmockkStatic(BitmapFactory::class)
        outputFile.delete()
        inputBitmap.recycle()
    }

    // endregion

    // region compressToFormat Tests

    @Test
    fun `compressToFormat saves JPEG file`() {
        val bitmap = createRealBitmap(100, 100)
        val outputFile = File.createTempFile("test", ".jpg")

        val result = imageProcessor.compressToFormat(
            bitmap = bitmap,
            outputFile = outputFile,
            format = ImageFormat.JPEG,
            quality = 80,
        )

        assertThat(result).isTrue()
        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0)

        outputFile.delete()
        bitmap.recycle()
    }

    @Test
    fun `compressToFormat saves PNG file`() {
        val bitmap = createRealBitmap(100, 100)
        val outputFile = File.createTempFile("test", ".png")

        val result = imageProcessor.compressToFormat(
            bitmap = bitmap,
            outputFile = outputFile,
            format = ImageFormat.PNG,
            quality = 100,
        )

        assertThat(result).isTrue()
        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0)

        outputFile.delete()
        bitmap.recycle()
    }

    @Test
    fun `compressToFormat saves WEBP file`() {
        val bitmap = createRealBitmap(100, 100)
        val outputFile = File.createTempFile("test", ".webp")

        val result = imageProcessor.compressToFormat(
            bitmap = bitmap,
            outputFile = outputFile,
            format = ImageFormat.WEBP,
            quality = 85,
        )

        assertThat(result).isTrue()
        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0)

        outputFile.delete()
        bitmap.recycle()
    }

    // endregion

    // region stripExifData Tests

    @Test
    fun `stripExifData does not crash on valid file`() {
        val bitmap = createRealBitmap(100, 100)
        val outputFile = File.createTempFile("test", ".jpg")
        imageProcessor.compressToFormat(bitmap, outputFile, ImageFormat.JPEG, 80)

        // Should not throw
        imageProcessor.stripExifData(outputFile.absolutePath)

        assertThat(outputFile.exists()).isTrue()

        outputFile.delete()
        bitmap.recycle()
    }

    @Test
    fun `stripExifData handles nonexistent file gracefully`() {
        // Should not throw
        imageProcessor.stripExifData("/nonexistent/file.jpg")
    }

    // endregion

    // region Helper Functions

    private fun createRealBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    // endregion
}
