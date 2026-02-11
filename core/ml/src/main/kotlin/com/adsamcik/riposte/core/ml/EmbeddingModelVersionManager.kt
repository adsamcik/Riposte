package com.adsamcik.riposte.core.ml

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
 * - "simple_hash:1.0.0" - Hash-based fallback
 * - "litert_use:1.0.0" - Universal Sentence Encoder via LiteRT
 * - "litert_use:2.0.0" - Updated USE model
 */
@Singleton
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
                currentModelVersion.startsWith("embeddinggemma") -> 768
                currentModelVersion.startsWith("mediapipe_use") -> 512
                currentModelVersion.startsWith("litert_use") -> 512
                currentModelVersion.startsWith("simple_hash") -> 128
                else -> 768
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
            } catch (e: Exception) {
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
                version.startsWith("simple_hash") ->
                    "Hash-based embedding fallback (reduced accuracy)"
                else -> "Unknown embedding model"
            }
        }

        companion object {
            /**
             * Current model version.
             * UPDATE THIS when changing the embedding model.
             */
            const val CURRENT_VERSION = "embeddinggemma:1.0.0"

            /**
             * Fallback model version for when EmbeddingGemma is unavailable.
             */
            const val FALLBACK_VERSION = "simple_hash:1.0.0"

            private val KEY_LAST_MODEL_VERSION = stringPreferencesKey("last_model_version")
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
