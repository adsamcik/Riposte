package com.mememymood.feature.gallery.di

import com.mememymood.feature.gallery.data.repository.GalleryRepositoryImpl
import com.mememymood.feature.gallery.data.repository.ShareTargetRepositoryImpl
import com.mememymood.feature.gallery.domain.repository.GalleryRepository
import com.mememymood.feature.gallery.domain.repository.ShareTargetRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GalleryModule {

    @Binds
    @Singleton
    abstract fun bindGalleryRepository(
        impl: GalleryRepositoryImpl
    ): GalleryRepository

    @Binds
    @Singleton
    abstract fun bindShareTargetRepository(
        impl: ShareTargetRepositoryImpl
    ): ShareTargetRepository
}
