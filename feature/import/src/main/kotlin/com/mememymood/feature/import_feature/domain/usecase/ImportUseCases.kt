package com.mememymood.feature.import_feature.domain.usecase

import android.net.Uri
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.Meme
import com.mememymood.core.model.MemeMetadata
import com.mememymood.feature.import_feature.domain.repository.ImportRepository
import javax.inject.Inject

/**
 * Use case for importing a single image.
 */
class ImportImageUseCase @Inject constructor(
    private val repository: ImportRepository
) {
    suspend operator fun invoke(uri: Uri, metadata: MemeMetadata? = null): Result<Meme> {
        return repository.importImage(uri, metadata)
    }
}

/**
 * Use case for importing multiple images.
 */
class ImportImagesUseCase @Inject constructor(
    private val repository: ImportRepository
) {
    suspend operator fun invoke(uris: List<Uri>): List<Result<Meme>> {
        return repository.importImages(uris)
    }
}

/**
 * Use case for extracting metadata from an image.
 */
class ExtractMetadataUseCase @Inject constructor(
    private val repository: ImportRepository
) {
    suspend operator fun invoke(uri: Uri): MemeMetadata? {
        return repository.extractMetadata(uri)
    }
}

/**
 * Use case for suggesting emojis for an image.
 */
class SuggestEmojisUseCase @Inject constructor(
    private val repository: ImportRepository
) {
    suspend operator fun invoke(uri: Uri): List<EmojiTag> {
        return repository.suggestEmojis(uri)
    }
}

/**
 * Use case for extracting text from an image.
 */
class ExtractTextUseCase @Inject constructor(
    private val repository: ImportRepository
) {
    suspend operator fun invoke(uri: Uri): String? {
        return repository.extractText(uri)
    }
}
