package com.adsamcik.riposte.core.common

/**
 * App-wide constants that need to be shared across modules.
 *
 * Note: For the actual release version, consider using BuildConfig from
 * the app module and injecting it via Hilt. This constant serves as a
 * single source of truth for hardcoded version references.
 */
object AppConstants {
    /**
     * Current app version string.
     * Update this when releasing a new version.
     */
    const val APP_VERSION = "1.0.0"

    /**
     * Metadata schema version for XMP and sidecar files.
     */
    const val METADATA_SCHEMA_VERSION = "1.1"

    /**
     * Unique work name for the meme import WorkManager worker.
     * Shared across modules so Gallery can observe import status
     * without depending on the import feature.
     */
    const val IMPORT_WORK_NAME = "meme_import_work"

    /**
     * Unique work name for the embedding generation WorkManager worker.
     * Shared across modules so Gallery can observe indexing status
     * without importing the worker class directly.
     */
    const val EMBEDDING_WORK_NAME = "embedding_generation_work"
}
