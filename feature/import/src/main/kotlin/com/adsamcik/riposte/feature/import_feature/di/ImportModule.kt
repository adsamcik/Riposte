package com.adsamcik.riposte.feature.import_feature.di

import com.adsamcik.riposte.feature.import_feature.data.DefaultZipImporter
import com.adsamcik.riposte.feature.import_feature.data.ImportRepositoryImpl
import com.adsamcik.riposte.feature.import_feature.domain.ZipImporter
import com.adsamcik.riposte.feature.import_feature.domain.repository.ImportRepository
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

    @Binds
    abstract fun bindZipImporter(
        impl: DefaultZipImporter
    ): ZipImporter
}
