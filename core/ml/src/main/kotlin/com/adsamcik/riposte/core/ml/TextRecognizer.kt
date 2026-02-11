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
