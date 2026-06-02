package com.adsamcik.riposte.core.ml.worker

import com.adsamcik.riposte.core.ml.EmbeddingGenerator
import com.adsamcik.riposte.core.model.EmbeddingType
import com.adsamcik.riposte.core.model.EmotionData
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

internal class EmbeddingSlotGenerator(
    private val embeddingGenerator: EmbeddingGenerator,
    private val embeddingRepository: EmbeddingWorkRepository,
) {
    suspend fun process(memeData: MemeDataForEmbedding): Boolean =
        try {
            val generated = listOf(
                generateContentEmbedding(memeData),
                generateTextEmbedding(memeData.id, buildIntentText(memeData), EmbeddingType.INTENT),
                generateTextEmbedding(memeData.id, buildEmojiText(memeData), EmbeddingType.EMOJI),
                generateTextEmbedding(
                    memeData.id,
                    buildDifferentiatorText(memeData),
                    EmbeddingType.DIFFERENTIATOR,
                ),
                generateTextEmbedding(memeData.id, buildEmotionText(memeData), EmbeddingType.EMOTION),
            )
            generated.any { it }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
            Timber.w(e, "Failed to generate embedding for meme ${memeData.id}")
            false
        }

    private suspend fun generateContentEmbedding(memeData: MemeDataForEmbedding): Boolean {
        val (title, body) = buildContentParts(memeData)
        val contentText = if (title != null) "$title. $body" else body
        return if (contentText.isBlank()) {
            false
        } else {
            val embedding = embeddingGenerator.generateFromText(body, title)
            saveGeneratedEmbedding(memeData.id, embedding, contentText, EmbeddingType.CONTENT)
            true
        }
    }

    private suspend fun generateTextEmbedding(
        memeId: Long,
        text: String,
        embeddingType: EmbeddingType,
    ): Boolean =
        if (text.isBlank()) {
            false
        } else {
            val embedding = embeddingGenerator.generateFromText(text)
            saveGeneratedEmbedding(memeId, embedding, text, embeddingType)
            true
        }

    private suspend fun saveGeneratedEmbedding(
        memeId: Long,
        embedding: FloatArray,
        sourceText: String,
        embeddingType: EmbeddingType,
    ) {
        embeddingRepository.saveEmbedding(
            memeId = memeId,
            embedding = encodeEmbedding(embedding),
            dimension = embedding.size,
            modelVersion = EmbeddingGenerationWorker.CURRENT_MODEL_VERSION,
            sourceTextHash = generateHash(sourceText),
            embeddingType = embeddingType.key,
        )
    }

    private fun buildContentParts(memeData: MemeDataForEmbedding): Pair<String?, String> {
        val body = buildString {
            memeData.description?.let { append(it).append(". ") }
            memeData.textContent?.let { append(it).append(". ") }
        }.trim().trimEnd('.')
        return Pair(memeData.title, body.ifBlank { memeData.title ?: "" })
    }

    private fun buildIntentText(memeData: MemeDataForEmbedding): String {
        val jsonString = memeData.searchPhrases?.takeIf { it.isNotBlank() } ?: return ""
        val phrases = try {
            Json.decodeFromString<List<String>>(jsonString)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
            Timber.d(e, "Failed to parse search phrases as JSON, falling back to comma-separated format")
            jsonString.split(",").map { it.trim() }
        }
        return phrases.joinToString(". ")
    }

    private fun buildEmojiText(memeData: MemeDataForEmbedding): String {
        val jsonString = memeData.emojiTagsJson?.takeIf { it.isNotBlank() } ?: return ""
        val emojis = try {
            Json.decodeFromString<List<String>>(jsonString)
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

    private fun resolveEmojiName(emoji: String): String {
        val names = mutableListOf<String>()
        var i = 0
        while (i < emoji.length) {
            val codePoint = Character.codePointAt(emoji, i)
            i += Character.charCount(codePoint)

            if (!isNonSemanticCodepoint(codePoint)) {
                Character.getName(codePoint)?.let { names.add(it.lowercase()) }
            }
        }
        return names.joinToString(" ")
    }

    private fun isNonSemanticCodepoint(codePoint: Int): Boolean =
        codePoint == ZWJ_CODEPOINT ||
            codePoint == VARIATION_SELECTOR_16 ||
            codePoint == VARIATION_SELECTOR_15 ||
            codePoint in SKIN_TONE_MODIFIER_RANGE

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

    private fun buildEmotionText(memeData: MemeDataForEmbedding): String {
        val jsonString = memeData.emotionsJson?.takeIf { it.isNotBlank() } ?: return ""
        return try {
            val emotions = Json.decodeFromString<EmotionData>(jsonString)
            buildEmotionText(emotions)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
            Timber.d(e, "Failed to parse emotions JSON")
            ""
        }
    }

    private fun buildEmotionText(emotions: EmotionData): String {
        val parts = mutableListOf(emotions.primary)
        if (emotions.secondary.isNotEmpty()) {
            parts.add(emotions.secondary.joinToString(", "))
        }
        parts.add("${emotions.sentiment} ${emotions.intensity}")
        if (emotions.memeUsage.isNotEmpty()) {
            parts.addAll(emotions.memeUsage)
        }
        return parts.joinToString(". ")
    }

    private fun encodeEmbedding(embedding: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(embedding.size * BYTES_PER_FLOAT)
            .order(ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun generateHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(text.toByteArray(Charsets.UTF_8))
        return hash.take(HASH_BYTE_LENGTH).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        private const val BYTES_PER_FLOAT = 4
        private const val HASH_BYTE_LENGTH = 16
        private const val ZWJ_CODEPOINT = 0x200D
        private const val VARIATION_SELECTOR_16 = 0xFE0F
        private const val VARIATION_SELECTOR_15 = 0xFE0E
        private val SKIN_TONE_MODIFIER_RANGE = 0x1F3FB..0x1F3FF
    }
}
