package com.adsamcik.riposte.core.database.dao

/**
 * Data class for embedding count per model version.
 */
data class EmbeddingVersionCount(
    val modelVersion: String,
    val count: Int,
)
