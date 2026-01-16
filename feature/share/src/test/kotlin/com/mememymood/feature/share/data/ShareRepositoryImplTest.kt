package com.mememymood.feature.share.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.database.dao.MemeDao
import com.mememymood.core.database.entity.MemeEntity
import com.mememymood.core.datastore.PreferencesDataStore
import com.mememymood.core.ml.XmpMetadataHandler
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.ImageFormat
import com.mememymood.core.model.ShareConfig
import com.mememymood.core.model.SharingPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for ShareRepositoryImpl.
 * Uses Robolectric for Android framework mocking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ShareRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var memeDao: MemeDao
    private lateinit var preferencesDataStore: PreferencesDataStore
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var xmpMetadataHandler: XmpMetadataHandler
    private lateinit var repository: ShareRepositoryImpl

    private val mockCacheDir = mockk<File>(relaxed = true)

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        memeDao = mockk()
        preferencesDataStore = mockk()
        imageProcessor = mockk()
        xmpMetadataHandler = mockk(relaxed = true)

        every { context.cacheDir } returns mockCacheDir
        every { mockCacheDir.absolutePath } returns "/cache"

        repository = ShareRepositoryImpl(
            context = context,
            memeDao = memeDao,
            preferencesDataStore = preferencesDataStore,
            imageProcessor = imageProcessor,
            xmpMetadataHandler = xmpMetadataHandler,
        )
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    // region getMeme Tests

    @Test
    fun `getMeme returns meme when entity exists`() = runTest {
        val entity = createTestMemeEntity(1L)
        coEvery { memeDao.getMemeById(1L) } returns entity

        val result = repository.getMeme(1L)

        assertThat(result).isNotNull()
        assertThat(result?.id).isEqualTo(1L)
        assertThat(result?.fileName).isEqualTo("meme_1.jpg")
    }

    @Test
    fun `getMeme returns null when entity does not exist`() = runTest {
        coEvery { memeDao.getMemeById(999L) } returns null

        val result = repository.getMeme(999L)

        assertThat(result).isNull()
    }

    // endregion

    // region getDefaultShareConfig Tests

    @Test
    fun `getDefaultShareConfig returns preferences from data store`() = runTest {
        val preferences = SharingPreferences(
            defaultFormat = ImageFormat.PNG,
            defaultQuality = 85,
            stripMetadata = true,
            addWatermark = false,
        )
        every { preferencesDataStore.sharingPreferences } returns flowOf(preferences)

        val result = repository.getDefaultShareConfig()

        assertThat(result.format).isEqualTo(ImageFormat.PNG)
        assertThat(result.quality).isEqualTo(85)
        assertThat(result.stripMetadata).isTrue()
        assertThat(result.addWatermark).isFalse()
    }

    @Test
    fun `getDefaultShareConfig uses defaults when datastore is empty`() = runTest {
        val preferences = SharingPreferences()
        every { preferencesDataStore.sharingPreferences } returns flowOf(preferences)

        val result = repository.getDefaultShareConfig()

        assertThat(result.format).isEqualTo(ImageFormat.WEBP)
        assertThat(result.quality).isEqualTo(85)
    }

    // endregion

    // region createShareIntent Tests

    @Test
    fun `createShareIntent creates intent with correct action`() {
        val uri = Uri.parse("content://test/image.jpg")

        val intent = repository.createShareIntent(uri, "image/jpeg")

        assertThat(intent.action).isEqualTo(Intent.ACTION_SEND)
    }

    @Test
    fun `createShareIntent creates intent with correct type`() {
        val uri = Uri.parse("content://test/image.jpg")

        val intent = repository.createShareIntent(uri, "image/jpeg")

        assertThat(intent.type).isEqualTo("image/jpeg")
    }

    @Test
    fun `createShareIntent includes URI as extra stream`() {
        val uri = Uri.parse("content://test/image.png")

        val intent = repository.createShareIntent(uri, "image/png")

        assertThat(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)).isEqualTo(uri)
    }

    @Test
    fun `createShareIntent adds read URI permission flag`() {
        val uri = Uri.parse("content://test/image.webp")

        val intent = repository.createShareIntent(uri, "image/webp")

        assertThat(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
    }

    // endregion

    // region Helper Functions

    private fun createTestMemeEntity(
        id: Long,
        width: Int = 1080,
        height: Int = 1920
    ): MemeEntity {
        return MemeEntity(
            id = id,
            filePath = "/test/path/meme_$id.jpg",
            fileName = "meme_$id.jpg",
            mimeType = "image/jpeg",
            width = width,
            height = height,
            fileSizeBytes = 100_000,
            importedAt = System.currentTimeMillis(),
            emojiTagsJson = """[{"emoji":"😂","name":"joy"}]""",
            title = "Test Meme $id",
            description = "Description for meme $id",
            textContent = null,
            embedding = null,
            isFavorite = false,
        )
    }

    // endregion
}
