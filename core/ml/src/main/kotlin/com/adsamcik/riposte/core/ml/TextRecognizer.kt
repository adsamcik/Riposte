package com.adsamcik.riposte.core.ml

import android.graphics.Bitmap
import android.net.Uri

/**
 * Interface for extracting text from images using OCR.
 */
interface TextRecognizer {

    /**
     * Extracts text from an image bitmap.
     * 
     * @param bitmap The image to extract text from.
     * @return Extracted text, or null if no text was found.
     */
    suspend fun recognizeText(bitmap: Bitmap): String?

    /**
     * Extracts text from an image URI.
     * 
     * @param uri The URI of the image.
     * @return Extracted text, or null if no text was found.
     */
    suspend fun recognizeText(uri: Uri): String?

    /**
     * Checks if the recognizer is ready.
     */
    fun isReady(): Boolean

    /**
     * Releases resources.
     */
    fun close()
}

/**
 * Result of text recognition.
 */
data class TextRecognitionResult(
    /**
     * The full extracted text.
     */
    val text: String,

    /**
     * Individual text blocks with bounding boxes.
     */
    val blocks: List<TextBlock>,

    /**
     * Confidence score (0.0 to 1.0) if available.
     */
    val confidence: Float?
)

/**
 * A block of recognized text.
 */
data class TextBlock(
    val text: String,
    val boundingBox: BoundingBox?,
    val confidence: Float?
)

/**
 * Bounding box for text location.
 */
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
