package com.mememymood.core.database.di

import android.content.Context
import androidx.room.Room
import com.mememymood.core.database.MemeDatabase
import com.mememymood.core.database.dao.EmojiTagDao
import com.mememymood.core.database.dao.MemeDao
import com.mememymood.core.database.dao.MemeEmbeddingDao
import com.mememymood.core.database.dao.MemeSearchDao
import com.mememymood.core.database.dao.ShareTargetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMemeDatabase(
        @ApplicationContext context: Context
    ): MemeDatabase {
        return Room.databaseBuilder(
            context,
            MemeDatabase::class.java,
            "meme_my_mood.db"
        )
            .addMigrations(
                MemeDatabase.MIGRATION_1_2,
                MemeDatabase.MIGRATION_2_3,
                MemeDatabase.MIGRATION_3_4,
                MemeDatabase.MIGRATION_4_5,
                MemeDatabase.MIGRATION_5_6,
                MemeDatabase.MIGRATION_6_7,
                MemeDatabase.MIGRATION_7_8,
                MemeDatabase.MIGRATION_8_9,
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideMemeDao(database: MemeDatabase): MemeDao {
        return database.memeDao()
    }

    @Provides
    @Singleton
    fun provideMemeSearchDao(database: MemeDatabase): MemeSearchDao {
        return database.memeSearchDao()
    }

    @Provides
    @Singleton
    fun provideEmojiTagDao(database: MemeDatabase): EmojiTagDao {
        return database.emojiTagDao()
    }

    @Provides
    @Singleton
    fun provideMemeEmbeddingDao(database: MemeDatabase): MemeEmbeddingDao {
        return database.memeEmbeddingDao()
    }

    @Provides
    @Singleton
    fun provideShareTargetDao(database: MemeDatabase): ShareTargetDao {
        return database.shareTargetDao()
    }
}
