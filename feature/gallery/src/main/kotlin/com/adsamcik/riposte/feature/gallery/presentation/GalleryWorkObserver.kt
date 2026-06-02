package com.adsamcik.riposte.feature.gallery.presentation

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.adsamcik.riposte.core.common.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal class GalleryWorkObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<GalleryUiState>,
    private val onDismissNotification: () -> Unit,
) {
    private var importIdleJob: Job? = null
    private var importProgressShownAt = 0L
    private var embeddingIdleJob: Job? = null
    private var embeddingProgressShownAt = 0L

    fun observeImportWork() {
        scope.launch {
            try {
                val workManager = WorkManager.getInstance(context)
                workManager.getWorkInfosForUniqueWorkFlow(AppConstants.IMPORT_WORK_NAME)
                    .collectLatest { workInfos ->
                        workInfos.firstOrNull()?.let { workInfo ->
                            handleImportState(workManager, workInfo)
                        } ?: debounceIdle(WorkKind.IMPORT) {
                            uiState.update { it.copy(importStatus = ImportWorkStatus.Idle) }
                        }
                    }
            } catch (e: IllegalStateException) {
                Timber.d(e, "WorkManager not available, skipping import work observation")
            }
        }
    }

    fun observeEmbeddingWork() {
        scope.launch {
            try {
                val workManager = WorkManager.getInstance(context)
                workManager.getWorkInfosForUniqueWorkFlow(AppConstants.EMBEDDING_WORK_NAME)
                    .collectLatest { workInfos ->
                        workInfos.firstOrNull()?.let { workInfo ->
                            handleEmbeddingState(workManager, workInfo)
                        } ?: debounceIdle(WorkKind.EMBEDDING) {
                            uiState.update { it.copy(embeddingStatus = EmbeddingWorkStatus.Idle) }
                        }
                    }
            } catch (e: IllegalStateException) {
                Timber.d(e, "WorkManager not available, skipping embedding work observation")
            }
        }
    }

    private suspend fun handleImportState(workManager: WorkManager, workInfo: WorkInfo) {
        when (workInfo.state) {
            WorkInfo.State.RUNNING -> handleImportRunning(workInfo)
            WorkInfo.State.SUCCEEDED -> handleImportSucceeded(workManager, workInfo)
            WorkInfo.State.FAILED -> handleImportFailed(workManager)
            else -> debounceIdle(WorkKind.IMPORT) { uiState.update { it.copy(importStatus = ImportWorkStatus.Idle) } }
        }
    }

    private fun handleImportRunning(workInfo: WorkInfo) {
        importIdleJob?.cancel()
        val completed = workInfo.progress.getInt("completed", 0)
        val total = workInfo.progress.getInt("total", 0)
        if (uiState.value.importStatus is ImportWorkStatus.Idle) {
            importProgressShownAt = System.currentTimeMillis()
        }
        uiState.update {
            it.copy(importStatus = ImportWorkStatus.InProgress(completed.takeIf { total > 0 } ?: 0, total))
        }
    }

    private suspend fun handleImportSucceeded(workManager: WorkManager, workInfo: WorkInfo) {
        importIdleJob?.cancel()
        val completed = workInfo.outputData.getInt("completed", 0)
        val failed = workInfo.outputData.getInt("failed", 0)
        debounceIdle(WorkKind.IMPORT) {
            uiState.update {
                it.copy(
                    importStatus = ImportWorkStatus.Idle,
                    notification = GalleryNotification.ImportComplete(completed, failed),
                )
            }
            pruneAndDismiss(workManager)
        }
    }

    private suspend fun handleImportFailed(workManager: WorkManager) {
        importIdleJob?.cancel()
        debounceIdle(WorkKind.IMPORT) {
            uiState.update {
                it.copy(
                    importStatus = ImportWorkStatus.Idle,
                    notification = GalleryNotification.ImportFailed(),
                )
            }
            pruneAndDismiss(workManager)
        }
    }

    private suspend fun handleEmbeddingState(workManager: WorkManager, workInfo: WorkInfo) {
        when (workInfo.state) {
            WorkInfo.State.RUNNING -> handleEmbeddingRunning(workInfo)
            WorkInfo.State.ENQUEUED -> handleEmbeddingEnqueued()
            WorkInfo.State.SUCCEEDED -> handleEmbeddingSucceeded(workManager, workInfo)
            else -> debounceIdle(WorkKind.EMBEDDING) {
                uiState.update { it.copy(embeddingStatus = EmbeddingWorkStatus.Idle) }
            }
        }
    }

    private fun handleEmbeddingRunning(workInfo: WorkInfo) {
        embeddingIdleJob?.cancel()
        val processed = workInfo.progress.getInt("processed_count", 0)
        val remaining = workInfo.progress.getInt("remaining_count", 0)
        if (processed + remaining > 0) {
            markEmbeddingProgressShown()
            uiState.update {
                it.copy(embeddingStatus = EmbeddingWorkStatus.InProgress(processed, remaining))
            }
        }
    }

    private fun handleEmbeddingEnqueued() {
        embeddingIdleJob?.cancel()
        markEmbeddingProgressShown()
        uiState.update { it.copy(embeddingStatus = EmbeddingWorkStatus.InProgress(0, 0)) }
    }

    private suspend fun handleEmbeddingSucceeded(workManager: WorkManager, workInfo: WorkInfo) {
        embeddingIdleJob?.cancel()
        val processedCount = workInfo.outputData.getInt("processed_count", 0)
        debounceIdle(WorkKind.EMBEDDING) {
            uiState.update { it.copy(embeddingStatus = EmbeddingWorkStatus.Idle) }
            if (processedCount > 0) {
                uiState.update { it.copy(notification = GalleryNotification.IndexingComplete(processedCount)) }
                pruneAndDismiss(workManager)
            }
        }
    }

    private fun markEmbeddingProgressShown() {
        if (uiState.value.embeddingStatus is EmbeddingWorkStatus.Idle) {
            embeddingProgressShownAt = System.currentTimeMillis()
        }
    }

    private fun debounceIdle(kind: WorkKind, block: suspend () -> Unit) {
        val shownAt = when (kind) {
            WorkKind.IMPORT -> importProgressShownAt
            WorkKind.EMBEDDING -> embeddingProgressShownAt
        }
        val job = scope.launch {
            delay(remainingVisibleTime(shownAt))
            block()
        }
        when (kind) {
            WorkKind.IMPORT -> {
                importIdleJob?.cancel()
                importIdleJob = job
            }
            WorkKind.EMBEDDING -> {
                embeddingIdleJob?.cancel()
                embeddingIdleJob = job
            }
        }
    }

    private enum class WorkKind { IMPORT, EMBEDDING }

    private fun remainingVisibleTime(shownAt: Long): Long {
        val shownFor = System.currentTimeMillis() - shownAt
        return (PROGRESS_MIN_DISPLAY_MS - shownFor).coerceAtLeast(PROGRESS_IDLE_DEBOUNCE_MS)
    }

    private suspend fun pruneAndDismiss(workManager: WorkManager) {
        workManager.pruneWork()
        delay(NOTIFICATION_AUTO_DISMISS_MS)
        onDismissNotification()
    }

    private companion object {
        const val NOTIFICATION_AUTO_DISMISS_MS = 5000L
        const val PROGRESS_MIN_DISPLAY_MS = 2_000L
        const val PROGRESS_IDLE_DEBOUNCE_MS = 500L
    }
}
