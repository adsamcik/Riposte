package com.mememymood.feature.gallery.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.Meme
import com.mememymood.core.ui.theme.MemeMoodTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemeGridItemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testMeme = Meme(
        id = 1L,
        filePath = "/storage/emulated/0/Memes/test.jpg",
        fileName = "test.jpg",
        mimeType = "image/jpeg",
        width = 500,
        height = 500,
        fileSizeBytes = 50_000L,
        importedAt = System.currentTimeMillis(),
        emojiTags = listOf(EmojiTag(emoji = "😂", name = "joy")),
        title = "Test meme",
    )

    @Test
    fun `tap on grid item opens meme and does not share`() {
        val receivedIntents = mutableListOf<GalleryIntent>()

        composeRule.setContent {
            MemeMoodTheme(dynamicColor = false) {
                Box(modifier = Modifier.size(200.dp)) {
                    MemeGridItem(
                        meme = testMeme,
                        isSelected = false,
                        isSelectionMode = false,
                        memeDescription = "Test meme",
                        onIntent = { receivedIntents.add(it) },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Test meme").performClick()

        assertThat(receivedIntents).hasSize(1)
        assertThat(receivedIntents.first()).isEqualTo(GalleryIntent.OpenMeme(1L))
    }

    @Test
    fun `long press directly triggers quick share`() {
        val receivedIntents = mutableListOf<GalleryIntent>()

        composeRule.setContent {
            MemeMoodTheme(dynamicColor = false) {
                Box(modifier = Modifier.size(200.dp)) {
                    MemeGridItem(
                        meme = testMeme,
                        isSelected = false,
                        isSelectionMode = false,
                        memeDescription = "Test meme",
                        onIntent = { receivedIntents.add(it) },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Test meme").performTouchInput {
            longClick()
        }

        composeRule.waitForIdle()

        assertThat(receivedIntents).contains(GalleryIntent.QuickShare(1L))
        assertThat(receivedIntents).doesNotContain(GalleryIntent.OpenMeme(1L))
    }

    @Test
    fun `in selection mode tap toggles selection instead of opening meme`() {
        val receivedIntents = mutableListOf<GalleryIntent>()

        composeRule.setContent {
            MemeMoodTheme(dynamicColor = false) {
                Box(modifier = Modifier.size(200.dp)) {
                    MemeGridItem(
                        meme = testMeme,
                        isSelected = false,
                        isSelectionMode = true,
                        memeDescription = "Test meme",
                        onIntent = { receivedIntents.add(it) },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Test meme").performClick()

        assertThat(receivedIntents).contains(GalleryIntent.ToggleSelection(1L))
        assertThat(receivedIntents).doesNotContain(GalleryIntent.OpenMeme(1L))
    }
}
