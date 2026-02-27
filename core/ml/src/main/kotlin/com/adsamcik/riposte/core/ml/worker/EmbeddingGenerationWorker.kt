package com.adsamcik.riposte.core.ml.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.adsamcik.riposte.core.common.lifecycle.AppLifecycleTracker
import com.adsamcik.riposte.core.events.EmbeddingsReady
import com.adsamcik.riposte.core.events.EventBus
import com.adsamcik.riposte.core.ml.EmbeddingGenerator
import com.adsamcik.riposte.core.model.EmbeddingType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for generating embeddings for memes in the background.
 *
 * This worker processes memes that don't have embeddings or need regeneration.
 * It runs with low priority to avoid impacting app performance.
 *
 * Features:
 * - Batch processing with configurable batch size
 * - Exponential backoff on failure
 * - Progress reporting
 * - Model version tracking
 */
@HiltWorker
class EmbeddingGenerationWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted params: WorkerParameters,
        private val embeddingGenerator: EmbeddingGenerator,
        private val embeddingRepository: EmbeddingWorkRepository,
        private val appLifecycleTracker: AppLifecycleTracker,
        private val notificationManager: EmbeddingNotificationManager,
        private val eventBus: EventBus,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            withContext(Dispatchers.Default) {
                val startTime = System.currentTimeMillis()
                Timber.i(
                    "Embedding generation starting (maxFetchSize=%d, attempt=%d)",
                    MAX_FETCH_SIZE, runAttemptCount + 1,
                )
                try {
                    notificationManager.createChannel()

                    // Get a large pool of pending memes — we'll process as many as time allows
                    val pendingMemes = embeddingRepository.getMemesNeedingEmbeddings(MAX_FETCH_SIZE)

                    if (pendingMemes.isEmpty()) {
                        Timber.d("No memes need embeddings, finishing early")
                        return@withContext Result.success(
                            workDataOf(
                                KEY_PROCESSED_COUNT to 0,
                                KEY_REMAINING_COUNT to 0,
                            ),
                        )
                    }

                    val (successCount, failureCount) = processAdaptiveBatch(pendingMemes)

                    // Check if there are more memes to process
                    val remainingCount = embeddingRepository.countMemesNeedingEmbeddings()

                    val outputData =
                        workDataOf(
                            KEY_PROCESSED_COUNT to successCount,
                            KEY_FAILED_COUNT to failureCount,
                            KEY_REMAINING_COUNT to remainingCount,
                        )

                    val elapsed = System.currentTimeMillis() - startTime

                    // Only schedule continuation if we made progress this batch.
                    // If no memes succeeded, the model is likely unavailable and
                    // re-scheduling immediately would create an infinite loop that
                    // floods the main thread with WorkManager overhead, causing ANR.
                    if (remainingCount > 0 && successCount > 0) {
                        Timber.i(
                            "Embedding batch done in %dms: %d ok, %d failed, %d remaining — scheduling next",
                            elapsed, successCount, failureCount, remainingCount,
                        )
                        enqueueContinuation(context)
                    } else if (remainingCount > 0) {
                        Timber.w(
                            "Embedding batch done in %dms: 0 ok, %d failed, %d remaining — " +
                                "not scheduling continuation to avoid busy loop",
                            elapsed, failureCount, remainingCount,
                        )
                    } else {
                        Timber.i(
                            "Embedding generation complete in %dms: %d ok, %d failed, 0 remaining",
                            elapsed, successCount, failureCount,
                        )
                    }

                    // Show completion notification if app is in background and this is the last batch
                    if (remainingCount == 0 && successCount > 0 && appLifecycleTracker.isInBackground.value) {
                        notificationManager.showCompleteNotification(successCount, failureCount)
                    }

                    eventBus.emit(
                        EmbeddingsReady(
                            processedCount = successCount,
                            failedCount = failureCount,
                            remainingCount = remainingCount,
                        ),
                    )
                    Result.success(outputData)
                } catch (
                    @Suppress("TooGenericExceptionCaught") // Worker must not crash - reports failure instead
                    e: Exception,
                ) {
                    val elapsed = System.currentTimeMillis() - startTime
                    Timber.e(
                        e,
                        "Embedding generation failed after %dms (attempt %d/%d)",
                        elapsed, runAttemptCount + 1, MAX_RETRY_COUNT,
                    )
                    if (runAttemptCount < MAX_RETRY_COUNT) {
                        Result.retry()
                    } else {
                        Result.failure(
                            workDataOf(KEY_ERROR_MESSAGE to e.message),
                        )
                    }
                }
            }

        /**
         * Adaptive batch: process the first item to measure device speed, then
         * fill the remaining time budget (7 min total, 3 min headroom = 4 min work).
         */
        private suspend fun processAdaptiveBatch(
            pendingMemes: List<MemeDataForEmbedding>,
        ): Pair<Int, Int> {
            var successCount = 0
            var failureCount = 0
            val batchStartTime = System.currentTimeMillis()

            // Process first item and measure duration
            val firstStart = System.currentTimeMillis()
            processOneEmbedding(pendingMemes.first()).let { ok -> if (ok) successCount++ else failureCount++ }
            val firstDuration = (System.currentTimeMillis() - firstStart).coerceAtLeast(MIN_ITEM_DURATION_MS)

            reportProgress(successCount, failureCount, pendingMemes.size)

            // Calculate how many more fit in budget
            val elapsedMs = System.currentTimeMillis() - batchStartTime
            val remainingBudgetMs = WORK_BUDGET_MS - elapsedMs
            val additionalItems = (remainingBudgetMs / firstDuration).toInt()
                .coerceIn(0, pendingMemes.size - 1)

            Timber.d(
                "Adaptive embedding batch: first item took %dms, budget allows %d more (of %d pending)",
                firstDuration,
                additionalItems,
                pendingMemes.size - 1,
            )

            for (i in 1..additionalItems) {
                processOneEmbedding(pendingMemes[i]).let { ok -> if (ok) successCount++ else failureCount++ }
                reportProgress(successCount, failureCount, pendingMemes.size)
            }

            return Pair(successCount, failureCount)
        }

        /** Generates embeddings for a single meme. Returns true on success. */
        private suspend fun processOneEmbedding(memeData: MemeDataForEmbedding): Boolean {
            return try {
                var generatedAny = false

                // Use two-arg generateFromText so EmbeddingGemma gets the structured "title: X | text: Y" prompt
                val (title, body) = buildContentParts(memeData)
                val contentText = if (title != null) "$title. $body" else body
                if (contentText.isNotBlank()) {
                    val embedding = embeddingGenerator.generateFromText(body, title)
                    val sourceHash = generateHash(contentText)
                    embeddingRepository.saveEmbedding(
                        memeId = memeData.id,
                        embedding = encodeEmbedding(embedding),
                        dimension = embedding.size,
                        modelVersion = CURRENT_MODEL_VERSION,
                        sourceTextHash = sourceHash,
                        embeddingType = EmbeddingType.CONTENT.key,
                    )
                    generatedAny = true
                }

                val intentText = buildIntentText(memeData)
                if (intentText.isNotBlank()) {
                    val embedding = embeddingGenerator.generateFromText(intentText)
                    val sourceHash = generateHash(intentText)
                    embeddingRepository.saveEmbedding(
                        memeId = memeData.id,
                        embedding = encodeEmbedding(embedding),
                        dimension = embedding.size,
                        modelVersion = CURRENT_MODEL_VERSION,
                        sourceTextHash = sourceHash,
                        embeddingType = EmbeddingType.INTENT.key,
                    )
                    generatedAny = true
                }

                val emojiText = buildEmojiText(memeData)
                if (emojiText.isNotBlank()) {
                    val embedding = embeddingGenerator.generateFromText(emojiText)
                    val sourceHash = generateHash(emojiText)
                    embeddingRepository.saveEmbedding(
                        memeId = memeData.id,
                        embedding = encodeEmbedding(embedding),
                        dimension = embedding.size,
                        modelVersion = CURRENT_MODEL_VERSION,
                        sourceTextHash = sourceHash,
                        embeddingType = EmbeddingType.EMOJI.key,
                    )
                    generatedAny = true
                }

                val differentiatorText = buildDifferentiatorText(memeData)
                if (differentiatorText.isNotBlank()) {
                    val embedding = embeddingGenerator.generateFromText(differentiatorText)
                    val sourceHash = generateHash(differentiatorText)
                    embeddingRepository.saveEmbedding(
                        memeId = memeData.id,
                        embedding = encodeEmbedding(embedding),
                        dimension = embedding.size,
                        modelVersion = CURRENT_MODEL_VERSION,
                        sourceTextHash = sourceHash,
                        embeddingType = EmbeddingType.DIFFERENTIATOR.key,
                    )
                    generatedAny = true
                }
                generatedAny
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Timber.w(e, "Failed to generate embedding for meme ${memeData.id}")
                false
            }
        }

        private fun reportProgress(success: Int, failed: Int, total: Int) {
            setProgressAsync(
                workDataOf(
                    KEY_PROGRESS to ((success + failed) * PERCENTAGE_MULTIPLIER / total),
                    KEY_PROCESSED_COUNT to (success + failed),
                    KEY_REMAINING_COUNT to (total - success - failed),
                ),
            )
        }

        /**
         * Build title and content body for the content embedding slot.
         * Returns a Pair of (title, body) where title may be null.
         */
        private fun buildContentText(memeData: MemeDataForEmbedding): String {
            val body = buildString {
                memeData.description?.let { append(it).append(". ") }
                memeData.textContent?.let { append(it).append(". ") }
            }.trim().trimEnd('.')
            return body.ifBlank { memeData.title ?: "" }
        }

        private fun buildContentParts(memeData: MemeDataForEmbedding): Pair<String?, String> {
            val body = buildString {
                memeData.description?.let { append(it).append(". ") }
                memeData.textContent?.let { append(it).append(". ") }
            }.trim().trimEnd('.')
            return Pair(memeData.title, body.ifBlank { memeData.title ?: "" })
        }

        /**
         * Build text for intent embedding slot: searchPhrases.
         */
        private fun buildIntentText(memeData: MemeDataForEmbedding): String {
            val jsonString = memeData.searchPhrases?.takeIf { it.isNotBlank() } ?: return ""
            val phrases =
                try {
                    kotlinx.serialization.json.Json.decodeFromString<List<String>>(jsonString)
                } catch (
                    @Suppress("TooGenericExceptionCaught") // Worker must not crash - reports failure instead
                    e: Exception,
                ) {
                    // Fallback: treat as comma-separated if not valid JSON
                    Timber.d(e, "Failed to parse search phrases as JSON, falling back to comma-separated format")
                    jsonString.split(",").map { it.trim() }
                }
            return phrases.joinToString(". ")
        }

        /**
         * Build text representation of emoji tags for embedding.
         * Converts raw emoji characters to their Unicode names for semantic meaning.
         * e.g. ["💪", "🏋"] → "flexed biceps, weight lifter"
         */
        private fun buildEmojiText(memeData: MemeDataForEmbedding): String {
            val jsonString = memeData.emojiTagsJson?.takeIf { it.isNotBlank() } ?: return ""
            val emojis =
                try {
                    kotlinx.serialization.json.Json.decodeFromString<List<String>>(jsonString)
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    Timber.d(e, "Failed to parse emoji tags JSON")
                    return ""
                }
            return emojis
                .map { resolveEmojiName(it) }
                .filter { it.isNotBlank() }
                .joinToString(", ")
        }

        /**
         * Convert an emoji string to human-readable Unicode names.
         * Iterates over codepoints, resolves each via [Character.getName],
         * and filters out non-semantic joiners and modifiers.
         *
         * E.g. "💪" → "flexed biceps", "👨‍💻" → "man personal computer"
         */
        private fun resolveEmojiName(emoji: String): String {
            val names = mutableListOf<String>()
            var i = 0
            while (i < emoji.length) {
                val codePoint = Character.codePointAt(emoji, i)
                i += Character.charCount(codePoint)

                if (isNonSemanticCodepoint(codePoint)) continue

                val name = Character.getName(codePoint)
                if (name != null) {
                    names.add(name.lowercase())
                }
            }
            return names.joinToString(" ")
        }

        /**
         * Returns true for codepoints that carry no semantic meaning
         * (joiners, variation selectors, skin tone modifiers).
         */
        private fun isNonSemanticCodepoint(codePoint: Int): Boolean =
            codePoint == ZWJ_CODEPOINT ||
                codePoint == VARIATION_SELECTOR_16 ||
                codePoint == VARIATION_SELECTOR_15 ||
                codePoint in SKIN_TONE_MODIFIER_RANGE

        /**
         * Build differentiator text from unique aspects of the meme:
         * OCR text, meme template source, and emoji combination.
         */
        private fun buildDifferentiatorText(memeData: MemeDataForEmbedding): String {
            val parts = mutableListOf<String>()
            memeData.basedOn?.takeIf { it.isNotBlank() }?.let {
                parts.add("template: ${it.replace("_", " ")}")
            }
            memeData.textContent?.takeIf { it.isNotBlank() }?.let {
                parts.add("text: $it")
            }
            val emojiText = buildEmojiText(memeData)
            if (emojiText.isNotBlank()) {
                parts.add("tags: $emojiText")
            }
            return parts.joinToString(" | ")
        }

        private fun encodeEmbedding(embedding: FloatArray): ByteArray {
            val buffer =
                ByteBuffer.allocate(embedding.size * BYTES_PER_FLOAT)
                    .order(ByteOrder.LITTLE_ENDIAN)
            embedding.forEach { buffer.putFloat(it) }
            return buffer.array()
        }

        private fun generateHash(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(text.toByteArray(Charsets.UTF_8))
            // Truncate to 32 chars (128 bits) to match EmbeddingManager.generateHash
            return hash.take(HASH_BYTE_LENGTH).joinToString("") { "%02x".format(it) }
        }

        companion object {
            const val WORK_NAME = "embedding_generation_work"
            const val MAX_RETRY_COUNT = 3
            const val CURRENT_MODEL_VERSION = "embeddinggemma:1.3.0"
            private const val CONTINUATION_DELAY_SECONDS = 5L
            private const val PERCENTAGE_MULTIPLIER = 100
            private const val BYTES_PER_FLOAT = 4
            private const val HASH_BYTE_LENGTH = 16
            private const val BACKOFF_SECONDS = 30L

            // Unicode codepoints filtered during emoji name resolution
            private const val ZWJ_CODEPOINT = 0x200D
            private const val VARIATION_SELECTOR_16 = 0xFE0F
            private const val VARIATION_SELECTOR_15 = 0xFE0E
            private val SKIN_TONE_MODIFIER_RANGE = 0x1F3FB..0x1F3FF

            /** Fetch up to this many pending memes; adaptive logic decides how many to process. */
            private const val MAX_FETCH_SIZE = 200

            /** 7 min total minus 3 min headroom = 4 min work budget per batch. */
            private const val WORK_BUDGET_MS = 4L * 60 * 1000

            /** Floor for per-item duration to avoid division issues on very fast inference. */
            private const val MIN_ITEM_DURATION_MS = 50L

            // Output data keys
            const val KEY_PROCESSED_COUNT = "processed_count"
            const val KEY_FAILED_COUNT = "failed_count"
            const val KEY_REMAINING_COUNT = "remaining_count"
            const val KEY_PROGRESS = "progress"
            const val KEY_ERROR_MESSAGE = "error_message"

            /**
             * Enqueues the embedding generation work.
             * Uses KEEP to prevent duplicate runs when triggered externally.
             */
            fun enqueue(context: Context) {
                val constraints =
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()

                val request =
                    OneTimeWorkRequestBuilder<EmbeddingGenerationWorker>()
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            BACKOFF_SECONDS,
                            TimeUnit.SECONDS,
                        )
                        .addTag(WORK_NAME)
                        .build()

                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        WORK_NAME,
                        ExistingWorkPolicy.KEEP,
                        request,
                    )
            }

            /**
             * Enqueues a continuation batch from within a running worker.
             * Uses REPLACE because the current work is still technically active
             * when this is called, so KEEP would silently drop the request.
             */
            private fun enqueueContinuation(context: Context) {
                val constraints =
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()

                val request =
                    OneTimeWorkRequestBuilder<EmbeddingGenerationWorker>()
                        .setConstraints(constraints)
                        .setInitialDelay(CONTINUATION_DELAY_SECONDS, TimeUnit.SECONDS)
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            BACKOFF_SECONDS,
                            TimeUnit.SECONDS,
                        )
                        .addTag(WORK_NAME)
                        .build()

                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        request,
                    )
            }

        }
    }

/**
 * Data class containing meme information needed for embedding generation.
 */
data class MemeDataForEmbedding(
    val id: Long,
    val filePath: String,
    val title: String?,
    val description: String?,
    val textContent: String?,
    val searchPhrases: String?,
    val emojiTagsJson: String? = null,
    val basedOn: String? = null,
)

/**
 * Repository interface for embedding work operations.
 * This abstracts the database operations needed by the worker.
 */
interface EmbeddingWorkRepository {
    /**
     * Get memes that need embedding generation.
     */
    suspend fun getMemesNeedingEmbeddings(limit: Int): List<MemeDataForEmbedding>

    /**
     * Save a generated embedding.
     */
    suspend fun saveEmbedding(
        memeId: Long,
        embedding: ByteArray,
        dimension: Int,
        modelVersion: String,
        sourceTextHash: String?,
        embeddingType: String = "content",
    )

    /**
     * Count memes that need embedding generation.
     */
    suspend fun countMemesNeedingEmbeddings(): Int

    /**
     * Delete embeddings with outdated model version.
     */
    suspend fun deleteOutdatedEmbeddings(currentVersion: String)
}
