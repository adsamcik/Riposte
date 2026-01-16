package com.mememymood.feature.import_feature.di

import com.mememymood.feature.import_feature.data.ImportRepositoryImpl
import com.mememymood.feature.import_feature.domain.repository.ImportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImportModule {

    @Binds
    @Singleton
    abstract fun bindImportRepository(
        impl: ImportRepositoryImpl
    ): ImportRepository
}
