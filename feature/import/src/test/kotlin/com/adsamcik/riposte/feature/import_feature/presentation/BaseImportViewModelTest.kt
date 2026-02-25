package com.adsamcik.riposte.feature.import_feature.presentation

import android.content.Context
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.testing.MainDispatcherRule
import com.adsamcik.riposte.feature.import_feature.data.worker.ImportStagingManager
import com.adsamcik.riposte.feature.import_feature.domain.repository.ImportRepository
import com.adsamcik.riposte.feature.import_feature.domain.usecase.CheckDuplicateUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.CleanupExtractedFilesUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ExtractTextUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ExtractZipForPreviewUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.FindDuplicateMemeIdUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ImportImageUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ImportViewModelUseCases
import com.adsamcik.riposte.feature.import_feature.domain.usecase.SuggestEmojisUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.UpdateMemeMetadataUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
abstract class BaseImportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    protected lateinit var context: Context
    protected lateinit var importImageUseCase: ImportImageUseCase
    protected lateinit var suggestEmojisUseCase: SuggestEmojisUseCase
    protected lateinit var extractTextUseCase: ExtractTextUseCase
    protected lateinit var extractZipForPreviewUseCase: ExtractZipForPreviewUseCase
    protected lateinit var checkDuplicateUseCase: CheckDuplicateUseCase
    protected lateinit var findDuplicateMemeIdUseCase: FindDuplicateMemeIdUseCase
    protected lateinit var updateMemeMetadataUseCase: UpdateMemeMetadataUseCase
    protected lateinit var cleanupExtractedFilesUseCase: CleanupExtractedFilesUseCase
    protected lateinit var preferencesDataStore: PreferencesDataStore
    protected lateinit var importStagingManager: ImportStagingManager
    protected lateinit var importRepository: ImportRepository
    protected lateinit var viewModel: ImportViewModel

    @Before
    fun setup() {
        // Initialize WorkManager with real Robolectric context
        val realContext = RuntimeEnvironment.getApplication()
        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(realContext, config)

        // Use relaxed mock for ViewModel context (getString returns empty strings)
        context =
            mockk(relaxed = true) {
                // Delegate WorkManager-related calls to real context
                every { applicationContext } returns realContext
                every { packageName } returns realContext.packageName
            }

        importImageUseCase = mockk(relaxed = true)
        suggestEmojisUseCase = mockk(relaxed = true)
        extractTextUseCase = mockk(relaxed = true)
        extractZipForPreviewUseCase = mockk(relaxed = true)
        checkDuplicateUseCase = mockk(relaxed = true)
        findDuplicateMemeIdUseCase = mockk(relaxed = true)
        updateMemeMetadataUseCase = mockk(relaxed = true)
        cleanupExtractedFilesUseCase = mockk(relaxed = true)
        preferencesDataStore =
            mockk(relaxed = true) {
                every { hasShownEmojiTip } returns flowOf(false)
            }
        importStagingManager =
            mockk(relaxed = true) {
                coEvery { stageImages(any()) } returns java.io.File(System.getProperty("java.io.tmpdir"), "staging")
            }
        importRepository = mockk(relaxed = true)
        viewModel = createViewModel()
    }

    protected fun createViewModel(): ImportViewModel =
        ImportViewModel(
            context = context,
            useCases =
                ImportViewModelUseCases(
                    importImage = importImageUseCase,
                    suggestEmojis = suggestEmojisUseCase,
                    extractText = extractTextUseCase,
                    extractZipForPreview = extractZipForPreviewUseCase,
                    checkDuplicate = checkDuplicateUseCase,
                    findDuplicateMemeId = findDuplicateMemeIdUseCase,
                    updateMemeMetadata = updateMemeMetadataUseCase,
                    cleanupExtractedFiles = cleanupExtractedFilesUseCase,
                ),
            userActionTracker = mockk(relaxed = true),
            preferencesDataStore = preferencesDataStore,
            importStagingManager = importStagingManager,
            importRepository = importRepository,
        )
}
