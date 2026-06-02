package com.adsamcik.riposte.feature.import_feature.presentation

import android.content.Context
import com.adsamcik.riposte.core.model.MemeMetadata
import com.adsamcik.riposte.feature.import_feature.R
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ImportViewModelUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal class ImportDuplicateCoordinator(
    private val context: Context,
    private val useCases: ImportViewModelUseCases,
    private val uiState: MutableStateFlow<ImportUiState>,
    private val effects: Channel<ImportEffect>,
    private val performImport: suspend (List<ImportImage>) -> Unit,
) {
    fun startImport(scope: CoroutineScope): Job? {
        var wasAlreadyImporting = false
        uiState.update { state ->
            if (state.isImporting) {
                wasAlreadyImporting = true
                state
            } else {
                state.copy(
                    isImporting = true,
                    importProgress = -1f,
                    statusMessage = context.getString(R.string.import_status_checking_duplicates),
                )
            }
        }
        return if (wasAlreadyImporting) {
            null
        } else {
            scope.launch { checkDuplicatesAndImport(uiState.value.selectedImages) }
        }
    }

    fun importDuplicatesAnyway(scope: CoroutineScope): Job {
        uiState.update {
            it.copy(
                showDuplicateDialog = false,
                duplicateIndices = emptySet(),
                duplicatesWithChangedMetadata = emptySet(),
                duplicateMemeIds = emptyMap(),
            )
        }
        return scope.launch { performImport(uiState.value.selectedImages) }
    }

    fun skipDuplicates(scope: CoroutineScope): Job? {
        val dupes = uiState.value.duplicateIndices
        uiState.update { state ->
            state.copy(
                selectedImages = state.selectedImages.filterIndexed { index, _ -> index !in dupes },
                showDuplicateDialog = false,
                duplicateIndices = emptySet(),
                duplicatesWithChangedMetadata = emptySet(),
                duplicateMemeIds = emptyMap(),
            )
        }
        val images = uiState.value.selectedImages
        return images.takeIf { it.isNotEmpty() }?.let { scope.launch { performImport(it) } }
    }

    fun updateDuplicateMetadata(scope: CoroutineScope): Job =
        scope.launch {
            val snapshot = uiState.value
            showMetadataUpdateInProgress()
            val updatedCount = updateChangedDuplicates(snapshot)
            notifyMetadataUpdate(updatedCount)
            removeDuplicateImages(snapshot.duplicateIndices)

            val remainingImages = uiState.value.selectedImages
            if (remainingImages.isNotEmpty()) {
                performImport(remainingImages)
            } else {
                effects.send(ImportEffect.NavigateToGallery)
            }
        }

    fun dismissDuplicateDialog() {
        uiState.update { it.copy(showDuplicateDialog = false) }
    }

    private suspend fun checkDuplicatesAndImport(images: List<ImportImage>) {
        val duplicates = findDuplicates(images)
        if (duplicates.indices.isNotEmpty()) {
            Timber.d("Found %d duplicate images during import", duplicates.indices.size)
            showDuplicateDialog(duplicates)
        } else {
            performImport(images)
        }
    }

    private suspend fun findDuplicates(images: List<ImportImage>): DuplicateInfo {
        val duplicateIndices = mutableSetOf<Int>()
        val duplicateMemeIds = mutableMapOf<Int, Long>()
        val duplicatesWithChangedMetadata = mutableSetOf<Int>()

        images.forEachIndexed { index, image ->
            try {
                val existingMemeId = useCases.findDuplicateMemeId(image.uri)
                if (existingMemeId != null) {
                    duplicateIndices.add(index)
                    duplicateMemeIds[index] = existingMemeId
                    if (image.hasIncomingMetadata()) {
                        duplicatesWithChangedMetadata.add(index)
                    }
                }
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Timber.d(e, "Failed to check duplicate metadata, proceeding with import")
            }
        }

        return DuplicateInfo(duplicateIndices, duplicateMemeIds, duplicatesWithChangedMetadata)
    }

    private fun ImportImage.hasIncomingMetadata(): Boolean =
        emojis.isNotEmpty() || title != null || description != null

    private fun showDuplicateDialog(duplicates: DuplicateInfo) {
        uiState.update {
            it.copy(
                isImporting = false,
                statusMessage = null,
                duplicateIndices = duplicates.indices,
                duplicateMemeIds = duplicates.memeIds,
                duplicatesWithChangedMetadata = duplicates.changedMetadataIndices,
                showDuplicateDialog = true,
            )
        }
    }

    private fun showMetadataUpdateInProgress() {
        uiState.update {
            it.copy(
                showDuplicateDialog = false,
                isImporting = true,
                importProgress = -1f,
                statusMessage = context.getString(R.string.import_status_updating_metadata),
            )
        }
    }

    private suspend fun updateChangedDuplicates(state: ImportUiState): Int {
        var updatedCount = 0
        for (index in state.duplicatesWithChangedMetadata) {
            val image = state.selectedImages.getOrNull(index)
            val memeId = state.duplicateMemeIds[index]
            if (image != null && memeId != null) {
                val result = useCases.updateMemeMetadata(memeId, image.toMetadata())
                if (result.isSuccess) updatedCount++
            }
        }
        return updatedCount
    }

    private fun ImportImage.toMetadata(): MemeMetadata =
        MemeMetadata(
            emojis = emojis.map { it.emoji }.ifEmpty { listOf("😀") },
            title = title,
            description = description,
            textContent = extractedText,
            searchPhrases = searchPhrases,
            basedOn = basedOn,
            primaryLanguage = primaryLanguage,
            localizations = localizations,
        )

    private suspend fun notifyMetadataUpdate(updatedCount: Int) {
        if (updatedCount > 0) {
            effects.send(
                ImportEffect.ShowSnackbar(
                    context.getString(R.string.import_metadata_updated_count, updatedCount),
                ),
            )
        }
    }

    private fun removeDuplicateImages(dupes: Set<Int>) {
        uiState.update { state ->
            state.copy(
                selectedImages = state.selectedImages.filterIndexed { index, _ -> index !in dupes },
                duplicateIndices = emptySet(),
                duplicatesWithChangedMetadata = emptySet(),
                duplicateMemeIds = emptyMap(),
                isImporting = false,
                statusMessage = null,
            )
        }
    }

    private data class DuplicateInfo(
        val indices: Set<Int>,
        val memeIds: Map<Int, Long>,
        val changedMetadataIndices: Set<Int>,
    )
}
