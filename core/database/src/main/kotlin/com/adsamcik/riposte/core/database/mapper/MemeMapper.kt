package com.adsamcik.riposte.core.database.mapper

import com.adsamcik.riposte.core.database.entity.EmojiTagEntity
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.LocalizedContent
import com.adsamcik.riposte.core.model.Meme
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
            searchPhrases = parseSearchPhrasesJson(searchPhrasesJson),
            isFavorite = isFavorite,
            createdAt = createdAt,
            useCount = useCount,
            primaryLanguage = primaryLanguage,
            localizations = parseLocalizationsJson(localizationsJson),
            viewCount = viewCount,
            lastViewedAt = lastViewedAt,
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
            searchPhrasesJson = serializeSearchPhrases(searchPhrases),
            isFavorite = isFavorite,
            createdAt = createdAt,
            useCount = useCount,
            primaryLanguage = primaryLanguage,
            localizationsJson = serializeLocalizations(localizations),
            viewCount = viewCount,
            lastViewedAt = lastViewedAt,
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

    /**
     * Parses localizations from JSON string.
     */
    private fun parseLocalizationsJson(jsonString: String?): Map<String, LocalizedContent> {
        if (jsonString.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Serializes localizations to JSON string.
     */
    private fun serializeLocalizations(localizations: Map<String, LocalizedContent>): String? {
        if (localizations.isEmpty()) return null
        return json.encodeToString(localizations)
    }

    /**
     * Parses search phrases from JSON string.
     */
    private fun parseSearchPhrasesJson(jsonString: String?): List<String> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Serializes search phrases to JSON string.
     */
    private fun serializeSearchPhrases(searchPhrases: List<String>): String? {
        if (searchPhrases.isEmpty()) return null
        return json.encodeToString(searchPhrases)
    }
}
