package com.adsamcik.riposte.core.ml.di

import com.adsamcik.riposte.core.ml.worker.DefaultEmbeddingWorkRepository
import com.adsamcik.riposte.core.ml.worker.EmbeddingWorkRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for WorkManager-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerModule {

    @Binds
    @Singleton
    abstract fun bindEmbeddingWorkRepository(
        impl: DefaultEmbeddingWorkRepository
    ): EmbeddingWorkRepository
}
