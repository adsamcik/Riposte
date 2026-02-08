package com.adsamcik.riposte.core.search.di

import com.adsamcik.riposte.core.search.data.SearchRepositoryImpl
import com.adsamcik.riposte.core.search.domain.repository.SearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {

    @Binds
    @Singleton
    abstract fun bindSearchRepository(
        impl: SearchRepositoryImpl,
    ): SearchRepository
}
