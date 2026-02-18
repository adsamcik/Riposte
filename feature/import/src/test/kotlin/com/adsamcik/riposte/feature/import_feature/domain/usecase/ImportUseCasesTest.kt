package com.adsamcik.riposte.feature.import_feature.domain.usecase

import android.net.Uri
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.MemeMetadata
import com.adsamcik.riposte.feature.import_feature.domain.repository.ImportRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ImportUseCasesTest {
    private lateinit var repository: ImportRepository
    private lateinit var importImageUseCase: ImportImageUseCase
    private lateinit var importImagesUseCase: ImportImagesUseCase
    private lateinit var extractMetadataUseCase: ExtractMetadataUseCase
    private lateinit var suggestEmojisUseCase: SuggestEmojisUseCase
    private lateinit var extractTextUseCase: ExtractTextUseCase

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        importImageUseCase = ImportImageUseCase(repository)
        importImagesUseCase = ImportImagesUseCase(repository)
        extractMetadataUseCase = ExtractMetadataUseCase(repository)
        suggestEmojisUseCase = SuggestEmojisUseCase(repository)
        extractTextUseCase = ExtractTextUseCase(repository)
    }

    // ImportImageUseCase tests

    @Test
    fun `ImportImageUseCase calls repository with uri and null metadata`() =
        runTest {
            val uri = mockk<Uri>()
            val meme = mockk<Meme>()
            coEvery { repository.importImage(uri, null) } returns Result.success(meme)

            val result = importImageUseCase(uri)

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo(meme)
            coVerify { repository.importImage(uri, null) }
        }

    @Test
    fun `ImportImageUseCase calls repository with uri and metadata`() =
        runTest {
            val uri = mockk<Uri>()
            val metadata = mockk<MemeMetadata>()
            val meme = mockk<Meme>()
            coEvery { repository.importImage(uri, metadata) } returns Result.success(meme)

            val result = importImageUseCase(uri, metadata)

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo(meme)
            coVerify { repository.importImage(uri, metadata) }
        }

    @Test
    fun `ImportImageUseCase returns failure when repository fails`() =
        runTest {
            val uri = mockk<Uri>()
            val error = RuntimeException("Import failed")
            coEvery { repository.importImage(uri, null) } returns Result.failure(error)

            val result = importImageUseCase(uri)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isEqualTo(error)
        }

    // ImportImagesUseCase tests

    @Test
    fun `ImportImagesUseCase calls repository with list of uris`() =
        runTest {
            val uri1 = mockk<Uri>()
            val uri2 = mockk<Uri>()
            val meme1 = mockk<Meme>()
            val meme2 = mockk<Meme>()
            coEvery { repository.importImages(listOf(uri1, uri2)) } returns
                listOf(
                    Result.success(meme1),
                    Result.success(meme2),
                )

            val results = importImagesUseCase(listOf(uri1, uri2))

            assertThat(results).hasSize(2)
            assertThat(results[0].getOrNull()).isEqualTo(meme1)
            assertThat(results[1].getOrNull()).isEqualTo(meme2)
        }

    @Test
    fun `ImportImagesUseCase handles partial failures`() =
        runTest {
            val uri1 = mockk<Uri>()
            val uri2 = mockk<Uri>()
            val meme1 = mockk<Meme>()
            val error = RuntimeException("Import failed")
            coEvery { repository.importImages(listOf(uri1, uri2)) } returns
                listOf(
                    Result.success(meme1),
                    Result.failure(error),
                )

            val results = importImagesUseCase(listOf(uri1, uri2))

            assertThat(results).hasSize(2)
            assertThat(results[0].isSuccess).isTrue()
            assertThat(results[1].isFailure).isTrue()
        }

    @Test
    fun `ImportImagesUseCase handles empty list`() =
        runTest {
            coEvery { repository.importImages(emptyList()) } returns emptyList()

            val results = importImagesUseCase(emptyList())

            assertThat(results).isEmpty()
        }

    // ExtractMetadataUseCase tests

    @Test
    fun `ExtractMetadataUseCase returns metadata from repository`() =
        runTest {
            val uri = mockk<Uri>()
            val metadata =
                MemeMetadata(
                    emojis = listOf("😀"),
                    title = "Test",
                )
            coEvery { repository.extractMetadata(uri) } returns metadata

            val result = extractMetadataUseCase(uri)

            assertThat(result).isEqualTo(metadata)
            coVerify { repository.extractMetadata(uri) }
        }

    @Test
    fun `ExtractMetadataUseCase returns null when no metadata`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { repository.extractMetadata(uri) } returns null

            val result = extractMetadataUseCase(uri)

            assertThat(result).isNull()
        }

    // SuggestEmojisUseCase tests

    @Test
    fun `SuggestEmojisUseCase returns suggested emojis`() =
        runTest {
            val uri = mockk<Uri>()
            val emojis =
                listOf(
                    EmojiTag("😀", "happy"),
                    EmojiTag("😂", "laughing"),
                )
            coEvery { repository.suggestEmojis(uri) } returns emojis

            val result = suggestEmojisUseCase(uri)

            assertThat(result).isEqualTo(emojis)
            coVerify { repository.suggestEmojis(uri) }
        }

    @Test
    fun `SuggestEmojisUseCase returns empty list when no suggestions`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { repository.suggestEmojis(uri) } returns emptyList()

            val result = suggestEmojisUseCase(uri)

            assertThat(result).isEmpty()
        }

    // ExtractTextUseCase tests

    @Test
    fun `ExtractTextUseCase returns extracted text`() =
        runTest {
            val uri = mockk<Uri>()
            val text = "Hello World"
            coEvery { repository.extractText(uri) } returns text

            val result = extractTextUseCase(uri)

            assertThat(result).isEqualTo(text)
            coVerify { repository.extractText(uri) }
        }

    @Test
    fun `ExtractTextUseCase returns null when no text found`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { repository.extractText(uri) } returns null

            val result = extractTextUseCase(uri)

            assertThat(result).isNull()
        }

    @Test
    fun `ExtractTextUseCase returns empty string for blank text`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { repository.extractText(uri) } returns ""

            val result = extractTextUseCase(uri)

            assertThat(result).isEmpty()
        }

    // FindDuplicateMemeIdUseCase tests

    @Test
    fun `FindDuplicateMemeIdUseCase returns meme id when duplicate found`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { repository.findDuplicateMemeId(uri) } returns 42L

            val findDuplicateMemeIdUseCase = FindDuplicateMemeIdUseCase(repository)
            val result = findDuplicateMemeIdUseCase(uri)

            assertThat(result).isEqualTo(42L)
            coVerify { repository.findDuplicateMemeId(uri) }
        }

    @Test
    fun `FindDuplicateMemeIdUseCase returns null when not a duplicate`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { repository.findDuplicateMemeId(uri) } returns null

            val findDuplicateMemeIdUseCase = FindDuplicateMemeIdUseCase(repository)
            val result = findDuplicateMemeIdUseCase(uri)

            assertThat(result).isNull()
        }

    // CheckDuplicateUseCase tests

    @Test
    fun `CheckDuplicateUseCase returns true when repository detects duplicate`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { repository.isDuplicate(uri) } returns true

            val checkDuplicateUseCase = CheckDuplicateUseCase(repository)
            val result = checkDuplicateUseCase(uri)

            assertThat(result).isTrue()
            coVerify { repository.isDuplicate(uri) }
        }

    @Test
    fun `CheckDuplicateUseCase returns false when not a duplicate`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { repository.isDuplicate(uri) } returns false

            val checkDuplicateUseCase = CheckDuplicateUseCase(repository)
            val result = checkDuplicateUseCase(uri)

            assertThat(result).isFalse()
        }

    @Test
    fun `CheckDuplicateUseCase delegates to repository isDuplicate`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { repository.isDuplicate(uri) } returns false

            val checkDuplicateUseCase = CheckDuplicateUseCase(repository)
            checkDuplicateUseCase(uri)

            coVerify(exactly = 1) { repository.isDuplicate(uri) }
        }

    // UpdateMemeMetadataUseCase tests

    @Test
    fun `UpdateMemeMetadataUseCase calls repository and returns success`() =
        runTest {
            val metadata =
                MemeMetadata(
                    emojis = listOf("😂", "🔥"),
                    title = "Updated Title",
                    description = "Updated Description",
                )
            coEvery { repository.updateMemeMetadata(42L, metadata) } returns Result.success(Unit)

            val updateMemeMetadataUseCase = UpdateMemeMetadataUseCase(repository)
            val result = updateMemeMetadataUseCase(42L, metadata)

            assertThat(result.isSuccess).isTrue()
            coVerify { repository.updateMemeMetadata(42L, metadata) }
        }

    @Test
    fun `UpdateMemeMetadataUseCase returns failure when repository fails`() =
        runTest {
            val metadata = MemeMetadata(emojis = listOf("😂"), title = "Test")
            val error = RuntimeException("Meme not found")
            coEvery { repository.updateMemeMetadata(99L, metadata) } returns Result.failure(error)

            val updateMemeMetadataUseCase = UpdateMemeMetadataUseCase(repository)
            val result = updateMemeMetadataUseCase(99L, metadata)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isEqualTo(error)
        }
}
