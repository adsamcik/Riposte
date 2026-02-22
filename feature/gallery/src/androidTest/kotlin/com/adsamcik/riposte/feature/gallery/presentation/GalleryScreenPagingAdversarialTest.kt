package com.adsamcik.riposte.feature.gallery.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Adversarial Compose UI tests for GalleryScreen paging path.
 *
 * These tests verify that duplicate meme IDs in PagingData do NOT cause
 * `IllegalArgumentException: Key "X" was already used` crashes at composition time.
 * This is the ONLY test layer that can catch key collisions — unit tests cannot
 * detect them because PagingData is opaque and key collisions are a Compose runtime error.
 *
 * Regression tests for: production crashes on Google Play (shipped 3 times).
 */
@RunWith(AndroidJUnit4::class)
class GalleryScreenPagingAdversarialTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // region Test data

    private fun createTestMeme(
        id: Long,
        fileName: String = "meme$id.jpg",
        isFavorite: Boolean = false,
    ) = Meme(
        id = id,
        filePath = "/test/$fileName",
        fileName = fileName,
        mimeType = "image/jpeg",
        width = 500,
        height = 500,
        fileSizeBytes = 50000,
        importedAt = System.currentTimeMillis(),
        emojiTags = listOf(EmojiTag("😂", "laughing")),
        title = "Test Meme $id",
        isFavorite = isFavorite,
    )

    // endregion

    @Test
    fun `paged items with duplicate IDs do not crash composition`() {
        // Simulates DAO JOIN expansion: same meme appears multiple times
        // (e.g., meme has both 'content' and 'intent' embeddings)
        val duplicateMemes = listOf(
            createTestMeme(1L),
            createTestMeme(2L),
            createTestMeme(2L), // duplicate
            createTestMeme(3L),
            createTestMeme(3L), // duplicate
            createTestMeme(3L), // triple
        )
        val pagingFlow = MutableStateFlow(PagingData.from(duplicateMemes))

        composeTestRule.setContent {
            val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()
            RiposteTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        isLoading = false,
                        usePaging = true,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                    pagedMemes = lazyPagingItems,
                )
            }
        }

        // If seenPagedIds dedup fails, this will throw:
        // IllegalArgumentException: Key "paged_2" was already used
        composeTestRule.waitForIdle()
    }

    @Test
    fun `paged items with suggestion overlap do not produce duplicate keys`() {
        // Suggestions are shown separately, so paged items must skip them.
        // If both lists contain the same meme ID, keys would collide without dedup.
        val suggestion = createTestMeme(1L, isFavorite = true)
        val pagedMemes = listOf(
            createTestMeme(1L), // same ID as suggestion
            createTestMeme(2L),
            createTestMeme(3L),
        )
        val pagingFlow = MutableStateFlow(PagingData.from(pagedMemes))

        composeTestRule.setContent {
            val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()
            RiposteTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        isLoading = false,
                        usePaging = true,
                        suggestions = listOf(suggestion),
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                    pagedMemes = lazyPagingItems,
                )
            }
        }

        // suggestion_1 and paged_1 use different prefixes, so no collision.
        // But meme ID=1 should be skipped from paged list (suggestionIds filter).
        composeTestRule.waitForIdle()
    }

    @Test
    fun `empty paging data does not crash`() {
        val pagingFlow = MutableStateFlow(PagingData.from(emptyList<Meme>()))

        composeTestRule.setContent {
            val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()
            RiposteTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        isLoading = false,
                        usePaging = true,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                    pagedMemes = lazyPagingItems,
                )
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun `paged items with all same ID do not crash`() {
        // Extreme case: all items have the same ID (worst-case JOIN duplication)
        val allSameId = List(5) { createTestMeme(42L) }
        val pagingFlow = MutableStateFlow(PagingData.from(allSameId))

        composeTestRule.setContent {
            val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()
            RiposteTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        isLoading = false,
                        usePaging = true,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                    pagedMemes = lazyPagingItems,
                )
            }
        }

        // seenPagedIds should keep only the first occurrence
        composeTestRule.waitForIdle()
    }

    @Test
    fun `mixed suggestions and paged duplicates do not crash`() {
        // Real-world scenario: suggestions contain IDs that also appear
        // multiple times in the paged data
        val suggestions = listOf(
            createTestMeme(1L, isFavorite = true),
            createTestMeme(2L, isFavorite = true),
        )
        val pagedMemes = listOf(
            createTestMeme(1L), // overlap with suggestion
            createTestMeme(1L), // duplicate + overlap
            createTestMeme(2L), // overlap with suggestion
            createTestMeme(3L),
            createTestMeme(3L), // duplicate
            createTestMeme(4L),
        )
        val pagingFlow = MutableStateFlow(PagingData.from(pagedMemes))

        composeTestRule.setContent {
            val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()
            RiposteTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        isLoading = false,
                        usePaging = true,
                        suggestions = suggestions,
                    ),
                    onIntent = {},
                    onNavigateToMeme = {},
                    onNavigateToImport = {},
                    onNavigateToSettings = {},
                    pagedMemes = lazyPagingItems,
                )
            }
        }

        composeTestRule.waitForIdle()
    }
}
