package com.adsamcik.riposte.documents

import android.content.Context
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.riposte.core.database.dao.MemeDao
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
    fun queryChildDocuments_at_root_returns_three_top_level_folders() {
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
        assertThat(seenIds).containsExactly("all", "favorites", "recent")
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

    private fun seedMeme(
        displayName: String,
        isFavorite: Boolean = false,
    ): MemeEntity = runBlocking {
        val file =
            File(targetContext.cacheDir, "doc_test_${System.nanoTime()}_$displayName").apply {
                parentFile?.mkdirs()
                writeBytes(TEST_IMAGE_BYTES)
            }
        seededFiles.add(file)

        val now = System.currentTimeMillis()
        val entity =
            MemeEntity(
                filePath = file.absolutePath,
                fileName = displayName,
                mimeType = "image/jpeg",
                width = 1,
                height = 1,
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
