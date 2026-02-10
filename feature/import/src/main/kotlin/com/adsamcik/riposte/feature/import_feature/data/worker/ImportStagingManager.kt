package com.adsamcik.riposte.feature.import_feature.data.worker

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Copies selected images from content URIs to an internal staging directory.
 *
 * Content URIs from SAF are not guaranteed to persist after process death,
 * so images must be staged before enqueueing the [ImportWorker].
 */
class ImportStagingManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val stagingRoot: File
        get() = File(context.cacheDir, STAGING_DIR_NAME)

    /**
     * Copies the content at [uri] to a uniquely named file inside a new staging subdirectory.
     * Returns the staging directory [File].
     */
    suspend fun stageImages(
        images: List<StagingInput>,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(stagingRoot, System.currentTimeMillis().toString())
        if (!dir.mkdirs()) {
            throw IOException("Failed to create staging directory: ${dir.absolutePath}")
        }

        images.forEach { input ->
            val destFile = File(dir, input.id)
            context.contentResolver.openInputStream(input.uri)?.use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IOException("Could not open input stream for ${input.uri}")
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
