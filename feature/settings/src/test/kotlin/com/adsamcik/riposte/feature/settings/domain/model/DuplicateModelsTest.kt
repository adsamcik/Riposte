package com.adsamcik.riposte.feature.settings.domain.model

import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DuplicateModelsTest {

    // region ScanProgress

    @Test
    fun `ScanProgress isComplete defaults to false`() {
        val progress = ScanProgress(hashedCount = 5, totalToHash = 10, duplicatesFound = 0)
        assertThat(progress.isComplete).isFalse()
    }

    @Test
    fun `ScanProgress can be created with isComplete true`() {
        val progress = ScanProgress(hashedCount = 10, totalToHash = 10, duplicatesFound = 3, isComplete = true)
        assertThat(progress.isComplete).isTrue()
        assertThat(progress.hashedCount).isEqualTo(10)
        assertThat(progress.duplicatesFound).isEqualTo(3)
    }

    @Test
    fun `ScanProgress stores all values correctly`() {
        val progress = ScanProgress(hashedCount = 42, totalToHash = 100, duplicatesFound = 7)
        assertThat(progress.hashedCount).isEqualTo(42)
        assertThat(progress.totalToHash).isEqualTo(100)
        assertThat(progress.duplicatesFound).isEqualTo(7)
    }

    @Test
    fun `ScanProgress equality works on all fields`() {
        val p1 = ScanProgress(1, 2, 3, false)
        val p2 = ScanProgress(1, 2, 3, false)
        val p3 = ScanProgress(1, 2, 3, true)

        assertThat(p1).isEqualTo(p2)
        assertThat(p1).isNotEqualTo(p3)
    }

    @Test
    fun `ScanProgress copy works correctly`() {
        val original = ScanProgress(5, 10, 2)
        val updated = original.copy(hashedCount = 8, duplicatesFound = 4)

        assertThat(updated.hashedCount).isEqualTo(8)
        assertThat(updated.totalToHash).isEqualTo(10) // unchanged
        assertThat(updated.duplicatesFound).isEqualTo(4)
        assertThat(updated.isComplete).isFalse() // unchanged default
    }

    @Test
    fun `ScanProgress with zero values`() {
        val progress = ScanProgress(0, 0, 0)
        assertThat(progress.hashedCount).isEqualTo(0)
        assertThat(progress.totalToHash).isEqualTo(0)
        assertThat(progress.duplicatesFound).isEqualTo(0)
    }

    // endregion

    // region MergeResult

    @Test
    fun `MergeResult stores all values correctly`() {
        val result = MergeResult(winnerId = 1, loserId = 2, loserFilePath = "/path/to/loser.jpg")
        assertThat(result.winnerId).isEqualTo(1)
        assertThat(result.loserId).isEqualTo(2)
        assertThat(result.loserFilePath).isEqualTo("/path/to/loser.jpg")
    }

    @Test
    fun `MergeResult equality works`() {
        val r1 = MergeResult(1, 2, "/a.jpg")
        val r2 = MergeResult(1, 2, "/a.jpg")
        val r3 = MergeResult(1, 3, "/a.jpg")

        assertThat(r1).isEqualTo(r2)
        assertThat(r1).isNotEqualTo(r3)
    }

    // endregion

    // region DuplicateGroup

    @Test
    fun `DuplicateGroup stores all values correctly`() {
        val meme1 = testMeme(id = 1)
        val meme2 = testMeme(id = 2)

        val group = DuplicateGroup(
            duplicateId = 42,
            meme1 = meme1,
            meme2 = meme2,
            hammingDistance = 5,
            detectionMethod = "perceptual",
        )

        assertThat(group.duplicateId).isEqualTo(42)
        assertThat(group.meme1.id).isEqualTo(1)
        assertThat(group.meme2.id).isEqualTo(2)
        assertThat(group.hammingDistance).isEqualTo(5)
        assertThat(group.detectionMethod).isEqualTo("perceptual")
    }

    @Test
    fun `DuplicateGroup with exact detection method`() {
        val group = DuplicateGroup(
            duplicateId = 1,
            meme1 = testMeme(id = 1),
            meme2 = testMeme(id = 2),
            hammingDistance = 0,
            detectionMethod = "exact",
        )

        assertThat(group.hammingDistance).isEqualTo(0)
        assertThat(group.detectionMethod).isEqualTo("exact")
    }

    @Test
    fun `DuplicateGroup equality depends on all fields`() {
        val meme1 = testMeme(id = 1)
        val meme2 = testMeme(id = 2)

        val g1 = DuplicateGroup(1, meme1, meme2, 5, "perceptual")
        val g2 = DuplicateGroup(1, meme1, meme2, 5, "perceptual")
        val g3 = DuplicateGroup(2, meme1, meme2, 5, "perceptual")

        assertThat(g1).isEqualTo(g2)
        assertThat(g1).isNotEqualTo(g3)
    }

    // endregion

    // region Helpers

    private fun testMeme(id: Long = 1) = MemeEntity(
        id = id,
        filePath = "/path/$id.jpg",
        fileName = "meme$id.jpg",
        mimeType = "image/jpeg",
        width = 100,
        height = 100,
        fileSizeBytes = 1000,
        importedAt = System.currentTimeMillis(),
        emojiTagsJson = "[]",
    )

    // endregion
}
