package com.adsamcik.riposte.core.ml.di

import com.adsamcik.riposte.core.ml.DefaultSemanticSearchEngine
import com.adsamcik.riposte.core.ml.EmbeddingGenerator
import com.adsamcik.riposte.core.ml.MindlayerEmbeddingGenerator
import com.adsamcik.riposte.core.ml.MindlayerTextRecognizer
import com.adsamcik.riposte.core.ml.search.SemanticSearchStrategy
import com.adsamcik.riposte.core.ml.SemanticSearchEngine
import com.adsamcik.riposte.core.ml.TextRecognizer
import com.adsamcik.riposte.core.model.SearchStrategy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {
    /**
     * OCR via the Mindlayer service (PaddleOCR PP-OCRv5 on-device).
     * Falls back to no-op when the service does not advertise OCR.
     */
    @Binds
    @Singleton
    abstract fun bindTextRecognizer(impl: MindlayerTextRecognizer): TextRecognizer

    /**
     * Embeddings via the Mindlayer service (EmbeddingGemma-300M on-device).
     * Replaces the previous in-process LiteRT + SentencePiece pipeline.
     */
    @Binds
    @Singleton
    abstract fun bindEmbeddingGenerator(impl: MindlayerEmbeddingGenerator): EmbeddingGenerator

    @Binds
    @Singleton
    abstract fun bindSemanticSearchEngine(impl: DefaultSemanticSearchEngine): SemanticSearchEngine

    @Binds
    @IntoSet
    abstract fun bindSemanticSearchStrategy(impl: SemanticSearchStrategy): SearchStrategy
}

