package com.mememymood.core.ml.di

import com.mememymood.core.ml.DefaultSemanticSearchEngine
import com.mememymood.core.ml.EmbeddingGenerator
import com.mememymood.core.ml.MlKitTextRecognizer
import com.mememymood.core.ml.SemanticSearchEngine
import com.mememymood.core.ml.SimpleEmbeddingGenerator
import com.mememymood.core.ml.TextRecognizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {

    @Binds
    @Singleton
    abstract fun bindTextRecognizer(
        impl: MlKitTextRecognizer
    ): TextRecognizer

    @Binds
    @Singleton
    abstract fun bindEmbeddingGenerator(
        impl: SimpleEmbeddingGenerator
    ): EmbeddingGenerator

    @Binds
    @Singleton
    abstract fun bindSemanticSearchEngine(
        impl: DefaultSemanticSearchEngine
    ): SemanticSearchEngine
}
