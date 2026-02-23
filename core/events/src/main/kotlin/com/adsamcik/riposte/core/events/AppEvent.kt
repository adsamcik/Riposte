package com.adsamcik.riposte.core.events

/**
 * Base interface for all domain events in the Riposte app.
 *
 * Events are ephemeral notifications used for cross-feature coordination
 * without direct module dependencies. They do NOT replace Room Flows for
 * data consistency — they signal that something happened so interested
 * parties can react (refresh suggestions, invalidate caches, update UI).
 */
interface AppEvent

/**
 * A meme was successfully shared to another app.
 *
 * @property memeId Database ID of the shared meme.
 * @property targetPackage Package name of the target app, if known.
 * @property timestampMs Wall-clock time of the share action.
 */
data class MemeShared(
    val memeId: Long,
    val targetPackage: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
) : AppEvent

/**
 * A meme was imported into the library (image saved + DB row created).
 *
 * @property memeId Database ID of the newly imported meme.
 * @property source Human-readable source description (e.g., "zip_bundle", "gallery_pick").
 */
data class MemeImported(
    val memeId: Long,
    val source: String,
) : AppEvent

/**
 * A meme was viewed in the detail screen.
 *
 * @property memeId Database ID of the viewed meme.
 */
data class MemeViewed(
    val memeId: Long,
) : AppEvent

/**
 * A meme's favorite status was toggled.
 *
 * @property memeId Database ID of the meme.
 * @property isFavorite New favorite state.
 */
data class MemeFavorited(
    val memeId: Long,
    val isFavorite: Boolean,
) : AppEvent

/**
 * One or more memes were deleted from the library.
 *
 * @property memeIds Set of deleted meme database IDs.
 */
data class MemeDeleted(
    val memeIds: Set<Long>,
) : AppEvent

/**
 * A batch of embedding generation completed.
 *
 * @property processedCount Number of memes successfully embedded in this batch.
 * @property failedCount Number of memes that failed embedding.
 * @property remainingCount Number of memes still awaiting embedding.
 */
data class EmbeddingsReady(
    val processedCount: Int,
    val failedCount: Int,
    val remainingCount: Int,
) : AppEvent
