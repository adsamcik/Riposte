package com.adsamcik.riposte.feature.import_feature.domain.usecase

import android.net.Uri
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.MemeMetadata
import com.adsamcik.riposte.feature.import_feature.data.ExtractedMeme
import com.adsamcik.riposte.feature.import_feature.data.ZipExtractionResult
import com.adsamcik.riposte.feature.import_feature.domain.ZipImporter
import com.adsamcik.riposte.feature.import_feature.domain.repository.ImportRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ImportZipBundleUseCase].
 */
class ImportZipBundleUseCaseTest {
    private lateinit var useCase: ImportZipBundleUseCase
    private lateinit var repository: ImportRepository
    private lateinit var zipImporter: ZipImporter

    @Before
    fun setup() {
        repository = mockk()
        zipImporter = mockk()
        useCase = ImportZipBundleUseCase(repository, zipImporter)
    }

    @Test
    fun `returns error when not a valid meme zip bundle`() =
        runTest {
            val zipUri = mockk<Uri>()
            every { zipImporter.isMemeZipBundle(zipUri) } returns false

            val result = useCase(zipUri)

            assertEquals(0, result.successCount)
            assertEquals(1, result.failureCount)
            assertEquals("Not a valid .meme.zip bundle", result.errors["bundle"])
        }

    @Test
    fun `imports extracted memes successfully`() =
        runTest {
            val zipUri = mockk<Uri>()
            val imageUri = mockk<Uri>()
            val metadata = MemeMetadata(emojis = listOf("😂"))
            val meme = mockk<Meme>()

            every { zipImporter.isMemeZipBundle(zipUri) } returns true
            coEvery { zipImporter.extractBundle(zipUri) } returns
                ZipExtractionResult(
                    extractedMemes = listOf(ExtractedMeme(imageUri, metadata)),
                    errors = emptyMap(),
                )
            coEvery { repository.importImage(imageUri, metadata) } returns Result.success(meme)
            every { zipImporter.cleanupExtractedFiles() } returns Unit

            val result = useCase(zipUri)

            assertEquals(1, result.successCount)
            assertEquals(0, result.failureCount)
            assertEquals(listOf(meme), result.importedMemes)
        }

    @Test
    fun `handles import failures gracefully`() =
        runTest {
            val zipUri = mockk<Uri>()
            val imageUri =
                mockk<Uri> {
                    every { lastPathSegment } returns "test.jpg"
                }

            every { zipImporter.isMemeZipBundle(zipUri) } returns true
            coEvery { zipImporter.extractBundle(zipUri) } returns
                ZipExtractionResult(
                    extractedMemes = listOf(ExtractedMeme(imageUri, null)),
                    errors = emptyMap(),
                )
            coEvery { repository.importImage(imageUri, null) } returns
                Result.failure(
                    Exception("Import failed"),
                )
            every { zipImporter.cleanupExtractedFiles() } returns Unit

            val result = useCase(zipUri)

            assertEquals(0, result.successCount)
            assertEquals(1, result.failureCount)
            assertEquals("Import failed", result.errors["test.jpg"])
        }

    @Test
    fun `includes extraction errors in result`() =
        runTest {
            val zipUri = mockk<Uri>()

            every { zipImporter.isMemeZipBundle(zipUri) } returns true
            coEvery { zipImporter.extractBundle(zipUri) } returns
                ZipExtractionResult(
                    extractedMemes = emptyList(),
                    errors = mapOf("corrupt.jpg" to "Failed to extract"),
                )
            every { zipImporter.cleanupExtractedFiles() } returns Unit

            val result = useCase(zipUri)

            assertEquals(0, result.successCount)
            assertEquals(1, result.failureCount)
            assertEquals("Failed to extract", result.errors["corrupt.jpg"])
        }

    @Test
    fun `cleans up temp files after successful import`() =
        runTest {
            val zipUri = mockk<Uri>()
            val imageUri = mockk<Uri>()
            val meme = mockk<Meme>()

            every { zipImporter.isMemeZipBundle(zipUri) } returns true
            coEvery { zipImporter.extractBundle(zipUri) } returns
                ZipExtractionResult(
                    extractedMemes = listOf(ExtractedMeme(imageUri, null)),
                    errors = emptyMap(),
                )
            coEvery { repository.importImage(imageUri, null) } returns Result.success(meme)
            every { zipImporter.cleanupExtractedFiles() } returns Unit

            useCase(zipUri)

            verify { zipImporter.cleanupExtractedFiles() }
        }

    @Test
    fun `cleans up temp files even when import throws exception`() =
        runTest {
            val zipUri = mockk<Uri>()
            val imageUri = mockk<Uri>()

            every { zipImporter.isMemeZipBundle(zipUri) } returns true
            coEvery { zipImporter.extractBundle(zipUri) } returns
                ZipExtractionResult(
                    extractedMemes = listOf(ExtractedMeme(imageUri, null)),
                    errors = emptyMap(),
                )
            coEvery { repository.importImage(imageUri, null) } throws RuntimeException("Unexpected error")
            every { zipImporter.cleanupExtractedFiles() } returns Unit

            try {
                useCase(zipUri)
            } catch (_: RuntimeException) {
                // Expected
            }

            verify { zipImporter.cleanupExtractedFiles() }
        }
}
