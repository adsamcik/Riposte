package com.adsamcik.riposte.documents

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import com.adsamcik.riposte.R
import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.entity.MemeEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File

/**
 * Exposes Riposte's meme library to the Android Storage Access Framework.
 *
 * Result: when a user is in any app (Discord, Gmail, WhatsApp, etc.) and taps
 * "+" → "Files" (or any picker that uses `ACTION_OPEN_DOCUMENT` /
 * `ACTION_GET_CONTENT`), they see "Riposte" as a source and can browse memes
 * by category and pick one directly. The picking app receives a
 * `content://com.adsamcik.riposte(.debug).documents/...` URI it can read
 * via its own `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` permission —
 * same robustness guarantees as MediaStore-backed sharing on the push side.
 *
 * Structure exposed:
 *   - Root: "Riposte" with a meme-count summary
 *     - All Memes      (every meme, newest first)
 *     - Favorites      (only `isFavorite = 1`)
 *     - Recently Used  (top 20 by viewCount/useCount)
 *     - Emojis         (one subfolder per emoji that tags any meme)
 *       - 😂 (n memes)
 *       - 🔥 (n memes)
 *       - ...
 *
 * Each meme file ID encodes the entity row id: `meme:123`. Folder IDs are
 * fixed symbolic strings (or `emoji:<char>` for emoji folders). This avoids
 * any need for a separate document index.
 *
 * Hilt note: ContentProviders are created before `Application.onCreate`, so
 * field-injection isn't available. We pull the DAOs lazily on first use
 * via an [EntryPoint], which is the documented Hilt pattern for providers.
 *
 * Threading: every method runs on a binder thread that's expected to block,
 * so `runBlocking` to bridge our suspend DAO calls is appropriate here. We do
 * NOT touch the main thread.
 *
 * Change notifications: a long-lived coroutine scope observes meme/favorite
 * counts via Room Flows and calls [notifyChange] on the relevant document
 * URIs whenever the underlying data shifts. Pickers that have queried us
 * register listeners via [Cursor.setNotificationUri] in our cursor returns,
 * so the system file picker stays in sync with imports / deletions /
 * favorite toggles without the user needing to back out and re-open.
 */
class RiposteDocumentsProvider : NullSafeSearchDocumentsProvider() {

    private val daoLazy: MemeDao by lazy {
        EntryPointAccessors
            .fromApplication(requireProviderContext(), Entry::class.java)
            .memeDao()
    }

    private val emojiDaoLazy: EmojiTagDao by lazy {
        EntryPointAccessors
            .fromApplication(requireProviderContext(), Entry::class.java)
            .emojiTagDao()
    }

    private val observerJob: Job = SupervisorJob()
    private val observerScope: CoroutineScope by lazy {
        CoroutineScope(Dispatchers.IO + observerJob)
    }

    /**
     * One-shot observer startup. Read from each cursor entry point so it
     * initializes lazily AFTER Hilt is ready (ContentProvider.onCreate runs
     * BEFORE Application.onCreate, so we can't start observers from
     * [onCreate] directly). The `by lazy` ensures we start exactly once
     * even with concurrent queries from the picker.
     */
    @Suppress("unused") // Reading it for side-effects
    private val observersStarter: Unit by lazy { startChangeObservers() }

    private fun requireProviderContext(): Context =
        context ?: error("RiposteDocumentsProvider has no context yet")

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun memeDao(): MemeDao

        fun emojiTagDao(): EmojiTagDao
    }

    override fun onCreate(): Boolean = true

    override fun shutdown() {
        observerJob.cancel()
        super.shutdown()
    }

    /**
     * Observe Room Flows for the data backing our folders. When something
     * changes, notify the picker process on the corresponding child-documents
     * URI so any cached cursors get invalidated and re-fetched.
     */
    private fun startChangeObservers() {
        val resolver = requireProviderContext().contentResolver

        daoLazy.observeMemeCount()
            .distinctUntilChanged()
            .drop(1)  // skip the initial value emitted on subscribe
            .onEach {
                resolver.notifyChange(childDocumentsUri(DOC_ALL), null)
                resolver.notifyChange(childDocumentsUri(DOC_RECENT), null)
                resolver.notifyChange(childDocumentsUri(DOC_EMOJIS), null)
                resolver.notifyChange(childDocumentsUri(DOC_ROOT), null)
                // Roots URI carries the meme-count summary, so refresh it too.
                resolver.notifyChange(DocumentsContract.buildRootsUri(authority()), null)
            }
            .launchIn(observerScope)

        daoLazy.observeFavoriteCount()
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                resolver.notifyChange(childDocumentsUri(DOC_FAVORITES), null)
            }
            .launchIn(observerScope)
    }

    // region Roots

    override fun queryRoots(projection: Array<out String>?): Cursor {
        observersStarter
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val memeCount =
            @Suppress("TooGenericExceptionCaught")
            try {
                runBlocking { daoLazy.getMemeCount() }
            } catch (e: Throwable) {
                Timber.w(e, "Failed to count memes for picker root")
                0
            }

        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, ROOT_ID)
            add(Root.COLUMN_SUMMARY, requireProviderContext().getString(R.string.app_name))
            add(
                Root.COLUMN_FLAGS,
                Root.FLAG_SUPPORTS_RECENTS or
                    Root.FLAG_SUPPORTS_SEARCH or
                    Root.FLAG_LOCAL_ONLY,
            )
            add(Root.COLUMN_TITLE, requireProviderContext().getString(R.string.app_name))
            add(Root.COLUMN_DOCUMENT_ID, DOC_ROOT)
            add(Root.COLUMN_MIME_TYPES, "image/*")
            add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
            add(Root.COLUMN_AVAILABLE_BYTES, memeCount.toLong())
        }
        cursor.setNotificationUri(
            requireProviderContext().contentResolver,
            DocumentsContract.buildRootsUri(authority()),
        )
        return cursor
    }

    // endregion

    // region Documents

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): Cursor {
        observersStarter
        val cursor = MatrixCursor(projection ?: DEFAULT_DOC_PROJECTION)
        addDocumentRow(cursor, documentId)
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        observersStarter
        val cursor = MatrixCursor(projection ?: DEFAULT_DOC_PROJECTION)
        when {
            parentDocumentId == DOC_ROOT -> {
                addFolderRow(cursor, DOC_ALL, FOLDER_NAME_ALL)
                addFolderRow(cursor, DOC_FAVORITES, FOLDER_NAME_FAVORITES)
                addFolderRow(cursor, DOC_RECENT, FOLDER_NAME_RECENT)
                addFolderRow(cursor, DOC_EMOJIS, FOLDER_NAME_EMOJIS)
            }
            parentDocumentId == DOC_ALL ->
                populateMemes(cursor) { runBlocking { daoLazy.getAllMemes().first() } }
            parentDocumentId == DOC_FAVORITES ->
                populateMemes(cursor) { runBlocking { daoLazy.getFavoriteMemes().first() } }
            parentDocumentId == DOC_RECENT ->
                populateMemes(cursor) {
                    runBlocking { daoLazy.getRecentlyViewedMemes(RECENT_LIMIT).first() }
                }
            parentDocumentId == DOC_EMOJIS ->
                populateEmojiFolders(cursor)
            parentDocumentId.startsWith(EMOJI_PREFIX) -> {
                val emoji = parentDocumentId.removePrefix(EMOJI_PREFIX)
                populateMemes(cursor) {
                    runBlocking { daoLazy.getMemesByEmoji(emoji).first() }
                }
            }
            // Meme leaves have no children; return empty cursor.
        }
        cursor.setNotificationUri(
            requireProviderContext().contentResolver,
            childDocumentsUri(parentDocumentId),
        )
        return cursor
    }

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<out String>?,
    ): Cursor = runSearch(rootId, query, projection)

    /**
     * Receives a guaranteed non-null Bundle (defaulted via
     * [NullSafeSearchDocumentsProvider] when the upstream caller passes null).
     * Falls back to extracting the query string from the URI when the Bundle
     * doesn't carry [DocumentsContract.QUERY_ARG_DISPLAY_NAME] — that's how
     * legacy String-based ContentResolver.query callers reach search.
     */
    override fun doQuerySearchDocuments(
        rootId: String,
        projection: Array<out String>?,
        queryArgs: android.os.Bundle,
    ): Cursor {
        val term = queryArgs.getString(DocumentsContract.QUERY_ARG_DISPLAY_NAME).orEmpty()
        return runSearch(rootId, term, projection)
    }

    private fun runSearch(
        rootId: String,
        query: String,
        projection: Array<out String>?,
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOC_PROJECTION)
        if (rootId != ROOT_ID || query.isBlank()) return cursor

        // Lightweight client-side filter on file name + emoji tags. Avoids
        // pulling in the FTS infrastructure for what's a low-frequency UX —
        // most users will browse folders, not type into the file picker.
        val needle = query.trim().lowercase()
        val matches =
            @Suppress("TooGenericExceptionCaught")
            try {
                runBlocking { daoLazy.getAllMemes().first() }
                    .asSequence()
                    .filter { it.matchesQuery(needle) }
                    .take(SEARCH_LIMIT)
                    .toList()
            } catch (e: Throwable) {
                Timber.w(e, "Picker search failed for query '%s'", query)
                emptyList()
            }
        matches.forEach { meme -> addMemeRow(cursor, meme) }
        return cursor
    }

    override fun queryRecentDocuments(
        rootId: String,
        projection: Array<out String>?,
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOC_PROJECTION)
        if (rootId != ROOT_ID) return cursor
        populateMemes(cursor) {
            runBlocking { daoLazy.getRecentlyViewedMemes(RECENT_LIMIT).first() }
        }
        return cursor
    }

    private fun addDocumentRow(
        cursor: MatrixCursor,
        documentId: String,
    ) {
        when {
            documentId == DOC_ROOT -> addFolderRow(cursor, DOC_ROOT, FOLDER_NAME_ROOT)
            documentId == DOC_ALL -> addFolderRow(cursor, DOC_ALL, FOLDER_NAME_ALL)
            documentId == DOC_FAVORITES -> addFolderRow(cursor, DOC_FAVORITES, FOLDER_NAME_FAVORITES)
            documentId == DOC_RECENT -> addFolderRow(cursor, DOC_RECENT, FOLDER_NAME_RECENT)
            documentId == DOC_EMOJIS -> addFolderRow(cursor, DOC_EMOJIS, FOLDER_NAME_EMOJIS)
            documentId.startsWith(EMOJI_PREFIX) -> {
                val emoji = documentId.removePrefix(EMOJI_PREFIX)
                addFolderRow(cursor, documentId, emoji)
            }
            documentId.startsWith(MEME_PREFIX) -> {
                val id = documentId.removePrefix(MEME_PREFIX).toLongOrNull() ?: return
                val meme =
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        runBlocking { daoLazy.getMemeById(id) }
                    } catch (e: Throwable) {
                        Timber.w(e, "Failed to load meme %d for picker", id)
                        null
                    }
                meme?.let { addMemeRow(cursor, it) }
            }
        }
    }

    private fun addFolderRow(
        cursor: MatrixCursor,
        documentId: String,
        displayName: String,
    ) {
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, documentId)
            add(Document.COLUMN_DISPLAY_NAME, displayName)
            add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
            add(Document.COLUMN_FLAGS, 0)
            add(Document.COLUMN_SIZE, null)
        }
    }

    private fun addMemeRow(
        cursor: MatrixCursor,
        meme: MemeEntity,
    ) {
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, MEME_PREFIX + meme.id)
            add(Document.COLUMN_DISPLAY_NAME, meme.fileName)
            add(Document.COLUMN_MIME_TYPE, meme.mimeType)
            add(Document.COLUMN_SIZE, meme.fileSizeBytes)
            add(Document.COLUMN_LAST_MODIFIED, meme.importedAt)
            add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_THUMBNAIL)
        }
    }

    private inline fun populateMemes(
        cursor: MatrixCursor,
        fetch: () -> List<MemeEntity>,
    ) {
        val memes =
            @Suppress("TooGenericExceptionCaught")
            try {
                fetch()
            } catch (e: Throwable) {
                Timber.w(e, "Failed to load memes for picker")
                emptyList()
            }
        memes.forEach { meme -> addMemeRow(cursor, meme) }
    }

    /**
     * One folder per distinct emoji used in the library, ordered by tag
     * count (most-used emoji first) — matches Riposte's in-app gallery
     * ordering so the picker feels familiar.
     */
    private fun populateEmojiFolders(cursor: MatrixCursor) {
        val emojis =
            @Suppress("TooGenericExceptionCaught")
            try {
                runBlocking { emojiDaoLazy.getAllEmojisWithCounts().first() }
            } catch (e: Throwable) {
                Timber.w(e, "Failed to load emoji folders for picker")
                emptyList()
            }
        emojis.forEach { stats ->
            addFolderRow(cursor, EMOJI_PREFIX + stats.emoji, stats.emoji)
        }
    }

    // endregion

    // region File access

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        require(documentId.startsWith(MEME_PREFIX)) {
            "Cannot open non-meme document id $documentId"
        }
        val id = documentId.removePrefix(MEME_PREFIX).toLongOrNull()
            ?: error("Malformed meme document id $documentId")
        val meme = runBlocking { daoLazy.getMemeById(id) }
            ?: error("Meme $id not found")
        val file = File(meme.filePath)
        require(file.exists()) { "Meme file missing on disk: ${meme.filePath}" }
        // Pickers only need read access. Even if the caller requests "w" or
        // "rw" we coerce to "r" — the picker UX is "pick an existing file."
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?,
    ): AssetFileDescriptor? {
        if (!documentId.startsWith(MEME_PREFIX)) return null
        val source =
            documentId.removePrefix(MEME_PREFIX).toLongOrNull()
                ?.let { id -> runBlocking { daoLazy.getMemeById(id) } }
                ?.let { meme -> File(meme.filePath).takeIf { it.exists() } }
                ?: return null
        val thumbFile = generateThumbnail(source, sizeHint) ?: return null
        val pfd = ParcelFileDescriptor.open(thumbFile, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    /**
     * Decode the source image at the smallest [BitmapFactory.Options.inSampleSize]
     * that still satisfies the picker's size hint, re-encode as JPEG, write
     * to `cache/doc_thumbnails/`, return the file. Cached files are keyed by
     * source name + requested dimensions so subsequent thumbnail requests at
     * the same hint return the existing file without re-decoding.
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private fun generateThumbnail(
        source: File,
        sizeHint: Point,
    ): File? {
        val reqWidth = sizeHint.x.coerceAtLeast(THUMBNAIL_MIN_PX)
        val reqHeight = sizeHint.y.coerceAtLeast(THUMBNAIL_MIN_PX)

        val thumbDir =
            File(requireProviderContext().cacheDir, THUMBNAIL_CACHE_DIR).apply { mkdirs() }
        val thumbFile = File(thumbDir, "${source.nameWithoutExtension}_${reqWidth}x$reqHeight.jpg")
        // Reuse cached thumbnail when source hasn't changed since we wrote it.
        if (thumbFile.exists() && thumbFile.lastModified() >= source.lastModified()) {
            return thumbFile
        }

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val decodeOpts =
                BitmapFactory.Options().apply {
                    inSampleSize = calculateSampleSize(
                        bounds.outWidth, bounds.outHeight, reqWidth, reqHeight,
                    )
                    inPreferredConfig = Bitmap.Config.RGB_565  // thumbnails don't need alpha
                }
            val bitmap = BitmapFactory.decodeFile(source.absolutePath, decodeOpts) ?: return null
            try {
                thumbFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
                }
                thumbFile
            } finally {
                bitmap.recycle()
            }
        } catch (e: Throwable) {
            Timber.w(e, "Thumbnail generation failed for %s", source.absolutePath)
            null
        }
    }

    private fun calculateSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        // Pick the largest power-of-two factor that keeps both dimensions
        // above 2× the requested size — picker upscaling on a slightly larger
        // bitmap looks better than rendering an aliased downscale.
        var sample = 1
        while ((width / (sample * 2)) >= reqWidth && (height / (sample * 2)) >= reqHeight) {
            sample *= 2
        }
        return sample
    }

    // endregion

    // region Helpers

    private fun authority(): String = "${requireProviderContext().packageName}.documents"

    private fun childDocumentsUri(parentDocumentId: String): Uri =
        DocumentsContract.buildChildDocumentsUri(authority(), parentDocumentId)

    private fun MemeEntity.matchesQuery(needle: String): Boolean {
        if (fileName.lowercase().contains(needle)) return true
        if (title?.lowercase()?.contains(needle) == true) return true
        if (description?.lowercase()?.contains(needle) == true) return true
        if (textContent?.lowercase()?.contains(needle) == true) return true
        // Emoji match — needle might be an emoji itself
        if (emojiTagsJson.contains(needle)) return true
        return false
    }

    // endregion

    private companion object {
        const val ROOT_ID = "riposte_root"

        const val DOC_ROOT = "root"
        const val DOC_ALL = "all"
        const val DOC_FAVORITES = "favorites"
        const val DOC_RECENT = "recent"
        const val DOC_EMOJIS = "emojis"
        const val MEME_PREFIX = "meme:"
        const val EMOJI_PREFIX = "emoji:"

        const val FOLDER_NAME_ROOT = "Riposte"
        const val FOLDER_NAME_ALL = "All Memes"
        const val FOLDER_NAME_FAVORITES = "Favorites"
        const val FOLDER_NAME_RECENT = "Recently Used"
        const val FOLDER_NAME_EMOJIS = "Emojis"

        const val RECENT_LIMIT = 20
        const val SEARCH_LIMIT = 100

        const val THUMBNAIL_CACHE_DIR = "doc_thumbnails"
        const val THUMBNAIL_QUALITY = 80
        // Some pickers pass tiny hints (32px) which produce uselessly small
        // bitmaps — clamp to a reasonable minimum.
        const val THUMBNAIL_MIN_PX = 128

        val DEFAULT_ROOT_PROJECTION =
            arrayOf(
                Root.COLUMN_ROOT_ID,
                Root.COLUMN_MIME_TYPES,
                Root.COLUMN_FLAGS,
                Root.COLUMN_ICON,
                Root.COLUMN_TITLE,
                Root.COLUMN_SUMMARY,
                Root.COLUMN_DOCUMENT_ID,
                Root.COLUMN_AVAILABLE_BYTES,
            )

        val DEFAULT_DOC_PROJECTION =
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_FLAGS,
                Document.COLUMN_SIZE,
            )
    }
}
