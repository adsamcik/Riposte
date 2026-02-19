package com.adsamcik.riposte.feature.settings.presentation.duplicatedetection

import app.cash.turbine.test
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.adsamcik.riposte.core.testing.MainDispatcherRule
import com.adsamcik.riposte.feature.settings.domain.model.DuplicateGroup
import com.adsamcik.riposte.feature.settings.domain.model.MergeResult
import com.adsamcik.riposte.feature.settings.domain.model.ScanProgress
import com.adsamcik.riposte.feature.settings.domain.repository.DuplicateDetectionRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DuplicateDetectionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var repository: DuplicateDetectionRepository
    private lateinit var viewModel: DuplicateDetectionViewModel

    private val duplicateGroupsFlow = MutableStateFlow<List<DuplicateGroup>>(emptyList())

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        every { repository.observeDuplicateGroups() } returns duplicateGroupsFlow
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createViewModel(): DuplicateDetectionViewModel {
        return DuplicateDetectionViewModel(repository = repository)
    }

    private fun createMemeEntity(
        id: Long = 1L,
        filePath: String = "/memes/meme_$id.jpg",
    ): MemeEntity {
        return MemeEntity(
            id = id,
            filePath = filePath,
            fileName = "meme_$id.jpg",
            mimeType = "image/jpeg",
            width = 1080,
            height = 1080,
            fileSizeBytes = 100_000L,
            importedAt = System.currentTimeMillis(),
            emojiTagsJson = "[]",
        )
    }

    private fun createDuplicateGroup(
        duplicateId: Long = 1L,
        meme1Id: Long = 1L,
        meme2Id: Long = 2L,
        hammingDistance: Int = 3,
        detectionMethod: String = "perceptual",
    ): DuplicateGroup {
        return DuplicateGroup(
            duplicateId = duplicateId,
            meme1 = createMemeEntity(id = meme1Id),
            meme2 = createMemeEntity(id = meme2Id),
            hammingDistance = hammingDistance,
            detectionMethod = detectionMethod,
        )
    }

    // region Initialization Tests

    @Test
    fun `initial state has default values`() = runTest {
        viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertThat(state.isScanning).isFalse()
        assertThat(state.scanProgress).isNull()
        assertThat(state.duplicateGroups).isEmpty()
        assertThat(state.sensitivity).isEqualTo(DuplicateDetectionUiState.DEFAULT_SENSITIVITY)
        assertThat(state.hasScanned).isFalse()
    }

    @Test
    fun `initial state observes duplicate groups from repository`() = runTest {
        val groups = listOf(
            createDuplicateGroup(duplicateId = 1L),
            createDuplicateGroup(duplicateId = 2L, meme1Id = 3L, meme2Id = 4L),
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        duplicateGroupsFlow.value = groups
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.duplicateGroups).isEqualTo(groups)
    }

    // endregion

    // region StartScan Tests

    @Test
    fun `StartScan sets isScanning to true`() = runTest {
        val scanFlow = MutableSharedFlow<ScanProgress>()
        every { repository.runDuplicateScan(any()) } returns scanFlow

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(DuplicateDetectionIntent.StartScan)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isScanning).isTrue()
    }

    @Test
    fun `StartScan collects progress updates`() = runTest {
        val scanFlow = MutableSharedFlow<ScanProgress>()
        every { repository.runDuplicateScan(any()) } returns scanFlow

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(DuplicateDetectionIntent.StartScan)
        advanceUntilIdle()

        val progress = ScanProgress(hashedCount = 5, totalToHash = 10, duplicatesFound = 1)
        scanFlow.emit(progress)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.scanProgress).isEqualTo(progress)
    }

    @Test
    fun `StartScan sets hasScanned when complete`() = runTest {
        val scanFlow = flow {
            emit(ScanProgress(hashedCount = 10, totalToHash = 10, duplicatesFound = 2, isComplete = true))
        }
        every { repository.runDuplicateScan(any()) } returns scanFlow

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(DuplicateDetectionIntent.StartScan)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasScanned).isTrue()
    }

    @Test
    fun `StartScan resets isScanning on completion`() = runTest {
        val scanFlow = flow {
            emit(ScanProgress(hashedCount = 10, totalToHash = 10, duplicatesFound = 2, isComplete = true))
        }
        every { repository.runDuplicateScan(any()) } returns scanFlow

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(DuplicateDetectionIntent.StartScan)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isScanning).isFalse()
    }

    @Test
    fun `StartScan uses current sensitivity as max hamming distance`() = runTest {
        val scanFlow = MutableSharedFlow<ScanProgress>()
        every { repository.runDuplicateScan(any()) } returns scanFlow

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(DuplicateDetectionIntent.SetSensitivity(15))
        advanceUntilIdle()

        viewModel.onIntent(DuplicateDetectionIntent.StartScan)
        advanceUntilIdle()

        io.mockk.verify { repository.runDuplicateScan(maxHammingDistance = 15) }
    }

    @Test
    fun `StartScan handles error with snackbar effect`() = runTest {
        val errorFlow = flow<ScanProgress> {
            throw RuntimeException("Scan error")
        }
        every { repository.runDuplicateScan(any()) } returns errorFlow

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(DuplicateDetectionIntent.StartScan)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(DuplicateDetectionEffect.ShowSnackbar::class.java)
            assertThat((effect as DuplicateDetectionEffect.ShowSnackbar).message).contains("Scan failed")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region SetSensitivity Tests

    @Test
    fun `SetSensitivity updates state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(DuplicateDetectionIntent.SetSensitivity(5))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.sensitivity).isEqualTo(5)
    }

    // endregion

    // region MergeDuplicate Tests

    @Test
    fun `MergeDuplicate calls repository and shows snackbar`() = runTest {
        coEvery { repository.mergeDuplicates(1L) } returns MergeResult(
            winnerId = 1L,
            loserId = 2L,
            loserFilePath = "/memes/meme_2.jpg",
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(DuplicateDetectionIntent.MergeDuplicate(1L))
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(DuplicateDetectionEffect.ShowSnackbar::class.java)
            assertThat((effect as DuplicateDetectionEffect.ShowSnackbar).message).contains("Merged")

            cancelAndIgnoreRemainingEvents()
        }

        coVerify { repository.mergeDuplicates(1L) }
    }

    @Test
    fun `MergeDuplicate shows error snackbar on failure`() = runTest {
        coEvery { repository.mergeDuplicates(1L) } throws RuntimeException("Merge error")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(DuplicateDetectionIntent.MergeDuplicate(1L))
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(DuplicateDetectionEffect.ShowSnackbar::class.java)
            assertThat((effect as DuplicateDetectionEffect.ShowSnackbar).message).contains("Merge failed")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region DismissDuplicate Tests

    @Test
    fun `DismissDuplicate calls repository and shows snackbar`() = runTest {
        coEvery { repository.dismissDuplicate(1L) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(DuplicateDetectionIntent.DismissDuplicate(1L))
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(DuplicateDetectionEffect.ShowSnackbar::class.java)
            assertThat((effect as DuplicateDetectionEffect.ShowSnackbar).message).contains("Dismissed")

            cancelAndIgnoreRemainingEvents()
        }

        coVerify { repository.dismissDuplicate(1L) }
    }

    @Test
    fun `DismissDuplicate shows error snackbar on failure`() = runTest {
        coEvery { repository.dismissDuplicate(1L) } throws RuntimeException("Dismiss error")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(DuplicateDetectionIntent.DismissDuplicate(1L))
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(DuplicateDetectionEffect.ShowSnackbar::class.java)
            assertThat((effect as DuplicateDetectionEffect.ShowSnackbar).message).contains("Dismiss failed")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region MergeAll Tests

    @Test
    fun `MergeAll calls repository and shows count in snackbar`() = runTest {
        val results = listOf(
            MergeResult(winnerId = 1L, loserId = 2L, loserFilePath = "/memes/meme_2.jpg"),
            MergeResult(winnerId = 3L, loserId = 4L, loserFilePath = "/memes/meme_4.jpg"),
            MergeResult(winnerId = 5L, loserId = 6L, loserFilePath = "/memes/meme_6.jpg"),
        )
        coEvery { repository.mergeAll() } returns results

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(DuplicateDetectionIntent.MergeAll)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(DuplicateDetectionEffect.ShowSnackbar::class.java)
            val message = (effect as DuplicateDetectionEffect.ShowSnackbar).message
            assertThat(message).contains("3")
            assertThat(message).contains("Merged")

            cancelAndIgnoreRemainingEvents()
        }

        coVerify { repository.mergeAll() }
    }

    @Test
    fun `MergeAll shows error snackbar on failure`() = runTest {
        coEvery { repository.mergeAll() } throws RuntimeException("Merge all error")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(DuplicateDetectionIntent.MergeAll)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(DuplicateDetectionEffect.ShowSnackbar::class.java)
            assertThat((effect as DuplicateDetectionEffect.ShowSnackbar).message).contains("Merge all failed")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region DismissAll Tests

    @Test
    fun `DismissAll calls repository and shows snackbar`() = runTest {
        coEvery { repository.dismissAll() } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(DuplicateDetectionIntent.DismissAll)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(DuplicateDetectionEffect.ShowSnackbar::class.java)
            assertThat((effect as DuplicateDetectionEffect.ShowSnackbar).message).contains("All duplicates dismissed")

            cancelAndIgnoreRemainingEvents()
        }

        coVerify { repository.dismissAll() }
    }

    @Test
    fun `DismissAll shows error snackbar on failure`() = runTest {
        coEvery { repository.dismissAll() } throws RuntimeException("Dismiss all error")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(DuplicateDetectionIntent.DismissAll)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(DuplicateDetectionEffect.ShowSnackbar::class.java)
            assertThat((effect as DuplicateDetectionEffect.ShowSnackbar).message).contains("Dismiss all failed")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region Observation Tests

    @Test
    fun `duplicate groups update when repository emits new values`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.duplicateGroups).isEmpty()

        val firstBatch = listOf(createDuplicateGroup(duplicateId = 1L))
        duplicateGroupsFlow.value = firstBatch
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.duplicateGroups).isEqualTo(firstBatch)

        val secondBatch = listOf(
            createDuplicateGroup(duplicateId = 1L),
            createDuplicateGroup(duplicateId = 2L, meme1Id = 3L, meme2Id = 4L),
        )
        duplicateGroupsFlow.value = secondBatch
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.duplicateGroups).isEqualTo(secondBatch)
        assertThat(viewModel.uiState.value.duplicateGroups).hasSize(2)
    }

    // endregion
}
