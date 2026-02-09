package com.adsamcik.riposte.feature.gallery.domain.usecase

import com.adsamcik.riposte.core.model.Meme

/**
 * Result of attempting to find similar memes, carrying the reason when none are found.
 */
sealed interface SimilarMemesStatus {
    /** Similar memes were found. */
    data class Found(val memes: List<Meme>) : SimilarMemesStatus

    /** The current meme has no embedding yet (still generating). */
    data object NoEmbeddingForMeme : SimilarMemesStatus

    /** No other memes have embeddings to compare against. */
    data object NoCandidates : SimilarMemesStatus

    /** Candidates exist but none scored above the similarity threshold. */
    data object NoSimilarFound : SimilarMemesStatus

    /** An error occurred during similarity computation. */
    data class Error(val message: String) : SimilarMemesStatus
}
