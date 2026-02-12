package com.adsamcik.riposte.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.ImportRequestDao
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.database.dao.MemeSearchDao
import com.adsamcik.riposte.core.database.dao.ShareTargetDao
import com.adsamcik.riposte.core.database.entity.EmojiTagEntity
import com.adsamcik.riposte.core.database.entity.ImportRequestEntity
import com.adsamcik.riposte.core.database.entity.ImportRequestItemEntity
import com.adsamcik.riposte.core.database.entity.MemeEmbeddingEntity
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.adsamcik.riposte.core.database.entity.MemeFtsEntity
import com.adsamcik.riposte.core.database.entity.ShareTargetEntity

/**
 * Room database for Riposte app.
 */
@Database(
    entities = [
        MemeEntity::class,
        MemeFtsEntity::class,
        EmojiTagEntity::class,
        MemeEmbeddingEntity::class,
        ShareTargetEntity::class,
        ImportRequestEntity::class,
        ImportRequestItemEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class MemeDatabase : RoomDatabase() {
    companion object {
        /**
         * Current database version. Must match the version in the @Database annotation.
         * Referenced by migration tests to verify the migration chain is complete.
         */
        const val LATEST_VERSION = 6
    }

    /**
     * DAO for meme CRUD operations.
     */
    abstract fun memeDao(): MemeDao

    /**
     * DAO for full-text search operations.
     */
    abstract fun memeSearchDao(): MemeSearchDao

    /**
     * DAO for emoji tag operations.
     */
    abstract fun emojiTagDao(): EmojiTagDao

    /**
     * DAO for embedding operations.
     */
    abstract fun memeEmbeddingDao(): MemeEmbeddingDao

    /**
     * DAO for share target tracking.
     */
    abstract fun shareTargetDao(): ShareTargetDao

    /**
     * DAO for import request persistence.
     */
    abstract fun importRequestDao(): ImportRequestDao
}
