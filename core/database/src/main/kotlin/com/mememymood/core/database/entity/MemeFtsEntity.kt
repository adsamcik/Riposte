package com.mememymood.core.database.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 virtual table for full-text search on memes.
 * This table is automatically kept in sync with the memes table by Room.
 */
@Entity(tableName = "memes_fts")
@Fts4(contentEntity = MemeEntity::class)
data class MemeFtsEntity(
    /**
     * File name for searching by name.
     */
    val fileName: String,
    
    /**
     * JSON of emoji tags for emoji search.
     * Note: Contains both emoji characters and their names for better matching.
     */
    val emojiTagsJson: String,
    
    /**
     * Title for text search.
     */
    val title: String?,
    
    /**
     * Description for text search.
     */
    val description: String?,
    
    /**
     * OCR-extracted text for content search.
     */
    val textContent: String?
)
