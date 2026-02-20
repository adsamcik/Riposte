package com.adsamcik.riposte.core.ml

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages embedding model versions and handles model upgrades.
 *
 * This class tracks which embedding model version is currently in use and
 * provides utilities for detecting when embeddings need to be regenerated
 * due to model updates.
 *
 * Version format: "model_name:major.minor.patch"
 * Examples:
 * - "litert_use:2.0.0" - Updated USE model
 */
@Singleton
@Suppress("TooManyFunctions")
class EmbeddingModelVersionManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val Context.embeddingPrefs: DataStore<Preferences> by preferencesDataStore(
            name = "embedding_model_prefs",
        )

        /**
         * Current model version used by the app.
         * Update this when the embedding model changes.
         */
        val currentModelVersion: String = CURRENT_VERSION

        /**
         * Observe the last used model version.
         */
        fun observeLastUsedVersion(): Flow<String?> {
            return context.embeddingPrefs.data.map { prefs ->
                prefs[KEY_LAST_MODEL_VERSION]
            }
        }

        /**
         * Get the last used model version.
         */
        suspend fun getLastUsedVersion(): String? {
            return context.embeddingPrefs.data.first()[KEY_LAST_MODEL_VERSION]
        }

        /**
         * Update the stored model version to current.
         */
        suspend fun updateToCurrentVersion() {
            context.embeddingPrefs.edit { prefs ->
                prefs[KEY_LAST_MODEL_VERSION] = currentModelVersion
            }
        }

        /**
         * Check if model has been upgraded since last embedding generation.
         */
        suspend fun hasModelBeenUpgraded(): Boolean {
            val lastVersion = getLastUsedVersion()
            return lastVersion != null && lastVersion != currentModelVersion
        }

        /**
         * Check if a specific embedding version is compatible with current model.
         */
        fun isVersionCompatible(embeddingVersion: String): Boolean {
            // Extract major version for compatibility check
            val currentMajor = extractMajorVersion(currentModelVersion)
            val embeddingMajor = extractMajorVersion(embeddingVersion)

            // Same model and major version = compatible
            val currentModel = extractModelName(currentModelVersion)
            val embeddingModel = extractModelName(embeddingVersion)

            return currentModel == embeddingModel && currentMajor == embeddingMajor
        }

        /**
         * Get embedding dimension for the current model.
         */
        fun getCurrentEmbeddingDimension(): Int {
            return when {
                currentModelVersion.startsWith("embeddinggemma") -> EMBEDDING_GEMMA_DIMENSION
                currentModelVersion.startsWith("mediapipe_use") -> USE_MODEL_DIMENSION
                currentModelVersion.startsWith("litert_use") -> USE_MODEL_DIMENSION
                else -> EMBEDDING_GEMMA_DIMENSION
            }
        }

        /**
         * Get information about the current model.
         */
        fun getModelInfo(): EmbeddingModelInfo {
            return EmbeddingModelInfo(
                version = currentModelVersion,
                name = extractModelName(currentModelVersion),
                dimension = getCurrentEmbeddingDimension(),
                description = getModelDescription(currentModelVersion),
            )
        }

        private fun extractModelName(version: String): String {
            return version.substringBefore(":")
        }

        private fun extractMajorVersion(version: String): Int {
            return try {
                version.substringAfter(":").substringBefore(".").toInt()
            } catch (
                @Suppress("TooGenericExceptionCaught") // ML libraries throw unpredictable exceptions
                e: Exception,
            ) {
                Timber.e(e, "Failed to extract major version from: $version")
                0
            }
        }

        private fun getModelDescription(version: String): String {
            return when {
                version.startsWith("embeddinggemma") ->
                    "EmbeddingGemma 300M via AI Edge RAG SDK for high-quality semantic embeddings (768 dims)"
                version.startsWith("mediapipe_use") ->
                    "Universal Sentence Encoder via MediaPipe for semantic text embeddings"
                version.startsWith("litert_use") ->
                    "Universal Sentence Encoder via LiteRT for semantic text embeddings"
                else -> "Unknown embedding model"
            }
        }

        /**
         * Clear error tracking (e.g., when initialization succeeds).
         */
        suspend fun clearInitializationFailure() {
            context.embeddingPrefs.edit { prefs ->
                prefs.remove(KEY_ERROR_APP_VERSION_CODE)
                prefs.remove(KEY_ERROR_ATTEMPT_COUNT)
            }
        }

        companion object {
            /**
             * Current model version.
             * UPDATE THIS when changing the embedding model.
             */
            const val CURRENT_VERSION = "embeddinggemma:1.0.0"

            private const val EMBEDDING_GEMMA_DIMENSION = 768
            private const val USE_MODEL_DIMENSION = 512

            private val KEY_LAST_MODEL_VERSION = stringPreferencesKey("last_model_version")
            private val KEY_ERROR_APP_VERSION_CODE = longPreferencesKey("error_app_version_code")
            private val KEY_ERROR_ATTEMPT_COUNT = intPreferencesKey("error_attempt_count")
        }
    }

/**
 * Information about an embedding model.
 */
data class EmbeddingModelInfo(
    val version: String,
    val name: String,
    val dimension: Int,
    val description: String,
)
