package com.mememymood.feature.share.di

import com.mememymood.feature.share.data.ShareRepositoryImpl
import com.mememymood.feature.share.domain.repository.ShareRepository
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
    abstract fun bindShareRepository(
        impl: ShareRepositoryImpl,
    ): ShareRepository
}
