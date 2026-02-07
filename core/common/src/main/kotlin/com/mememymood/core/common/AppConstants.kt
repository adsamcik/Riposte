package com.mememymood.core.common

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
}
