package com.adsamcik.riposte.feature.gallery.domain.model

/**
 * Domain representation of a meme's embedding data for similarity computation.
 */
data class MemeEmbeddingData(
    val memeId: Long,
    val embedding: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MemeEmbeddingData
        if (memeId != other.memeId) return false
        if (!embedding.contentEquals(other.embedding)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = memeId.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}
