package com.mememymood.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Simple embedding generator using text hashing and image labeling.
 * 
 * This is a placeholder implementation. In production, you would use:
 * - TensorFlow Lite with Universal Sentence Encoder for text
 * - A proper image embedding model (MobileNet, etc.)
 * 
 * This implementation provides a working baseline for development.
 */
@Singleton
class SimpleEmbeddingGenerator @Inject constructor(
    @param:ApplicationContext private val context: Context
) : EmbeddingGenerator {

    private val imageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
    )

    override val embeddingDimension: Int = 128

    override suspend fun generateFromText(text: String): FloatArray = withContext(Dispatchers.Default) {
        // Simple text embedding using hash-based approach
        // In production, use Universal Sentence Encoder or similar
        textToEmbedding(text.lowercase().trim())
    }

    override suspend fun generateFromImage(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        // Get image labels
        val labels = getImageLabels(bitmap)
        
        // Convert labels to text and generate embedding
        val labelText = labels.joinToString(" ")
        textToEmbedding(labelText)
    }

    override suspend fun generateFromUri(uri: Uri): FloatArray = withContext(Dispatchers.IO) {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
        
        if (bitmap != null) {
            try {
                generateFromImage(bitmap)
            } finally {
                bitmap.recycle()
            }
        } else {
            FloatArray(embeddingDimension)
        }
    }

    private suspend fun getImageLabels(bitmap: Bitmap): List<String> {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            
            val task = imageLabeler.process(image)
                .addOnSuccessListener { labels ->
                    val labelTexts = labels.map { it.text.lowercase() }
                    continuation.resume(labelTexts)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
            
            continuation.invokeOnCancellation {
                // ML Kit tasks cannot be cancelled, but we log for debugging
                // The callback will still fire but continuation is already cancelled
            }
        }
    }

    /**
     * Simple text to embedding conversion using hash-based approach.
     * This is deterministic and provides reasonable similarity for similar texts.
     */
    private fun textToEmbedding(text: String): FloatArray {
        val embedding = FloatArray(embeddingDimension)
        
        if (text.isBlank()) return embedding

        // Tokenize
        val words = text.split(Regex("\\s+"))
        
        // Create word embeddings and average them
        for (word in words) {
            val wordEmbedding = hashToVector(word)
            for (i in embedding.indices) {
                embedding[i] += wordEmbedding[i]
            }
        }

        // Normalize
        val norm = kotlin.math.sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }

        return embedding
    }

    /**
     * Converts a word to a pseudo-random but deterministic vector.
     */
    private fun hashToVector(word: String): FloatArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(word.toByteArray())
        
        val vector = FloatArray(embeddingDimension)
        for (i in 0 until embeddingDimension) {
            // Use hash bytes to generate float values between -1 and 1
            val byteIndex = i % hash.size
            vector[i] = (hash[byteIndex].toInt() and 0xFF) / 127.5f - 1f
        }
        
        return vector
    }

    override suspend fun isReady(): Boolean = true

    override suspend fun initialize() {
        // No initialization needed for this simple implementation
    }

    override fun close() {
        imageLabeler.close()
    }
}
