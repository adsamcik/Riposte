package com.mememymood.core.ml.di

import android.content.Context
import com.mememymood.core.ml.DefaultSemanticSearchEngine
import com.mememymood.core.ml.EmbeddingGenerator
import com.mememymood.core.ml.EmbeddingGemmaGenerator
import com.mememymood.core.ml.MediaPipeEmbeddingGenerator
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
     * Uses EmbeddingGemma via Google AI Edge RAG SDK for high-quality semantic embeddings.
     * EmbeddingGemma (2025) provides 768-dimensional embeddings with excellent multilingual support.
     */
    @Binds
    @Singleton
    abstract fun bindEmbeddingGenerator(
        impl: EmbeddingGemmaGenerator
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

        /**
         * Provides MediaPipe-based embedding generator as legacy fallback.
         * Uses Universal Sentence Encoder (USE-QA) - older model but proven reliable.
         */
        @Provides
        @Singleton
        @Named("mediapipe")
        fun provideMediaPipeEmbeddingGenerator(
            impl: MediaPipeEmbeddingGenerator
        ): EmbeddingGenerator {
            return impl
        }
    }
}
