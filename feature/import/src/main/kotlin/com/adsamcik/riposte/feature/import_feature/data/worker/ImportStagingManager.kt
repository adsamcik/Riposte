package com.adsamcik.riposte.feature.import_feature.data.worker

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Copies selected images from content URIs to an internal staging directory.
 *
 * Content URIs from SAF are not guaranteed to persist after process death,
 * so images must be staged before enqueueing the [ImportWorker].
 *
 * For cloud-backed content URIs (e.g. Google Drive), the stream may be slow
 * or interrupted. This class cleans up partial files on failure to avoid
 * leaving corrupted data in the staging directory.
 */
class ImportStagingManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val stagingRoot: File
            get() = File(context.cacheDir, STAGING_DIR_NAME)

        /**
         * Copies the content at each [StagingInput.uri] to a uniquely named file inside a new
         * staging subdirectory. Returns the staging directory [File].
         *
         * @param images list of images to stage.
         * @param onProgress optional callback invoked after each file is staged, with
         *   (completedCount, totalCount) parameters. Useful for updating UI progress.
         */
        suspend fun stageImages(
            images: List<StagingInput>,
            onProgress: (suspend (completed: Int, total: Int) -> Unit)? = null,
        ): File =
            withContext(Dispatchers.IO) {
                val dir = File(stagingRoot, System.currentTimeMillis().toString())
                if (!dir.mkdirs()) {
                    throw IOException("Failed to create staging directory: ${dir.absolutePath}")
                }

                images.forEachIndexed { index, input ->
                    val destFile = File(dir, input.id)
                    try {
                        context.contentResolver.openInputStream(input.uri)?.use { inputStream ->
                            destFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        } ?: throw IOException("Could not open input stream for ${input.uri}")

                        // Validate the staged file is non-empty
                        if (destFile.length() == 0L) {
                            destFile.delete()
                            throw IOException(
                                "Staged file is empty (possible interrupted cloud download) for ${input.uri}",
                            )
                        }
                    } catch (e: IOException) {
                        // Clean up partial file on failure
                        if (destFile.exists()) {
                            destFile.delete()
                            Timber.w("Deleted partial staged file: %s", destFile.name)
                        }
                        throw e
                    }

                    onProgress?.invoke(index + 1, images.size)
                }

                dir
            }

        /** Deletes the staging directory and all files inside it. */
        fun cleanupStagingDir(dir: File) {
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }

        /** Deletes all staging directories (e.g., on app start for leftover data). */
        fun cleanupAll() {
            stagingRoot.deleteRecursively()
        }

        /** Input for a single image to stage. */
        data class StagingInput(
            val id: String,
            val uri: android.net.Uri,
        )

        companion object {
            private const val STAGING_DIR_NAME = "import_staging"
        }
    }
