package com.adsamcik.riposte.feature.import_feature.domain.model

/**
 * Domain representation of an import request item, decoupled from Room entities.
 */
data class ImportRequestItemData(
    val id: String,
    val stagedFilePath: String,
    val originalFileName: String,
    val emojis: String,
    val title: String?,
    val description: String?,
    val extractedText: String?,
    val metadataJson: String?,
)
