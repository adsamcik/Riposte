package com.adsamcik.riposte.core.ml

import android.content.Context
import timber.log.Timber
import java.io.File

internal fun Context.embeddingGemmaModelDir(): File =
    File(filesDir, EmbeddingGemmaGenerator.MODEL_DIRECTORY)

internal fun Context.getEmbeddingGemmaModelPath(): String {
    val modelDir = embeddingGemmaModelDir()
    val bestModelFile = EmbeddingGemmaGenerator.getBestModelFilename()
    val optimizedPath = File(modelDir, bestModelFile)
    val genericPath = File(modelDir, EmbeddingGemmaGenerator.MODEL_FILENAME_GENERIC)

    return when {
        optimizedPath.exists() -> {
            Timber.d("Using optimized model: $bestModelFile")
            optimizedPath.absolutePath
        }
        genericPath.exists() -> {
            Timber.d("Using generic model (optimized not found)")
            genericPath.absolutePath
        }
        else -> optimizedPath.absolutePath
    }
}

internal fun Context.getGenericEmbeddingGemmaModelPath(): String? {
    val genericFile = File(embeddingGemmaModelDir(), EmbeddingGemmaGenerator.MODEL_FILENAME_GENERIC)
    return if (genericFile.exists()) genericFile.absolutePath else null
}

internal fun Context.getEmbeddingGemmaTokenizerPath(): String =
    File(embeddingGemmaModelDir(), EmbeddingGemmaGenerator.TOKENIZER_FILENAME).absolutePath

internal fun createZeroEmbedding(dimension: Int): FloatArray = FloatArray(dimension)
