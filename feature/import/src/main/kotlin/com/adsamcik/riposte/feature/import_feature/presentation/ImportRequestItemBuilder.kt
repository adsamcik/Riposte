package com.adsamcik.riposte.feature.import_feature.presentation

import com.adsamcik.riposte.core.model.MemeMetadata
import com.adsamcik.riposte.feature.import_feature.domain.model.ImportRequestItemData
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

internal object ImportRequestItemBuilder {
    fun build(
        requestId: String,
        index: Int,
        image: ImportImage,
        stagingDir: File,
    ): ImportRequestItemData {
        val stagedFileId = buildStagingFileId(requestId, index, image.fileName)
        val emojiStrings = image.emojis.map { it.emoji }
        return ImportRequestItemData(
            id = stagedFileId,
            stagedFilePath = File(stagingDir, stagedFileId).absolutePath,
            originalFileName = image.fileName,
            emojis = image.emojis.joinToString(",") { it.emoji },
            title = image.title,
            description = image.description,
            extractedText = image.extractedText,
            metadataJson = buildMetadataJson(image, emojiStrings),
        )
    }

    fun buildStagingFileId(
        requestId: String,
        index: Int,
        originalFileName: String,
    ): String {
        val extension = originalFileName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
        return if (extension != null) {
            "${requestId}_$index.$extension"
        } else {
            "${requestId}_$index"
        }
    }

    private fun buildMetadataJson(image: ImportImage, emojiStrings: List<String>): String? =
        if (emojiStrings.isNotEmpty()) {
            try {
                Json.encodeToString(
                    MemeMetadata(
                        emojis = emojiStrings,
                        title = image.title,
                        description = image.description,
                        textContent = image.extractedText,
                        searchPhrases = image.searchPhrases,
                        basedOn = image.basedOn,
                        primaryLanguage = image.primaryLanguage,
                        localizations = image.localizations,
                    ),
                )
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Timber.w(e, "Failed to serialize metadata during import")
                null
            }
        } else {
            null
        }
}
