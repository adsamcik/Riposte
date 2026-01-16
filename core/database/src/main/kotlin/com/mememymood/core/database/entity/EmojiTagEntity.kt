package com.mememymood.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction table for normalized emoji tags.
 * Allows efficient queries like "find all memes with 😂 emoji".
 */
@Entity(
    tableName = "emoji_tags",
    primaryKeys = ["memeId", "emoji"],
    foreignKeys = [
        ForeignKey(
            entity = MemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["memeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["emoji"]),
        Index(value = ["memeId"])
    ]
)
data class EmojiTagEntity(
    /**
     * Foreign key to the meme.
     */
    val memeId: Long,
    
    /**
     * The emoji character (e.g., "😂").
     */
    val emoji: String,
    
    /**
     * The standardized name of the emoji (e.g., "face_with_tears_of_joy").
     * Used for search and display.
     */
    val emojiName: String
)
