package com.adsamcik.riposte.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit-based text recognizer implementation.
 */
@Singleton
class MlKitTextRecognizer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : TextRecognizer {
        private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        override suspend fun recognizeText(bitmap: Bitmap): String? =
            withContext(Dispatchers.Default) {
                val image = InputImage.fromBitmap(bitmap, 0)
                processImage(image)
            }

        override suspend fun recognizeText(uri: Uri): String? =
            withContext(Dispatchers.Default) {
                val image = InputImage.fromFilePath(context, uri)
                processImage(image)
            }

        private suspend fun processImage(image: InputImage): String? {
            return suspendCancellableCoroutine { continuation ->
                val task =
                    recognizer.process(image)
                        .addOnSuccessListener { result ->
                            val text = result.text.takeIf { it.isNotBlank() }
                            continuation.resume(text)
                        }
                        .addOnFailureListener { exception ->
                            continuation.resumeWithException(exception)
                        }

                continuation.invokeOnCancellation {
                    // ML Kit tasks cannot be cancelled, but we acknowledge the cancellation
                    // The callback will still fire but continuation is already cancelled
                }
            }
        }

        override fun isReady(): Boolean = true

        override fun close() {
            recognizer.close()
        }
    }
