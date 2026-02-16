package com.adsamcik.riposte.core.ml.di

import com.adsamcik.riposte.core.ml.DefaultSemanticSearchEngine
import com.adsamcik.riposte.core.ml.EmbeddingGemmaGenerator
import com.adsamcik.riposte.core.ml.EmbeddingGenerator
import com.adsamcik.riposte.core.ml.MediaPipeEmbeddingGenerator
import com.adsamcik.riposte.core.ml.MlKitTextRecognizer
import com.adsamcik.riposte.core.ml.SemanticSearchEngine
import com.adsamcik.riposte.core.ml.TextRecognizer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {
    @Binds
    @Singleton
    abstract fun bindTextRecognizer(impl: MlKitTextRecognizer): TextRecognizer

    /**
     * Bind the primary embedding generator.
     * Uses EmbeddingGemma via Google AI Edge RAG SDK for high-quality semantic embeddings.
     * EmbeddingGemma (2025) provides 768-dimensional embeddings with excellent multilingual support.
     */
    @Binds
    @Singleton
    abstract fun bindEmbeddingGenerator(impl: EmbeddingGemmaGenerator): EmbeddingGenerator

    @Binds
    @Singleton
    abstract fun bindSemanticSearchEngine(impl: DefaultSemanticSearchEngine): SemanticSearchEngine

    companion object {
        /**
         * Provides MediaPipe-based embedding generator as legacy fallback.
         * Uses Universal Sentence Encoder (USE-QA) - older model but proven reliable.
         */
        @Provides
        @Singleton
        @Named("mediapipe")
        fun provideMediaPipeEmbeddingGenerator(impl: MediaPipeEmbeddingGenerator): EmbeddingGenerator {
            return impl
        }
    }
}
