package com.adsamcik.riposte.feature.share.di

import com.adsamcik.riposte.feature.share.data.DefaultBitmapLoader
import com.adsamcik.riposte.feature.share.data.ShareRepositoryImpl
import com.adsamcik.riposte.feature.share.domain.BitmapLoader
import com.adsamcik.riposte.feature.share.domain.repository.ShareRepository
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

    @Binds
    @Singleton
    abstract fun bindBitmapLoader(
        impl: DefaultBitmapLoader,
    ): BitmapLoader
}
