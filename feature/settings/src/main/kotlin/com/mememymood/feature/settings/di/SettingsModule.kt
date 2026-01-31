package com.mememymood.feature.settings.di

import com.mememymood.feature.settings.data.repository.DefaultSettingsRepository
import com.mememymood.feature.settings.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: DefaultSettingsRepository,
    ): SettingsRepository
}
