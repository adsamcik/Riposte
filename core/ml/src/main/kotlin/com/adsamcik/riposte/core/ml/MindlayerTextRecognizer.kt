package com.adsamcik.riposte.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.adsamcik.mindlayer.sdk.MindlayerException
import com.adsamcik.mindlayer.sdk.OcrProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TextRecognizer] backed by the Mindlayer SDK.
 *
 * Delegates OCR to the Mindlayer on-device service, which runs PaddleOCR
 * PP-OCRv5 under the hood. The previous in-process ML Kit Text Recognition
 * implementation is replaced wholesale.
 *
 * # Availability
 *
 * Mindlayer's OCR capability is currently gated behind
 * `OcrFeatureFlags.IS_PRODUCTION_READY` on the service side. When the service
 * does not advertise [com.adsamcik.mindlayer.ServiceCapabilities.FEATURE_OCR_SESSION],
 * [isReady] returns false and [recognizeText] returns `null` rather than
 * throwing, so callers degrade transparently.
 *
 * # Profile choice
 *
 * Memes are typically scene-text or screenshot-like, not receipts or ID
 * cards, so [OcrProfile.GeneralDocument] is the right default. The returned
 * `lines` are joined with newlines to match the previous ML Kit shape.
 */
@Singleton
class MindlayerTextRecognizer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val mindlayerClient: MindlayerClient,
    ) : TextRecognizer {

        override suspend fun recognizeText(bitmap: Bitmap): String? =
            withContext(Dispatchers.Default) {
                runOcr { it.readText(bitmap, profile = OcrProfile.GeneralDocument) }
            }

        override suspend fun recognizeText(uri: Uri): String? =
            withContext(Dispatchers.IO) {
                val (bytes, mime) = readUriBytes(uri) ?: return@withContext null
                runOcr { it.readText(bytes, mimeType = mime, profile = OcrProfile.GeneralDocument) }
            }

        override fun isReady(): Boolean =
            when (val state = mindlayerClient.availability.value) {
                is MindlayerAvailability.Ready -> state.capabilities.supports(
                    com.adsamcik.mindlayer.ServiceCapabilities.FEATURE_OCR_SESSION,
                )
                else -> false
            }

        override fun close() {
            // No per-recognizer resources to release — Mindlayer connection is owned by MindlayerClient.
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend inline fun runOcr(
            block: (com.adsamcik.mindlayer.sdk.Mindlayer) -> String,
        ): String? {
            val session = try {
                mindlayerClient.awaitMindlayer()
            } catch (e: MindlayerUnavailableException) {
                Timber.d(e, "Mindlayer OCR unavailable — skipping recognition")
                return null
            }

            if (!session.supportsOcr) {
                Timber.d("Mindlayer service does not advertise FEATURE_OCR_SESSION — skipping")
                return null
            }

            return try {
                block(session.mindlayer).takeIf { it.isNotBlank() }
            } catch (e: MindlayerException) {
                Timber.w(e, "Mindlayer OCR call failed (code=%d)", e.code)
                null
            } catch (e: Exception) {
                Timber.w(e, "Unexpected error during Mindlayer OCR")
                null
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun readUriBytes(uri: Uri): Pair<ByteArray, String>? = try {
            val resolver = context.contentResolver
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val mime = resolver.getType(uri) ?: guessMimeFromBytes(bytes)
            bytes to mime
        } catch (e: Exception) {
            Timber.w(e, "Failed to read OCR image bytes from URI: %s", uri)
            null
        }

        private fun guessMimeFromBytes(bytes: ByteArray): String {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            return opts.outMimeType ?: "image/jpeg"
        }
    }
