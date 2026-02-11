package com.adsamcik.riposte.core.ml

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.common.truth.Truth.assertThat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.google.mlkit.vision.text.TextRecognizer as MlKitTextRecognizer

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class MlKitTextRecognizerTest {
    @MockK
    private lateinit var mockContext: Context

    @MockK
    private lateinit var mockContentResolver: ContentResolver

    @MockK
    private lateinit var mockMlKitRecognizer: MlKitTextRecognizer

    @MockK
    private lateinit var mockBitmap: Bitmap

    @MockK
    private lateinit var mockInputImage: InputImage

    private lateinit var textRecognizer: com.adsamcik.riposte.core.ml.MlKitTextRecognizer

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        every { mockContext.contentResolver } returns mockContentResolver

        // Mock the TextRecognition.getClient() static call BEFORE creating the instance
        mockkStatic(TextRecognition::class)
        every {
            TextRecognition.getClient(any<com.google.mlkit.vision.text.latin.TextRecognizerOptions>())
        } returns mockMlKitRecognizer

        // Mock InputImage static factory methods
        mockkStatic(InputImage::class)
        every { InputImage.fromBitmap(any(), any()) } returns mockInputImage
        every { InputImage.fromFilePath(any(), any()) } returns mockInputImage

        textRecognizer = com.adsamcik.riposte.core.ml.MlKitTextRecognizer(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== recognizeText(Bitmap) Tests ====================

    @Test
    fun `recognizeText from bitmap returns extracted text`() =
        runTest {
            val expectedText = "Hello World"
            setupSuccessfulTextRecognition(expectedText)

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isEqualTo(expectedText)
        }

    @Test
    fun `recognizeText from bitmap returns null for empty text`() =
        runTest {
            setupSuccessfulTextRecognition("")

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isNull()
        }

    @Test
    fun `recognizeText from bitmap returns null for blank text`() =
        runTest {
            setupSuccessfulTextRecognition("   ")

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isNull()
        }

    @Test
    fun `recognizeText from bitmap returns text for whitespace-padded content`() =
        runTest {
            setupSuccessfulTextRecognition("  Hello  ")

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isEqualTo("  Hello  ")
        }

    @Test
    fun `recognizeText from bitmap handles multiline text`() =
        runTest {
            val multilineText = "Line 1\nLine 2\nLine 3"
            setupSuccessfulTextRecognition(multilineText)

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isEqualTo(multilineText)
        }

    @Test
    fun `recognizeText from bitmap handles special characters`() =
        runTest {
            val specialText = "Hello! @#$%^&*() 123"
            setupSuccessfulTextRecognition(specialText)

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isEqualTo(specialText)
        }

    @Test
    fun `recognizeText from bitmap handles unicode characters`() =
        runTest {
            val unicodeText = "Hello 世界 🌍 Привет"
            setupSuccessfulTextRecognition(unicodeText)

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isEqualTo(unicodeText)
        }

    @Test(expected = Exception::class)
    fun `recognizeText from bitmap throws on failure`() =
        runTest {
            setupFailedTextRecognition(Exception("Recognition failed"))

            textRecognizer.recognizeText(mockBitmap)
        }

    @Test(expected = IllegalStateException::class)
    fun `recognizeText from bitmap throws IllegalStateException on illegal state`() =
        runTest {
            setupFailedTextRecognition(IllegalStateException("Model not downloaded"))

            textRecognizer.recognizeText(mockBitmap)
        }

    @Test(expected = RuntimeException::class)
    fun `recognizeText from bitmap throws RuntimeException on runtime error`() =
        runTest {
            setupFailedTextRecognition(RuntimeException("Internal error"))

            textRecognizer.recognizeText(mockBitmap)
        }

    // ==================== recognizeText(Uri) Tests ====================

    @Test
    fun `recognizeText from URI returns extracted text`() =
        runTest {
            val mockUri = mockk<Uri>()
            val expectedText = "Text from image"

            mockkStatic(InputImage::class)
            every { InputImage.fromFilePath(mockContext, mockUri) } returns mockk()

            setupSuccessfulTextRecognition(expectedText)

            val result = textRecognizer.recognizeText(mockUri)

            assertThat(result).isEqualTo(expectedText)

            unmockkStatic(InputImage::class)
        }

    @Test
    fun `recognizeText from URI returns null for image without text`() =
        runTest {
            val mockUri = mockk<Uri>()

            mockkStatic(InputImage::class)
            every { InputImage.fromFilePath(mockContext, mockUri) } returns mockk()

            setupSuccessfulTextRecognition("")

            val result = textRecognizer.recognizeText(mockUri)

            assertThat(result).isNull()

            unmockkStatic(InputImage::class)
        }

    @Test(expected = Exception::class)
    fun `recognizeText from URI throws on invalid URI`() =
        runTest {
            val mockUri = mockk<Uri>()

            mockkStatic(InputImage::class)
            every { InputImage.fromFilePath(mockContext, mockUri) } throws Exception("Invalid URI")

            textRecognizer.recognizeText(mockUri)

            unmockkStatic(InputImage::class)
        }

    @Test(expected = SecurityException::class)
    fun `recognizeText from URI throws SecurityException on permission denied`() =
        runTest {
            val mockUri = mockk<Uri>()

            mockkStatic(InputImage::class)
            every { InputImage.fromFilePath(mockContext, mockUri) } throws SecurityException("Permission denied")

            textRecognizer.recognizeText(mockUri)

            unmockkStatic(InputImage::class)
        }

    // ==================== Images with Various Text Scenarios ====================

    @Test
    fun `recognizeText handles image with single word`() =
        runTest {
            setupSuccessfulTextRecognition("Hello")

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isEqualTo("Hello")
        }

    @Test
    fun `recognizeText handles image with long paragraph`() =
        runTest {
            val longText =
                "This is a very long paragraph that contains multiple sentences. " +
                    "It should be recognized correctly by the OCR engine. " +
                    "This tests the ability to handle larger amounts of text."
            setupSuccessfulTextRecognition(longText)

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isEqualTo(longText)
        }

    @Test
    fun `recognizeText handles image with numbers only`() =
        runTest {
            setupSuccessfulTextRecognition("1234567890")

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isEqualTo("1234567890")
        }

    @Test
    fun `recognizeText handles image with mixed content`() =
        runTest {
            val mixedText = "Price: $99.99\nDate: 01/15/2024\nCode: ABC-123"
            setupSuccessfulTextRecognition(mixedText)

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isEqualTo(mixedText)
        }

    @Test
    fun `recognizeText handles typical meme text format`() =
        runTest {
            val memeText = "TOP TEXT\n\nBOTTOM TEXT"
            setupSuccessfulTextRecognition(memeText)

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isEqualTo(memeText)
        }

    @Test
    fun `recognizeText handles all caps text`() =
        runTest {
            val capsText = "WHEN YOU REALIZE IT'S MONDAY"
            setupSuccessfulTextRecognition(capsText)

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isEqualTo(capsText)
        }

    // ==================== State Tests ====================

    @Test
    fun `isReady returns true`() {
        val result = textRecognizer.isReady()

        assertThat(result).isTrue()
    }

    @Test
    fun `close closes the ML Kit recognizer`() {
        textRecognizer.close()

        verify { mockMlKitRecognizer.close() }
    }

    @Test
    fun `multiple recognizeText calls use same recognizer instance`() =
        runTest {
            setupSuccessfulTextRecognition("Text 1")
            textRecognizer.recognizeText(mockBitmap)

            setupSuccessfulTextRecognition("Text 2")
            textRecognizer.recognizeText(mockBitmap)

            // Verify that process was called twice on the same recognizer
            verify(exactly = 2) { mockMlKitRecognizer.process(any<InputImage>()) }
        }

    // ==================== Edge Case Tests ====================

    @Test
    fun `recognizeText handles text with emojis`() =
        runTest {
            val emojiText = "I love 😂 memes 🔥"
            setupSuccessfulTextRecognition(emojiText)

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isEqualTo(emojiText)
        }

    @Test
    fun `recognizeText handles text with only newlines`() =
        runTest {
            setupSuccessfulTextRecognition("\n\n\n")

            val result = textRecognizer.recognizeText(mockBitmap)

            // Newlines only should be considered blank
            assertThat(result).isNull()
        }

    @Test
    fun `recognizeText handles text with tabs`() =
        runTest {
            val tabbedText = "Column1\tColumn2\tColumn3"
            setupSuccessfulTextRecognition(tabbedText)

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isEqualTo(tabbedText)
        }

    @Test
    fun `recognizeText handles very long text`() =
        runTest {
            val longText = "word ".repeat(1000)
            setupSuccessfulTextRecognition(longText)

            val result = textRecognizer.recognizeText(mockBitmap)

            assertThat(result).isEqualTo(longText)
        }

    // ==================== Concurrent Call Tests ====================

    @Test
    fun `recognizeText handles rapid successive calls`() =
        runTest {
            setupSuccessfulTextRecognition("Text")

            // Make multiple rapid calls
            val results =
                (1..5).map {
                    textRecognizer.recognizeText(mockBitmap)
                }

            results.forEach { result ->
                assertThat(result).isEqualTo("Text")
            }
        }

    // ==================== Error Handling Tests ====================

    @Test(expected = RuntimeException::class)
    fun `recognizeText propagates RuntimeException from ML Kit`() =
        runTest {
            setupFailedTextRecognition(RuntimeException("Internal ML Kit error"))

            textRecognizer.recognizeText(mockBitmap)
        }

    // ==================== Helper Functions ====================

    private fun setupSuccessfulTextRecognition(resultText: String) {
        val mockText = mockk<Text>()
        every { mockText.text } returns resultText

        val mockTask = mockk<Task<Text>>(relaxed = true)
        every { mockMlKitRecognizer.process(any<InputImage>()) } returns mockTask

        every { mockTask.addOnSuccessListener(any<OnSuccessListener<Text>>()) } answers {
            val listener = firstArg<OnSuccessListener<Text>>()
            listener.onSuccess(mockText)
            mockTask
        }

        every { mockTask.addOnFailureListener(any<OnFailureListener>()) } returns mockTask
    }

    private fun setupFailedTextRecognition(exception: Throwable) {
        val mockTask = mockk<Task<Text>>(relaxed = true)
        every { mockMlKitRecognizer.process(any<InputImage>()) } returns mockTask

        every { mockTask.addOnSuccessListener(any<OnSuccessListener<Text>>()) } returns mockTask

        every { mockTask.addOnFailureListener(any<OnFailureListener>()) } answers {
            val listener = firstArg<OnFailureListener>()
            listener.onFailure(exception as Exception)
            mockTask
        }
    }
}
