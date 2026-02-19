package com.adsamcik.riposte.feature.settings.presentation.duplicatedetection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.riposte.feature.settings.domain.repository.DuplicateDetectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DuplicateDetectionViewModel @Inject constructor(
    private val repository: DuplicateDetectionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicateDetectionUiState())
    val uiState: StateFlow<DuplicateDetectionUiState> = _uiState.asStateFlow()

    private val _effects = Channel<DuplicateDetectionEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        observeDuplicateGroups()
    }

    fun onIntent(intent: DuplicateDetectionIntent) {
        when (intent) {
            is DuplicateDetectionIntent.StartScan -> startScan()
            is DuplicateDetectionIntent.SetSensitivity -> setSensitivity(intent.value)
            is DuplicateDetectionIntent.MergeDuplicate -> mergeDuplicate(intent.duplicateId)
            is DuplicateDetectionIntent.DismissDuplicate -> dismissDuplicate(intent.duplicateId)
            is DuplicateDetectionIntent.MergeAll -> mergeAll()
            is DuplicateDetectionIntent.DismissAll -> dismissAll()
        }
    }

    private fun observeDuplicateGroups() {
        viewModelScope.launch {
            repository.observeDuplicateGroups()
                .catch { /* Silently handle errors in observation */ }
                .collect { groups ->
                    _uiState.update { it.copy(duplicateGroups = groups) }
                }
        }
    }

    private fun startScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = null) }

            repository.runDuplicateScan(maxHammingDistance = _uiState.value.sensitivity)
                .catch { e ->
                    _uiState.update { it.copy(isScanning = false) }
                    _effects.send(DuplicateDetectionEffect.ShowSnackbar("Scan failed: ${e.message}"))
                }
                .collect { progress ->
                    _uiState.update { it.copy(scanProgress = progress) }
                    if (progress.isComplete) {
                        _uiState.update { it.copy(isScanning = false, hasScanned = true) }
                    }
                }
        }
    }

    private fun setSensitivity(value: Int) {
        _uiState.update { it.copy(sensitivity = value) }
    }

    private fun mergeDuplicate(duplicateId: Long) {
        viewModelScope.launch {
            try {
                repository.mergeDuplicates(duplicateId)
                _effects.send(DuplicateDetectionEffect.ShowSnackbar("Merged! Kept best quality, combined metadata"))
            } catch (e: Exception) {
                _effects.send(DuplicateDetectionEffect.ShowSnackbar("Merge failed: ${e.message}"))
            }
        }
    }

    private fun dismissDuplicate(duplicateId: Long) {
        viewModelScope.launch {
            try {
                repository.dismissDuplicate(duplicateId)
                _effects.send(DuplicateDetectionEffect.ShowSnackbar("Dismissed"))
            } catch (e: Exception) {
                _effects.send(DuplicateDetectionEffect.ShowSnackbar("Dismiss failed: ${e.message}"))
            }
        }
    }

    private fun mergeAll() {
        viewModelScope.launch {
            try {
                val results = repository.mergeAll()
                _effects.send(
                    DuplicateDetectionEffect.ShowSnackbar("Merged ${results.size} duplicate pairs"),
                )
            } catch (e: Exception) {
                _effects.send(DuplicateDetectionEffect.ShowSnackbar("Merge all failed: ${e.message}"))
            }
        }
    }

    private fun dismissAll() {
        viewModelScope.launch {
            try {
                repository.dismissAll()
                _effects.send(DuplicateDetectionEffect.ShowSnackbar("All duplicates dismissed"))
            } catch (e: Exception) {
                _effects.send(DuplicateDetectionEffect.ShowSnackbar("Dismiss all failed: ${e.message}"))
            }
        }
    }
}
