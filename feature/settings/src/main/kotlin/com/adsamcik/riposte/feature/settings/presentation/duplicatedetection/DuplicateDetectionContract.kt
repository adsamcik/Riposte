package com.adsamcik.riposte.feature.settings.presentation.duplicatedetection

import com.adsamcik.riposte.feature.settings.domain.model.DuplicateGroup
import com.adsamcik.riposte.feature.settings.domain.model.ScanProgress

/**
 * UI state for the duplicate detection screen.
 */
data class DuplicateDetectionUiState(
    val isScanning: Boolean = false,
    val scanProgress: ScanProgress? = null,
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val sensitivity: Int = DEFAULT_SENSITIVITY,
    val hasScanned: Boolean = false,
) {
    companion object {
        const val DEFAULT_SENSITIVITY = 10
        const val MIN_SENSITIVITY = 0
        const val MAX_SENSITIVITY = 20
    }
}

/**
 * User actions on the duplicate detection screen.
 */
sealed interface DuplicateDetectionIntent {
    data object StartScan : DuplicateDetectionIntent
    data class SetSensitivity(val value: Int) : DuplicateDetectionIntent
    data class MergeDuplicate(val duplicateId: Long) : DuplicateDetectionIntent
    data class DismissDuplicate(val duplicateId: Long) : DuplicateDetectionIntent
    data object MergeAll : DuplicateDetectionIntent
    data object DismissAll : DuplicateDetectionIntent
}

/**
 * One-time side effects for the duplicate detection screen.
 */
sealed interface DuplicateDetectionEffect {
    data class ShowSnackbar(val message: String) : DuplicateDetectionEffect
}
