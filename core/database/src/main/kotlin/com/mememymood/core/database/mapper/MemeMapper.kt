package com.mememymood.core.database.mapper

import com.mememymood.core.database.entity.EmojiTagEntity
import com.mememymood.core.database.entity.MemeEntity
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.Meme
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Maps between database entities and domain models.
 */
object MemeMapper {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Converts a MemeEntity to a Meme domain model.
     */
    fun MemeEntity.toDomain(emojiTags: List<EmojiTagEntity>? = null): Meme {
        val parsedEmojis = emojiTags?.map { it.toDomain() } 
            ?: parseEmojiTagsJson(emojiTagsJson)
        
        return Meme(
            id = id,
            filePath = filePath,
            fileName = fileName,
            mimeType = mimeType,
            width = width,
            height = height,
            fileSizeBytes = fileSizeBytes,
            importedAt = importedAt,
            emojiTags = parsedEmojis,
            title = title,
            description = description,
            textContent = textContent,
            isFavorite = isFavorite
        )
    }

    /**
     * Converts a Meme domain model to a MemeEntity.
     */
    fun Meme.toEntity(): MemeEntity {
        return MemeEntity(
            id = id,
            filePath = filePath,
            fileName = fileName,
            mimeType = mimeType,
            width = width,
            height = height,
            fileSizeBytes = fileSizeBytes,
            importedAt = importedAt,
            emojiTagsJson = serializeEmojiTags(emojiTags),
            title = title,
            description = description,
            textContent = textContent,
            isFavorite = isFavorite
        )
    }

    /**
     * Converts an EmojiTagEntity to an EmojiTag domain model.
     */
    fun EmojiTagEntity.toDomain(): EmojiTag {
        return EmojiTag(
            emoji = emoji,
            name = emojiName
        )
    }

    /**
     * Converts an EmojiTag domain model to an EmojiTagEntity.
     */
    fun EmojiTag.toEntity(memeId: Long): EmojiTagEntity {
        return EmojiTagEntity(
            memeId = memeId,
            emoji = emoji,
            emojiName = name
        )
    }

    /**
     * Parses emoji tags from JSON string.
     */
    private fun parseEmojiTagsJson(jsonString: String): List<EmojiTag> {
        return try {
            val emojis: List<String> = json.decodeFromString(jsonString)
            emojis.map { EmojiTag.fromEmoji(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Serializes emoji tags to JSON string.
     */
    private fun serializeEmojiTags(emojiTags: List<EmojiTag>): String {
        return json.encodeToString(emojiTags.map { it.emoji })
    }
}
