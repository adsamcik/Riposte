package com.adsamcik.riposte.feature.settings.di

import com.adsamcik.riposte.feature.settings.data.repository.DefaultSettingsRepository
import com.adsamcik.riposte.feature.settings.domain.repository.SettingsRepository
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
