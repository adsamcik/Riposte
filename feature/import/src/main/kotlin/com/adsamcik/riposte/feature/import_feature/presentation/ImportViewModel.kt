package com.adsamcik.riposte.feature.import_feature.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.adsamcik.riposte.core.common.AppConstants
import com.adsamcik.riposte.core.common.review.UserActionTracker
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.feature.import_feature.R
import com.adsamcik.riposte.feature.import_feature.data.worker.ImportStagingManager
import com.adsamcik.riposte.feature.import_feature.data.worker.ImportWorker
import com.adsamcik.riposte.feature.import_feature.domain.model.ImportRequestItemData
import com.adsamcik.riposte.feature.import_feature.domain.repository.ImportRepository
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ImportViewModelUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
class ImportViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val useCases: ImportViewModelUseCases,
        private val userActionTracker: UserActionTracker,
        private val preferencesDataStore: PreferencesDataStore,
        private val importStagingManager: ImportStagingManager,
        private val importRepository: ImportRepository,
    ) : ViewModel() {
        companion object {
            private const val MAX_APPLIED_EMOJIS = 5
        }

        private val _uiState = MutableStateFlow(ImportUiState())
        val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

        private val _effects = Channel<ImportEffect>(Channel.BUFFERED)
        val effects = _effects.receiveAsFlow()

        private var importJob: Job? = null
        private val duplicateCoordinator = ImportDuplicateCoordinator(
            context = context,
            useCases = useCases,
            uiState = _uiState,
            effects = _effects,
            performImport = ::performImport,
        )

        init {
            observeImportWork()
        }

        /** Observes active import work via WorkManager for progress and completion. */
        private fun observeImportWork() {
            viewModelScope.launch {
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWorkFlow(AppConstants.IMPORT_WORK_NAME)
                    .collect { workInfos ->
                        val workInfo = workInfos.firstOrNull() ?: return@collect
                        handleWorkInfoUpdate(workInfo)
                    }
            }
        }

        private suspend fun handleWorkInfoUpdate(workInfo: WorkInfo) {
            when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    val completed = workInfo.progress.getInt(ImportWorker.KEY_COMPLETED, 0)
                    val total = workInfo.progress.getInt(ImportWorker.KEY_TOTAL, 0)
                    if (total > 0) {
                        _uiState.update {
                            it.copy(
                                isImporting = true,
                                importProgress = completed.toFloat() / total,
                                totalImportCount = total,
                                statusMessage = null,
                            )
                        }
                    }
                }

                WorkInfo.State.SUCCEEDED -> if (_uiState.value.isImporting) {
                    val completed = workInfo.outputData.getInt(ImportWorker.KEY_COMPLETED, 0)
                    val failed = workInfo.outputData.getInt(ImportWorker.KEY_FAILED, 0)
                    handleImportComplete(completed, failed)
                }

                WorkInfo.State.FAILED -> if (_uiState.value.isImporting) {
                    val totalCount = _uiState.value.totalImportCount
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            statusMessage = null,
                            importResult =
                                ImportResult(
                                    successCount = 0,
                                    failureCount = totalCount,
                                ),
                        )
                    }
                    _effects.send(
                        ImportEffect.ShowError(
                            context.getString(R.string.import_error_images_failed),
                        ),
                    )
                }

                WorkInfo.State.CANCELLED -> if (_uiState.value.isImporting) {
                    _uiState.update {
                        it.copy(isImporting = false, importProgress = 0f, statusMessage = null)
                    }
                }

                else -> { /* ENQUEUED, BLOCKED — no UI update needed */ }
            }
        }

        private suspend fun handleImportComplete(
            successCount: Int,
            failedCount: Int,
        ) {
            Timber.i("Import completed: %d succeeded, %d failed", successCount, failedCount)
            useCases.cleanupExtractedFiles()

            _uiState.update {
                it.copy(
                    isImporting = false,
                    statusMessage = null,
                    importResult =
                        ImportResult(
                            successCount = successCount,
                            failureCount = failedCount,
                        ),
                )
            }

            if (successCount > 0) {
                userActionTracker.trackPositiveAction()
                _effects.send(ImportEffect.ImportComplete(successCount))
                if (!preferencesDataStore.hasShownEmojiTip.first()) {
                    preferencesDataStore.setEmojiTipShown()
                    _effects.send(
                        ImportEffect.ShowSnackbar(
                            context.getString(R.string.import_tip_emoji_tagging),
                        ),
                    )
                }
            } else {
                _effects.send(
                    ImportEffect.ShowError(
                        context.getString(R.string.import_error_images_failed),
                    ),
                )
            }
        }

        override fun onCleared() {
            super.onCleared()
            useCases.cleanupExtractedFiles()
        }

        fun onIntent(intent: ImportIntent) {
            when (intent) {
                is ImportIntent.ImagesSelected -> handleImagesSelected(intent)
                is ImportIntent.ZipSelected -> handleZipSelected(intent)
                is ImportIntent.RemoveImage -> removeImage(intent.index)
                is ImportIntent.EditImage -> editImage(intent.index)
                is ImportIntent.CloseEditor -> closeEditor()
                is ImportIntent.UpdateTitle -> updateTitle(intent.title)
                is ImportIntent.UpdateDescription -> updateDescription(intent.description)
                is ImportIntent.AddEmoji -> addEmoji(intent.emoji)
                is ImportIntent.RemoveEmoji -> removeEmoji(intent.emoji)
                is ImportIntent.ShowEmojiPicker -> showEmojiPicker()
                is ImportIntent.HideEmojiPicker -> hideEmojiPicker()
                is ImportIntent.ApplySuggestedEmojis -> applySuggestedEmojis()
                is ImportIntent.StartImport -> startImport()
                is ImportIntent.CancelImport -> cancelImport()
                is ImportIntent.ClearAll -> clearAll()
                is ImportIntent.PickMoreImages -> pickMoreImages()
                is ImportIntent.PickZipBundle -> pickZipBundle()
                is ImportIntent.ImportDuplicatesAnyway -> importDuplicatesAnyway()
                is ImportIntent.SkipDuplicates -> skipDuplicates()
                is ImportIntent.UpdateDuplicateMetadata -> updateDuplicateMetadata()
                is ImportIntent.DismissDuplicateDialog -> dismissDuplicateDialog()
                is ImportIntent.RetryFailedImports -> retryFailedImports()
                is ImportIntent.DismissImportResult -> dismissImportResult()
            }
        }

        private fun handleImagesSelected(intent: ImportIntent.ImagesSelected) {
            viewModelScope.launch {
                val newImages =
                    intent.uris.map { uri ->
                        val fileName = uri.lastPathSegment ?: "image_${System.currentTimeMillis()}"
                        ImportImage(
                            uri = uri,
                            fileName = fileName,
                            isProcessing = true,
                        )
                    }

                _uiState.update { state ->
                    state.copy(selectedImages = state.selectedImages + newImages)
                }

                // Process each image for suggestions
                newImages.forEachIndexed { index, image ->
                    val actualIndex = _uiState.value.selectedImages.size - newImages.size + index
                    processImage(actualIndex, image)
                }
            }
        }

        private fun handleZipSelected(intent: ImportIntent.ZipSelected) {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isImporting = true,
                        importProgress = -1f,
                        statusMessage = context.getString(R.string.import_status_extracting),
                    )
                }

                val result = useCases.extractZipForPreview(intent.uri)

                if (result.extractedMemes.isEmpty()) {
                    _uiState.update { it.copy(isImporting = false, statusMessage = null) }
                    val errorMsg =
                        result.errors.values.firstOrNull()
                            ?: context.getString(R.string.import_error_bundle_failed)
                    _effects.send(ImportEffect.ShowError(errorMsg))
                    return@launch
                }

                // Convert extracted memes to ImportImage for preview
                val newImages =
                    result.extractedMemes.map { extracted ->
                        val emojis =
                            extracted.metadata?.emojis?.map {
                                com.adsamcik.riposte.core.model.EmojiTag.fromEmoji(it)
                            } ?: emptyList()
                        ImportImage(
                            uri = extracted.imageUri,
                            fileName = extracted.imageUri.lastPathSegment ?: "image",
                            emojis = emojis,
                            title = extracted.metadata?.title,
                            description = extracted.metadata?.description,
                            extractedText = extracted.metadata?.textContent,
                            searchPhrases = extracted.metadata?.searchPhrases ?: emptyList(),
                            basedOn = extracted.metadata?.basedOn,
                            primaryLanguage = extracted.metadata?.primaryLanguage,
                            localizations = extracted.metadata?.localizations ?: emptyMap(),
                        )
                    }

                _uiState.update { state ->
                    state.copy(
                        isImporting = false,
                        statusMessage = null,
                        selectedImages = state.selectedImages + newImages,
                    )
                }

                if (result.errors.isNotEmpty()) {
                    _effects.send(
                        ImportEffect.ShowSnackbar(
                            context.getString(
                                R.string.import_status_zip_extract_errors,
                                result.errors.size,
                            ),
                        ),
                    )
                }
            }
        }

        private suspend fun processImage(
            index: Int,
            image: ImportImage,
        ) {
            try {
                // Suggest emojis
                val suggestedEmojis = useCases.suggestEmojis(image.uri)

                // Extract text
                val extractedText = useCases.extractText(image.uri)

                _uiState.update { state ->
                    val updatedImages = state.selectedImages.toMutableList()
                    if (index < updatedImages.size) {
                        updatedImages[index] =
                            updatedImages[index].copy(
                                suggestedEmojis = suggestedEmojis,
                                extractedText = extractedText,
                                isProcessing = false,
                            )
                    }
                    state.copy(selectedImages = updatedImages)
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") // Worker must not crash - reports failure instead
                e: Exception,
            ) {
                Timber.w(e, "Failed to process image at index %d", index)
                _uiState.update { state ->
                    val updatedImages = state.selectedImages.toMutableList()
                    if (index < updatedImages.size) {
                        updatedImages[index] =
                            updatedImages[index].copy(
                                isProcessing = false,
                                error = e.message,
                            )
                    }
                    state.copy(selectedImages = updatedImages)
                }
            }
        }

        private fun removeImage(index: Int) {
            _uiState.update { state ->
                val updatedImages = state.selectedImages.toMutableList()
                if (index < updatedImages.size) {
                    updatedImages.removeAt(index)
                }
                state.copy(
                    selectedImages = updatedImages,
                    editingImageIndex = if (state.editingImageIndex == index) null else state.editingImageIndex,
                )
            }
        }

        private fun editImage(index: Int) {
            _uiState.update { it.copy(editingImageIndex = index) }
        }

        private fun closeEditor() {
            _uiState.update { it.copy(editingImageIndex = null, showEmojiPicker = false) }
        }

        private fun updateTitle(title: String) {
            updateEditingImage { it.copy(title = title) }
        }

        private fun updateDescription(description: String) {
            updateEditingImage { it.copy(description = description) }
        }

        private fun addEmoji(emoji: com.adsamcik.riposte.core.model.EmojiTag) {
            updateEditingImage { image ->
                if (emoji !in image.emojis) {
                    image.copy(emojis = image.emojis + emoji)
                } else {
                    image
                }
            }
        }

        private fun removeEmoji(emoji: com.adsamcik.riposte.core.model.EmojiTag) {
            updateEditingImage { image ->
                image.copy(emojis = image.emojis - emoji)
            }
        }

        private fun showEmojiPicker() {
            _uiState.update { it.copy(showEmojiPicker = true) }
        }

        private fun hideEmojiPicker() {
            _uiState.update { it.copy(showEmojiPicker = false) }
        }

        private fun applySuggestedEmojis() {
            updateEditingImage { image ->
                image.copy(emojis = image.suggestedEmojis.take(MAX_APPLIED_EMOJIS))
            }
        }

        private fun updateEditingImage(transform: (ImportImage) -> ImportImage) {
            _uiState.update { state ->
                val index = state.editingImageIndex ?: return@update state
                val updatedImages = state.selectedImages.toMutableList()
                if (index < updatedImages.size) {
                    updatedImages[index] = transform(updatedImages[index])
                }
                state.copy(selectedImages = updatedImages)
            }
        }

        private fun startImport() {
            importJob = duplicateCoordinator.startImport(viewModelScope)
        }

        private fun importDuplicatesAnyway() {
            importJob = duplicateCoordinator.importDuplicatesAnyway(viewModelScope)
        }

        private fun skipDuplicates() {
            importJob = duplicateCoordinator.skipDuplicates(viewModelScope)
        }

        private fun updateDuplicateMetadata() {
            importJob = duplicateCoordinator.updateDuplicateMetadata(viewModelScope)
        }

        private fun dismissDuplicateDialog() {
            duplicateCoordinator.dismissDuplicateDialog()
        }

        private suspend fun performImport(images: List<ImportImage>) {
            showImportStagingStarted(images.size)
            try {
                val stagedImport = stageImportRequest(images)
                importRepository.createImportRequest(
                    stagedImport.requestId,
                    images.size,
                    stagedImport.stagingDir.absolutePath,
                )
                importRepository.createImportRequestItems(stagedImport.requestId, stagedImport.items)
                ImportWorker.enqueue(context, stagedImport.requestId)
                finishImportStaging()
            } catch (
                @Suppress("TooGenericExceptionCaught") // Worker must not crash - reports failure instead
                e: Exception,
            ) {
                handleImportFailure(e, images.size)
            }
        }

        private fun showImportStagingStarted(imageCount: Int) {
            _uiState.update {
                it.copy(
                    isImporting = true,
                    importProgress = 0f,
                    totalImportCount = imageCount,
                    statusMessage = context.getString(R.string.import_status_staging),
                )
            }
        }

        private suspend fun stageImportRequest(images: List<ImportImage>): StagedImportRequest {
            val requestId = UUID.randomUUID().toString()
            val stagingInputs = buildStagingInputs(requestId, images)
            val stagingDir = importStagingManager.stageImages(stagingInputs) { completed, total ->
                _uiState.update {
                    it.copy(
                        importProgress = completed.toFloat() / total,
                        statusMessage = context.getString(
                            R.string.import_status_staging_progress,
                            completed,
                            total,
                        ),
                    )
                }
            }
            val items = images.mapIndexed { index, image ->
                ImportRequestItemBuilder.build(requestId, index, image, stagingDir)
            }
            return StagedImportRequest(requestId, stagingDir, items)
        }

        private fun buildStagingInputs(
            requestId: String,
            images: List<ImportImage>,
        ): List<ImportStagingManager.StagingInput> =
            images.mapIndexed { index, image ->
                ImportStagingManager.StagingInput(
                    id = ImportRequestItemBuilder.buildStagingFileId(requestId, index, image.fileName),
                    uri = image.uri,
                )
            }

        private suspend fun finishImportStaging() {
            _uiState.update {
                it.copy(
                    isImporting = false,
                    importProgress = 0f,
                    totalImportCount = it.selectedImages.size,
                    statusMessage = null,
                    selectedImages = emptyList(),
                )
            }
            _effects.send(ImportEffect.NavigateToGallery)
        }

        private suspend fun handleImportFailure(error: Exception, imageCount: Int) {
            Timber.e(error, "Import failed for %d images", imageCount)
            _uiState.update { it.copy(isImporting = false, statusMessage = null) }
            _effects.send(
                ImportEffect.ShowError(
                    error.message ?: context.getString(R.string.import_error_images_failed),
                ),
            )
        }

        private data class StagedImportRequest(
            val requestId: String,
            val stagingDir: java.io.File,
            val items: List<ImportRequestItemData>,
        )

        private fun cancelImport() {
            importJob?.cancel()
            importJob = null
            WorkManager.getInstance(context).cancelUniqueWork(AppConstants.IMPORT_WORK_NAME)
            _uiState.update { it.copy(isImporting = false, importProgress = 0f, statusMessage = null) }
        }

        private fun clearAll() {
            useCases.cleanupExtractedFiles()
            _uiState.update { ImportUiState() }
        }

        private fun pickMoreImages() {
            viewModelScope.launch {
                _effects.send(ImportEffect.OpenImagePicker)
            }
        }

        private fun pickZipBundle() {
            viewModelScope.launch {
                _effects.send(ImportEffect.OpenFilePicker)
            }
        }

        private fun retryFailedImports() {
            val failedImages = _uiState.value.importResult?.failedImages ?: return
            _uiState.update {
                it.copy(
                    selectedImages = failedImages,
                    importResult = null,
                )
            }
        }

        private fun dismissImportResult() {
            _uiState.update { it.copy(importResult = null) }
            viewModelScope.launch {
                _effects.send(ImportEffect.NavigateToGallery)
            }
        }
    }
