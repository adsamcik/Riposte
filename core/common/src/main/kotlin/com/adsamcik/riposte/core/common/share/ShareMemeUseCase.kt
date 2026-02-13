package com.adsamcik.riposte.core.common.share

import android.content.Intent
import javax.inject.Inject

/**
 * Orchestrates the full single-meme share flow:
 * load meme → get config → process image → create share intent.
 *
 * This is the single entry point for sharing a meme from any screen.
 */
class ShareMemeUseCase
    @Inject
    constructor(
        private val repository: ShareRepository,
    ) {
        /**
         * Prepare and return a share intent for a single meme.
         *
         * @param memeId The ID of the meme to share.
         * @return A ready-to-launch chooser Intent, or failure if meme not found or processing fails.
         */
        suspend operator fun invoke(memeId: Long): Result<Intent> {
            val meme =
                repository.getMeme(memeId)
                    ?: return Result.failure(IllegalArgumentException("Meme not found: $memeId"))

            val config = repository.getDefaultShareConfig()

            val uriResult = repository.prepareForSharing(meme, config)
            val uri = uriResult.getOrElse { return Result.failure(it) }

            val mimeType = config.format.mimeType
            val intent = repository.createShareIntent(uri, mimeType)

            return Result.success(intent)
        }
    }
