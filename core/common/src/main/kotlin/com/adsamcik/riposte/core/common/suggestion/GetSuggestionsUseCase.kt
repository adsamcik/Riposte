package com.adsamcik.riposte.core.common.suggestion

import com.adsamcik.riposte.core.model.Meme
import javax.inject.Inject

/**
 * Use case for computing sticker suggestions.
 *
 * Pure computation — takes a list of memes and a context, returns ordered suggestions.
 * ViewModels provide memes from their existing repositories; this use case applies
 * the StickerHand algorithm.
 *
 * Includes an in-memory cache with a 5-minute TTL. The cache is keyed by surface
 * and input list size; it's invalidated when the meme list changes or the TTL expires.
 */
class GetSuggestionsUseCase @Inject constructor() {

    private val engine = SuggestionEngine()

    private var cachedResult: List<Meme> = emptyList()
    private var cacheKey: CacheKey? = null
    private var cacheTimestamp: Long = 0L

    operator fun invoke(
        allMemes: List<Meme>,
        context: SuggestionContext,
        now: Long = System.currentTimeMillis(),
    ): List<Meme> {
        val key = CacheKey(
            surface = context.surface,
            memeCount = allMemes.size,
            memeHash = allMemes.hashCode(),
            contextHash = context.hashCode(),
        )

        if (key == cacheKey && (now - cacheTimestamp) < CACHE_TTL_MS) {
            return cachedResult
        }

        val result = engine.suggest(allMemes, context, now)
        cachedResult = result
        cacheKey = key
        cacheTimestamp = now
        return result
    }

    private data class CacheKey(
        val surface: Surface,
        val memeCount: Int,
        val memeHash: Int,
        val contextHash: Int,
    )

    companion object {
        /** Cache time-to-live: 5 minutes. */
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
