package com.mememymood.feature.import_feature.domain.usecase

import com.mememymood.core.model.Meme
import com.mememymood.core.model.MemeMetadata
import com.mememymood.feature.import_feature.data.ExtractedMeme
import com.mememymood.feature.import_feature.data.ZipImporter
import com.mememymood.feature.import_feature.data.ZipStreamingEvent
import com.mememymood.feature.import_feature.domain.repository.ImportRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ImportZipBundleStreamingUseCase] focusing on cancellation and cleanup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportZipBundleStreamingUseCaseTest {

    private lateinit var useCase: ImportZipBundleStreamingUseCase
    private lateinit var repository: ImportRepository
    private lateinit var zipImporter: ZipImporter

    // Sample image bytes for testing
    private val testImageBytes = ByteArray(100) { it.toByte() }

    @Before
    fun setup() {
        repository = mockk()
        zipImporter = mockk()
        useCase = ImportZipBundleStreamingUseCase(repository, zipImporter)
    }

    @Test
    fun `returns error flow when not a valid meme zip bundle`() = runTest {
        val zipUri = mockk<android.net.Uri>()
        every { zipImporter.isMemeZipBundle(zipUri) } returns false
        every { zipImporter.cleanupExtractedFiles() } just runs

        val results = useCase(zipUri).toList()

        assertEquals(2, results.size)
        assertTrue(results[0] is ZipBundleImportProgress.Error)
        assertTrue(results[1] is ZipBundleImportProgress.Complete)
        
        val complete = results[1] as ZipBundleImportProgress.Complete
        assertEquals(0, complete.result.successCount)
        assertEquals(1, complete.result.failureCount)
    }

    @Test
    fun `imports extracted memes and emits progress`() = runTest {
        val zipUri = mockk<android.net.Uri>()
        val metadata = MemeMetadata(emojis = listOf("😂"))
        val extractedMeme = ExtractedMeme(
            imageBytes = testImageBytes,
            imageName = "test.jpg",
            metadata = metadata,
        )
        val meme = mockk<Meme>()

        every { zipImporter.isMemeZipBundle(zipUri) } returns true
        every { zipImporter.extractBundleSinglePass(zipUri) } returns flow {
            emit(ZipStreamingEvent.MemeReady(extractedMeme, 1))
            emit(ZipStreamingEvent.Complete(totalExtracted = 1, totalErrors = 0))
        }
        coEvery { repository.importImageFromBytes(testImageBytes, "test.jpg", metadata) } returns Result.success(meme)
        every { zipImporter.cleanupExtractedFiles() } just runs

        val results = useCase(zipUri).toList()

        assertTrue(results.any { it is ZipBundleImportProgress.Importing })
        assertTrue(results.any { it is ZipBundleImportProgress.MemeImported })
        assertTrue(results.any { it is ZipBundleImportProgress.Complete })
        
        val complete = results.filterIsInstance<ZipBundleImportProgress.Complete>().first()
        assertEquals(1, complete.result.successCount)
        assertEquals(0, complete.result.failureCount)
    }

    @Test
    fun `handles import failures gracefully`() = runTest {
        val zipUri = mockk<android.net.Uri>()
        val extractedMeme = ExtractedMeme(
            imageBytes = testImageBytes,
            imageName = "test.jpg",
            metadata = null,
        )

        every { zipImporter.isMemeZipBundle(zipUri) } returns true
        every { zipImporter.extractBundleSinglePass(zipUri) } returns flow {
            emit(ZipStreamingEvent.MemeReady(extractedMeme, 1))
            emit(ZipStreamingEvent.Complete(totalExtracted = 1, totalErrors = 0))
        }
        coEvery { repository.importImageFromBytes(testImageBytes, "test.jpg", null) } returns Result.failure(
            Exception("Import failed")
        )
        every { zipImporter.cleanupExtractedFiles() } just runs

        val results = useCase(zipUri).toList()

        assertTrue(results.any { it is ZipBundleImportProgress.Error })
        
        val complete = results.filterIsInstance<ZipBundleImportProgress.Complete>().first()
        assertEquals(0, complete.result.successCount)
        assertEquals(1, complete.result.failureCount)
        assertEquals("Import failed", complete.result.errors["test.jpg"])
    }

    @Test
    fun `includes extraction errors in final result`() = runTest {
        val zipUri = mockk<android.net.Uri>()

        every { zipImporter.isMemeZipBundle(zipUri) } returns true
        every { zipImporter.extractBundleSinglePass(zipUri) } returns flow {
            emit(ZipStreamingEvent.Error("corrupt.jpg", "Failed to extract"))
            emit(ZipStreamingEvent.Complete(totalExtracted = 0, totalErrors = 1))
        }
        every { zipImporter.cleanupExtractedFiles() } just runs

        val results = useCase(zipUri).toList()

        assertTrue(results.any { it is ZipBundleImportProgress.Error })
        val complete = results.filterIsInstance<ZipBundleImportProgress.Complete>().first()
        assertEquals(0, complete.result.successCount)
        assertEquals(1, complete.result.failureCount)
        assertEquals("Failed to extract", complete.result.errors["corrupt.jpg"])
    }

    @Test
    fun `cleanup is called on flow cancellation`() = runTest {
        val zipUri = mockk<android.net.Uri>()
        val meme = mockk<Meme>()

        every { zipImporter.isMemeZipBundle(zipUri) } returns true
        every { zipImporter.extractBundleSinglePass(zipUri) } returns flow {
            // Slow extraction to allow cancellation
            repeat(100) { i ->
                delay(100)
                emit(ZipStreamingEvent.MemeReady(
                    ExtractedMeme(
                        imageBytes = testImageBytes,
                        imageName = "test$i.jpg",
                        metadata = null,
                    ),
                    i + 1
                ))
            }
            emit(ZipStreamingEvent.Complete(totalExtracted = 100, totalErrors = 0))
        }
        coEvery { repository.importImageFromBytes(any(), any(), any()) } returns Result.success(meme)
        every { zipImporter.cleanupExtractedFiles() } just runs

        val job = launch {
            useCase(zipUri).collect { /* collecting */ }
        }

        // Let it start
        delay(50)

        // Cancel the flow
        job.cancelAndJoin()

        // Cleanup should still be called due to onCompletion
        verify { zipImporter.cleanupExtractedFiles() }
    }

    @Test
    fun `emits Importing progress events`() = runTest {
        val zipUri = mockk<android.net.Uri>()
        val imageBytes1 = ByteArray(50) { 1 }
        val imageBytes2 = ByteArray(60) { 2 }
        val meme = mockk<Meme>()

        every { zipImporter.isMemeZipBundle(zipUri) } returns true
        every { zipImporter.extractBundleSinglePass(zipUri) } returns flow {
            emit(ZipStreamingEvent.MemeReady(
                ExtractedMeme(imageBytes = imageBytes1, imageName = "img1.jpg", metadata = null),
                1,
            ))
            emit(ZipStreamingEvent.MemeReady(
                ExtractedMeme(imageBytes = imageBytes2, imageName = "img2.jpg", metadata = null),
                2,
            ))
            emit(ZipStreamingEvent.Complete(totalExtracted = 2, totalErrors = 0))
        }
        coEvery { repository.importImageFromBytes(any(), any(), any()) } returns Result.success(meme)
        every { zipImporter.cleanupExtractedFiles() } just runs

        val results = useCase(zipUri).toList()

        val importing = results.filterIsInstance<ZipBundleImportProgress.Importing>()
        assertEquals(2, importing.size)
        assertEquals("img1.jpg", importing[0].fileName)
        assertEquals("img2.jpg", importing[1].fileName)
    }

    @Test
    fun `mixed success and failure results reported correctly`() = runTest {
        val zipUri = mockk<android.net.Uri>()
        val goodImageBytes = ByteArray(70) { 3 }
        val badImageBytes = ByteArray(80) { 4 }
        val meme = mockk<Meme>()

        every { zipImporter.isMemeZipBundle(zipUri) } returns true
        every { zipImporter.extractBundleSinglePass(zipUri) } returns flow {
            emit(ZipStreamingEvent.MemeReady(
                ExtractedMeme(imageBytes = goodImageBytes, imageName = "good.jpg", metadata = null),
                1,
            ))
            emit(ZipStreamingEvent.MemeReady(
                ExtractedMeme(imageBytes = badImageBytes, imageName = "bad.jpg", metadata = null),
                2,
            ))
            emit(ZipStreamingEvent.Complete(totalExtracted = 2, totalErrors = 0))
        }
        coEvery { repository.importImageFromBytes(goodImageBytes, "good.jpg", any()) } returns Result.success(meme)
        coEvery { repository.importImageFromBytes(badImageBytes, "bad.jpg", any()) } returns Result.failure(
            Exception("Failed")
        )
        every { zipImporter.cleanupExtractedFiles() } just runs

        val results = useCase(zipUri).toList()
        val complete = results.filterIsInstance<ZipBundleImportProgress.Complete>().first()

        assertEquals(1, complete.result.successCount)
        assertEquals(1, complete.result.failureCount)
        assertEquals(listOf(meme), complete.result.importedMemes)
        assertEquals("Failed", complete.result.errors["bad.jpg"])
    }
}
