package com.mememymood.core.common.util

import android.util.Log
import kotlin.time.measureTimedValue

/**
 * Comprehensive logging utility for debugging search functionality.
 * 
 * Enable detailed logging by setting [isEnabled] = true.
 * Filter logs with: `adb logcat -s "SearchDebug"`
 */
object SearchDebugLogger {
    
    private const val TAG = "SearchDebug"
    
    /**
     * Enable/disable search debug logging.
     * Set to true during development to trace search issues.
     */
    var isEnabled: Boolean = true
    
    // ============ Query & Input Logging ============
    
    fun logSearchStart(
        searchMode: String,
        query: String,
        emojiFilters: List<String>,
        quickFilter: String? = null,
    ) {
        if (!isEnabled) return
        Log.d(TAG, """
            |╔══════════════════════════════════════════════════════════════
            |║ SEARCH START
            |╠══════════════════════════════════════════════════════════════
            |║ Mode: $searchMode
            |║ Query: "$query"
            |║ Emoji Filters: ${emojiFilters.ifEmpty { "(none)" }}
            |║ Quick Filter: ${quickFilter ?: "(none)"}
            |╚══════════════════════════════════════════════════════════════
        """.trimMargin())
    }
    
    // ============ FTS/Query Sanitization Logging ============
    
    fun logQuerySanitization(
        originalQuery: String,
        sanitizedQuery: String,
        removedTerms: List<String> = emptyList(),
        finalMatchQuery: String? = null,
    ) {
        if (!isEnabled) return
        Log.d(TAG, """
            |┌─ FTS QUERY SANITIZATION ─────────────────────────────────────
            |│ Original: "$originalQuery"
            |│ Sanitized: "$sanitizedQuery"
            |│ Removed terms: ${removedTerms.ifEmpty { "(none)" }}
            |│ Final MATCH query: ${finalMatchQuery ?: "(same as sanitized)"}
            |└───────────────────────────────────────────────────────────────
        """.trimMargin())
    }
    
    fun logEmojiQueryConstruction(
        emoji: String,
        finalQuery: String,
    ) {
        if (!isEnabled) return
        Log.d(TAG, "┌─ EMOJI QUERY: emoji='$emoji' → MATCH='$finalQuery'")
    }
    
    // ============ FTS Search Logging ============
    
    fun logFtsSearchStart(matchQuery: String) {
        if (!isEnabled) return
        Log.d(TAG, "├─ FTS SEARCH: Executing MATCH '$matchQuery'")
    }
    
    fun logFtsSearchResult(resultCount: Int, timeMs: Long) {
        if (!isEnabled) return
        Log.d(TAG, "├─ FTS RESULT: $resultCount memes found in ${timeMs}ms")
    }
    
    // ============ Semantic Search Logging ============
    
    fun logSemanticSearchStart(
        query: String,
        candidateCount: Int,
        embeddingDimension: Int,
        threshold: Float,
    ) {
        if (!isEnabled) return
        Log.d(TAG, """
            |├─ SEMANTIC SEARCH START ──────────────────────────────────────
            |│ Query: "$query"
            |│ Candidates: $candidateCount memes with embeddings
            |│ Embedding dimension: $embeddingDimension
            |│ Similarity threshold: $threshold
        """.trimMargin())
    }
    
    fun logQueryEmbeddingGenerated(cached: Boolean, dimension: Int, timeMs: Long) {
        if (!isEnabled) return
        val cacheStatus = if (cached) "CACHE HIT" else "GENERATED"
        Log.d(TAG, "│ Query embedding: $cacheStatus, dim=$dimension, time=${timeMs}ms")
    }
    
    fun logSemanticSearchResult(
        matchCount: Int,
        totalCandidates: Int,
        maxSimilarity: Float,
        minSimilarity: Float,
        timeMs: Long,
    ) {
        if (!isEnabled) return
        Log.d(TAG, """
            |│ Semantic matches: $matchCount / $totalCandidates candidates
            |│ Similarity range: [${"%.3f".format(minSimilarity)}, ${"%.3f".format(maxSimilarity)}]
            |├─ SEMANTIC SEARCH COMPLETE: ${timeMs}ms
        """.trimMargin())
    }
    
    // ============ Hybrid Search Logging ============
    
    fun logHybridMergeStart(ftsCount: Int, semanticCount: Int) {
        if (!isEnabled) return
        Log.d(TAG, """
            |├─ HYBRID MERGE START ─────────────────────────────────────────
            |│ FTS results: $ftsCount
            |│ Semantic results: $semanticCount
        """.trimMargin())
    }
    
    fun logHybridMergeResult(
        totalUnique: Int,
        hybridMatches: Int,
        ftsOnlyMatches: Int,
        semanticOnlyMatches: Int,
        timeMs: Long,
    ) {
        if (!isEnabled) return
        Log.d(TAG, """
            |│ Total unique: $totalUnique
            |│ Both sources (HYBRID): $hybridMatches
            |│ FTS only: $ftsOnlyMatches
            |│ Semantic only: $semanticOnlyMatches
            |├─ HYBRID MERGE COMPLETE: ${timeMs}ms
        """.trimMargin())
    }
    
    // ============ Filter Application Logging ============
    
    fun logFilterApplication(
        inputCount: Int,
        outputCount: Int,
        emojiFiltersApplied: List<String>,
        quickFilterEmoji: String? = null,
    ) {
        if (!isEnabled) return
        Log.d(TAG, """
            |├─ FILTER APPLICATION ─────────────────────────────────────────
            |│ Input: $inputCount results
            |│ Output: $outputCount results
            |│ Emoji filters: ${emojiFiltersApplied.ifEmpty { "(none)" }}
            |│ Quick filter emoji: ${quickFilterEmoji ?: "(none)"}
            |│ Removed by filters: ${inputCount - outputCount}
        """.trimMargin())
    }
    
    // ============ Final Result Logging ============
    
    fun logSearchComplete(
        totalResults: Int,
        searchMode: String,
        totalTimeMs: Long,
    ) {
        if (!isEnabled) return
        Log.d(TAG, """
            |╔══════════════════════════════════════════════════════════════
            |║ SEARCH COMPLETE
            |╠══════════════════════════════════════════════════════════════
            |║ Mode: $searchMode
            |║ Final results: $totalResults
            |║ Total time: ${totalTimeMs}ms
            |╚══════════════════════════════════════════════════════════════
        """.trimMargin())
    }
    
    // ============ Error Logging ============
    
    fun logError(phase: String, error: Throwable) {
        Log.e(TAG, """
            |╔══════════════════════════════════════════════════════════════
            |║ SEARCH ERROR
            |╠══════════════════════════════════════════════════════════════
            |║ Phase: $phase
            |║ Error: ${error.message}
            |║ Type: ${error::class.simpleName}
            |╚══════════════════════════════════════════════════════════════
        """.trimMargin(), error)
    }
    
    fun logWarning(message: String) {
        if (!isEnabled) return
        Log.w(TAG, "⚠️ WARNING: $message")
    }
    
    // ============ Timing Utility ============
    
    inline fun <T> timed(phase: String, block: () -> T): Pair<T, Long> {
        val start = System.currentTimeMillis()
        val result = block()
        val elapsed = System.currentTimeMillis() - start
        return result to elapsed
    }
}
