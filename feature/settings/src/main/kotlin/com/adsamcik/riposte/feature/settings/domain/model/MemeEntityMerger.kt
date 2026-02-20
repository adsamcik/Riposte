package com.adsamcik.riposte.feature.settings.domain.model

import com.adsamcik.riposte.core.database.entity.MemeEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * Intelligently merges metadata from two duplicate memes.
 *
 * Merge rules:
 * - Image: keep the higher resolution (width × height), fallback to larger file size
 * - Emoji tags: union of both sets
 * - Title/Description/TextContent: keep the longer non-null value
 * - Search phrases: union of both sets
 * - Use count / View count: sum both
 * - Favorite: true if either is favorited
 * - ImportedAt / CreatedAt: keep the earliest
 */
class MemeEntityMerger @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Determine which meme should be the "winner" (kept) and produce merged metadata.
     *
     * @return Pair of (winnerId, merged metadata fields)
     */
    fun merge(meme1: MemeEntity, meme2: MemeEntity): MergedMemeData {
        val winner: MemeEntity
        val loser: MemeEntity

        // Keep the higher resolution image
        val pixels1 = meme1.width.toLong() * meme1.height
        val pixels2 = meme2.width.toLong() * meme2.height

        if (pixels1 > pixels2 || (pixels1 == pixels2 && meme1.fileSizeBytes >= meme2.fileSizeBytes)) {
            winner = meme1
            loser = meme2
        } else {
            winner = meme2
            loser = meme1
        }

        return MergedMemeData(
            winnerId = winner.id,
            loserId = loser.id,
            loserFilePath = loser.filePath,
            emojiTagsJson = unionEmojiTags(meme1.emojiTagsJson, meme2.emojiTagsJson),
            title = longerOrFirst(meme1.title, meme2.title),
            description = longerOrFirst(meme1.description, meme2.description),
            textContent = longerOrFirst(meme1.textContent, meme2.textContent),
            searchPhrasesJson = unionSearchPhrases(meme1.searchPhrasesJson, meme2.searchPhrasesJson),
            useCount = meme1.useCount + meme2.useCount,
            viewCount = meme1.viewCount + meme2.viewCount,
            isFavorite = meme1.isFavorite || meme2.isFavorite,
        )
    }

    private fun longerOrFirst(a: String?, b: String?): String? {
        if (a == null) return b
        if (b == null) return a
        return if (b.length > a.length) b else a
    }

    private fun unionEmojiTags(json1: String, json2: String): String {
        val set1 = parseJsonStringArray(json1)
        val set2 = parseJsonStringArray(json2)
        val union = (set1 + set2).distinct()
        return json.encodeToString(
            JsonArray.serializer(),
            JsonArray(union.map { kotlinx.serialization.json.JsonPrimitive(it) }),
        )
    }

    private fun unionSearchPhrases(json1: String?, json2: String?): String? {
        if (json1 == null && json2 == null) return null
        val set1 = json1?.let { parseJsonStringArray(it) } ?: emptyList()
        val set2 = json2?.let { parseJsonStringArray(it) } ?: emptyList()
        val union = (set1 + set2).distinct()
        if (union.isEmpty()) return null
        return json.encodeToString(
            JsonArray.serializer(),
            JsonArray(union.map { kotlinx.serialization.json.JsonPrimitive(it) }),
        )
    }

    private fun parseJsonStringArray(jsonStr: String): List<String> =
        try {
            json.decodeFromString(JsonArray.serializer(), jsonStr)
                .map { it.jsonPrimitive.content }
        } catch (_: Exception) {
            emptyList()
        }
}

/**
 * Holds the result of a merge computation — the fields to write to the winner meme.
 */
data class MergedMemeData(
    val winnerId: Long,
    val loserId: Long,
    val loserFilePath: String,
    val emojiTagsJson: String,
    val title: String?,
    val description: String?,
    val textContent: String?,
    val searchPhrasesJson: String?,
    val useCount: Int,
    val viewCount: Int,
    val isFavorite: Boolean,
)
