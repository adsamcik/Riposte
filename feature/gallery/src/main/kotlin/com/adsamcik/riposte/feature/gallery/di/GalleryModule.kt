package com.adsamcik.riposte.feature.gallery.di

import com.adsamcik.riposte.feature.gallery.data.repository.GalleryRepositoryImpl
import com.adsamcik.riposte.feature.gallery.domain.repository.GalleryRepository
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
    abstract fun bindGalleryRepository(impl: GalleryRepositoryImpl): GalleryRepository
}
