package com.adsamcik.riposte.feature.share.di

import com.adsamcik.riposte.core.common.share.ShareRepository
import com.adsamcik.riposte.feature.share.data.ShareRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ShareModule {
    @Binds
    @Singleton
    abstract fun bindShareRepository(impl: ShareRepositoryImpl): ShareRepository
}
