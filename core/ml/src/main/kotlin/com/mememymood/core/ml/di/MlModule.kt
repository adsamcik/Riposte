package com.mememymood.core.ml.di

import android.content.Context
import com.mememymood.core.ml.DefaultSemanticSearchEngine
import com.mememymood.core.ml.EmbeddingGenerator
import com.mememymood.core.ml.MlKitTextRecognizer
import com.mememymood.core.ml.SemanticSearchEngine
import com.mememymood.core.ml.SimpleEmbeddingGenerator
import com.mememymood.core.ml.TextRecognizer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {

    @Binds
    @Singleton
    abstract fun bindTextRecognizer(
        impl: MlKitTextRecognizer
    ): TextRecognizer

    /**
     * Bind the primary embedding generator.
     * Uses hash-based embeddings for reliable cosine similarity search.
     */
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

    companion object {
        /**
         * Provides the fallback embedding generator for testing or when LiteRT is unavailable.
         */
        @Provides
        @Singleton
        @Named("fallback")
        fun provideFallbackEmbeddingGenerator(
            @ApplicationContext context: Context
        ): EmbeddingGenerator {
            return SimpleEmbeddingGenerator(context)
        }
    }
}
