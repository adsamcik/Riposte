package com.adsamcik.riposte.core.database.di

import com.adsamcik.riposte.core.database.repository.ShareTargetRepository
import com.adsamcik.riposte.core.database.repository.ShareTargetRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindShareTargetRepository(impl: ShareTargetRepositoryImpl): ShareTargetRepository
}
