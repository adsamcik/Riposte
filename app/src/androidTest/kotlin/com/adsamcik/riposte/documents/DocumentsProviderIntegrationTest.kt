package com.adsamcik.riposte.documents

import android.content.Context
import android.graphics.BitmapFactory
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.entity.EmojiTagEntity
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * Integration tests for [RiposteDocumentsProvider].
 *
 * Verifies Riposte exposes its meme library to the Android Storage Access
 * Framework correctly, so other apps (Discord, Gmail, etc.) can pick memes
 * via their system file picker without going through ACTION_SEND at all.
 *
 * The provider is protected by `android.permission.MANAGE_DOCUMENTS` which
 * only the system DocumentsUI holds in production. For the test we adopt
 * shell permission identity — the standard way to exercise protected
 * providers from instrumentation.
 *
 * Seeded memes go through the real [MemeDao] so this test catches DAO/entity
 * drift that would silently break picker exposure.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DocumentsProviderIntegrationTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var memeDao: MemeDao

    @Inject
    lateinit var emojiTagDao: EmojiTagDao

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context get() = instrumentation.targetContext

    private val authority: String by lazy { "${targetContext.packageName}.documents" }

    private val seededFiles = mutableListOf<File>()
    private val seededIds = mutableListOf<Long>()

    @Before
    fun setup() {
        hiltRule.inject()
        instrumentation.uiAutomation.adoptShellPermissionIdentity()
    }

    @After
    fun tearDown() {
        if (::emojiTagDao.isInitialized && seededIds.isNotEmpty()) {
            runBlocking {
                seededIds.forEach { emojiTagDao.deleteEmojiTagsForMeme(it) }
            }
        }
        if (::memeDao.isInitialized && seededIds.isNotEmpty()) {
            runBlocking { memeDao.deleteMemesByIds(seededIds.toList()) }
        }
        seededFiles.forEach { it.delete() }
        instrumentation.uiAutomation.dropShellPermissionIdentity()
    }

    @Test
    fun queryRoots_exposes_single_riposte_root() {
        val rootsUri = DocumentsContract.buildRootsUri(authority)
        val seenRoots = mutableListOf<String>()
        targetContext.contentResolver.query(rootsUri, null, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_ROOT_ID)
            while (cursor.moveToNext()) {
                seenRoots.add(cursor.getString(idIdx))
            }
        }
        assertThat(seenRoots).containsExactly("riposte_root")
    }

    @Test
    fun queryChildDocuments_at_root_returns_four_top_level_folders() {
        val childrenUri = DocumentsContract.buildChildDocumentsUri(authority, "root")
        val seenIds = mutableSetOf<String>()
        targetContext.contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                assertThat(cursor.getString(mimeIdx)).isEqualTo(DocumentsContract.Document.MIME_TYPE_DIR)
                seenIds.add(cursor.getString(idIdx))
            }
        }
        assertThat(seenIds).containsExactly("all", "favorites", "recent", "emojis")
    }

    @Test
    fun queryChildDocuments_at_all_exposes_seeded_meme() {
        val seeded = seedMeme(displayName = "test_meme_1.jpg", isFavorite = false)
        val childrenUri = DocumentsContract.buildChildDocumentsUri(authority, "all")
        val docIds = mutableListOf<String>()
        targetContext.contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            while (cursor.moveToNext()) {
                docIds.add(cursor.getString(idIdx))
            }
        }
        assertThat(docIds).contains("meme:${seeded.id}")
    }

    @Test
    fun queryChildDocuments_at_favorites_filters_to_isFavorite() {
        val nonFav = seedMeme(displayName = "non_favorite.jpg", isFavorite = false)
        val fav = seedMeme(displayName = "favorite.jpg", isFavorite = true)
        val childrenUri = DocumentsContract.buildChildDocumentsUri(authority, "favorites")
        val docIds = mutableListOf<String>()
        targetContext.contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            while (cursor.moveToNext()) {
                docIds.add(cursor.getString(idIdx))
            }
        }
        assertThat(docIds).contains("meme:${fav.id}")
        assertThat(docIds).doesNotContain("meme:${nonFav.id}")
    }

    @Test
    fun openDocument_returns_real_meme_bytes() {
        val seeded = seedMeme(displayName = "openable.jpg")
        val docUri = DocumentsContract.buildDocumentUri(authority, "meme:${seeded.id}")
        val bytes =
            targetContext.contentResolver.openInputStream(docUri).use { stream ->
                checkNotNull(stream) { "Could not open document for meme ${seeded.id}" }
                stream.readBytes()
            }
        assertThat(bytes).isEqualTo(TEST_IMAGE_BYTES)
    }

    @Test
    fun querySearchDocuments_filters_by_display_name_substring() {
        val match = seedMeme(displayName = "unique-needle-12345.jpg")
        seedMeme(displayName = "unrelated.jpg")
        val searchUri =
            DocumentsContract.buildSearchDocumentsUri(authority, "riposte_root", "needle-12345")
        // DocumentsUI in production passes the query via QUERY_ARG_DISPLAY_NAME in the
        // ContentResolver Bundle args. Mirror that so we exercise the same code path.
        val queryArgs =
            android.os.Bundle().apply {
                putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, "needle-12345")
            }
        val docIds = mutableListOf<String>()
        targetContext.contentResolver
            .query(searchUri, null, queryArgs, null)
            ?.use { cursor ->
                val idIdx =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                while (cursor.moveToNext()) {
                    docIds.add(cursor.getString(idIdx))
                }
            }
        assertThat(docIds).contains("meme:${match.id}")
    }

    // region Emoji subfolders

    @Test
    fun queryChildDocuments_at_emojis_lists_one_folder_per_distinct_emoji_in_library() {
        // Three memes: two tagged 😂, one tagged 🔥 — expect two emoji folders.
        val meme1 = seedMeme(displayName = "joy_a.jpg")
        val meme2 = seedMeme(displayName = "joy_b.jpg")
        val meme3 = seedMeme(displayName = "fire.jpg")
        seedEmojiTags(meme1.id, "😂" to "joy")
        seedEmojiTags(meme2.id, "😂" to "joy")
        seedEmojiTags(meme3.id, "🔥" to "fire")

        val emojisUri = DocumentsContract.buildChildDocumentsUri(authority, "emojis")
        val docIds = mutableListOf<String>()
        targetContext.contentResolver.query(emojisUri, null, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                assertThat(cursor.getString(mimeIdx)).isEqualTo(DocumentsContract.Document.MIME_TYPE_DIR)
                docIds.add(cursor.getString(idIdx))
            }
        }
        assertThat(docIds).contains("emoji:😂")
        assertThat(docIds).contains("emoji:🔥")
    }

    @Test
    fun queryChildDocuments_inside_emoji_folder_returns_only_memes_with_that_emoji() {
        val joy1 = seedMeme(displayName = "joy_a.jpg")
        val joy2 = seedMeme(displayName = "joy_b.jpg")
        val fire = seedMeme(displayName = "fire.jpg")
        seedEmojiTags(joy1.id, "😂" to "joy")
        seedEmojiTags(joy2.id, "😂" to "joy")
        seedEmojiTags(fire.id, "🔥" to "fire")

        val joyFolderUri = DocumentsContract.buildChildDocumentsUri(authority, "emoji:😂")
        val docIds = mutableListOf<String>()
        targetContext.contentResolver.query(joyFolderUri, null, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            while (cursor.moveToNext()) {
                docIds.add(cursor.getString(idIdx))
            }
        }
        assertThat(docIds).containsExactly("meme:${joy1.id}", "meme:${joy2.id}")
        assertThat(docIds).doesNotContain("meme:${fire.id}")
    }

    // endregion

    // region Thumbnails

    @Test
    fun openDocumentThumbnail_returns_downsized_image_smaller_than_source() {
        // Use a much larger image than the picker's typical thumbnail hint so
        // downsizing has a chance to demonstrate itself. If thumbnailing were
        // a no-op (returning the full image), the bitmap would come back at
        // source dimensions — instead we expect it close to the hint.
        val seeded = seedMeme(displayName = "big.jpg", widthPx = 1024, heightPx = 1024)

        val docUri = DocumentsContract.buildDocumentUri(authority, "meme:${seeded.id}")
        // ContentResolver.loadThumbnail is the system API the file picker uses;
        // it packages the Size into ContentResolver.EXTRA_SIZE and routes to our
        // openDocumentThumbnail override under the hood.
        val thumbBitmap =
            targetContext.contentResolver.loadThumbnail(
                docUri,
                android.util.Size(128, 128),
                null,
            )
        try {
            // Both dimensions must be <= the source — proving downsizing actually
            // happened rather than returning the full image.
            assertThat(thumbBitmap.width).isLessThan(seeded.width)
            assertThat(thumbBitmap.height).isLessThan(seeded.height)
            // And at least the minimum we clamp to, so picker icons aren't useless
            // tiny smudges when a caller passes a very small hint.
            assertThat(thumbBitmap.width).isAtLeast(64)
        } finally {
            thumbBitmap.recycle()
        }
    }

    // endregion

    // region Helpers

    private fun seedMeme(
        displayName: String,
        isFavorite: Boolean = false,
        widthPx: Int = 1,
        heightPx: Int = 1,
    ): MemeEntity = runBlocking {
        val file =
            File(targetContext.cacheDir, "doc_test_${System.nanoTime()}_$displayName").apply {
                parentFile?.mkdirs()
                writeBytes(syntheticJpegBytes(widthPx, heightPx))
            }
        seededFiles.add(file)

        val now = System.currentTimeMillis()
        val entity =
            MemeEntity(
                filePath = file.absolutePath,
                fileName = displayName,
                mimeType = "image/jpeg",
                width = widthPx,
                height = heightPx,
                fileSizeBytes = file.length(),
                importedAt = now,
                emojiTagsJson = "[]",
                isFavorite = isFavorite,
                createdAt = now,
            )
        val id = memeDao.insertMeme(entity)
        seededIds.add(id)
        entity.copy(id = id)
    }

    private fun seedEmojiTags(
        memeId: Long,
        vararg emojis: Pair<String, String>,
    ) = runBlocking {
        emojiTagDao.insertEmojiTags(
            emojis.map { (emoji, name) ->
                EmojiTagEntity(memeId = memeId, emoji = emoji, emojiName = name)
            },
        )
    }

    /**
     * Build a JPEG of the requested dimensions. For 1x1 we return a known-good
     * micro-JPEG (smallest possible). For larger sizes we synthesise a real
     * bitmap so the thumbnail decoder has actual pixels to downsize.
     */
    private fun syntheticJpegBytes(
        width: Int = 1,
        height: Int = 1,
    ): ByteArray {
        if (width <= 1 && height <= 1) return TEST_IMAGE_BYTES
        val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            bmp.eraseColor(android.graphics.Color.MAGENTA)
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            return out.toByteArray()
        } finally {
            bmp.recycle()
        }
    }

    // endregion

    private companion object {
        // Smallest valid JPEG header — sufficient for round-trip byte equality.
        val TEST_IMAGE_BYTES: ByteArray =
            byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
                0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                0xFF.toByte(), 0xD9.toByte(),
            )
    }
}
