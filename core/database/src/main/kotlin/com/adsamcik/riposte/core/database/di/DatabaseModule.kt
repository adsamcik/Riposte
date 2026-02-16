package com.adsamcik.riposte.core.database.di

import android.content.Context
import androidx.room.Room
import com.adsamcik.riposte.core.database.ALL_MIGRATIONS
import com.adsamcik.riposte.core.database.MemeDatabase
import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.ImportRequestDao
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.database.dao.MemeSearchDao
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
        @ApplicationContext context: Context,
    ): MemeDatabase {
        return Room.databaseBuilder(
            context,
            MemeDatabase::class.java,
            "riposte.db",
        )
            .addMigrations(*ALL_MIGRATIONS)
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
    fun provideImportRequestDao(database: MemeDatabase): ImportRequestDao {
        return database.importRequestDao()
    }
}
