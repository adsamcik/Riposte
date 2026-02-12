package com.adsamcik.riposte.feature.gallery.domain.usecase

import javax.inject.Inject

/**
 * Bundle of use cases consumed by
 * [MemeDetailViewModel][com.adsamcik.riposte.feature.gallery.presentation.MemeDetailViewModel].
 */
data class MemeDetailUseCases
    @Inject
    constructor(
        val getMemeById: GetMemeByIdUseCase,
        val updateMeme: UpdateMemeUseCase,
        val deleteMemes: DeleteMemesUseCase,
        val toggleFavorite: ToggleFavoriteUseCase,
        val recordMemeView: RecordMemeViewUseCase,
        val getSimilarMemes: GetSimilarMemesUseCase,
        val getAllMemeIds: GetAllMemeIdsUseCase,
    )
