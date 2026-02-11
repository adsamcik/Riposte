package com.adsamcik.riposte.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.adsamcik.riposte.core.database.MemeDatabase
import com.adsamcik.riposte.core.database.entity.EmojiTagEntity
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EmojiTagDaoTest {
    private lateinit var database: MemeDatabase
    private lateinit var memeDao: MemeDao
    private lateinit var emojiTagDao: EmojiTagDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(
                context,
                MemeDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()
        memeDao = database.memeDao()
        emojiTagDao = database.emojiTagDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // region Test Data Helpers

    private fun createMeme(
        id: Long = 0,
        filePath: String = "/storage/memes/meme.png",
        fileName: String = "meme.png",
    ) = MemeEntity(
        id = id,
        filePath = filePath,
        fileName = fileName,
        mimeType = "image/png",
        width = 1024,
        height = 768,
        fileSizeBytes = 102400,
        importedAt = System.currentTimeMillis(),
        emojiTagsJson = "[]",
    )

    private fun createEmojiTag(
        memeId: Long,
        emoji: String,
        emojiName: String,
    ) = EmojiTagEntity(
        memeId = memeId,
        emoji = emoji,
        emojiName = emojiName,
    )

    // endregion

    // region Insert Tests

    @Test
    fun `insertEmojiTags inserts tags for a meme`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            val tags =
                listOf(
                    createEmojiTag(memeId, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(memeId, "🔥", "fire"),
                    createEmojiTag(memeId, "💯", "hundred_points"),
                )

            emojiTagDao.insertEmojiTags(tags)

            val result = emojiTagDao.getEmojiTagsForMeme(memeId)
            assertThat(result).hasSize(3)
            assertThat(result.map { it.emoji }).containsExactly("😂", "🔥", "💯")
        }

    @Test
    fun `insertEmojiTags with replace strategy updates existing tags`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            val originalTag = createEmojiTag(memeId, "😂", "face_with_tears_of_joy")
            emojiTagDao.insertEmojiTags(listOf(originalTag))

            val updatedTag = createEmojiTag(memeId, "😂", "updated_name")
            emojiTagDao.insertEmojiTags(listOf(updatedTag))

            val result = emojiTagDao.getEmojiTagsForMeme(memeId)
            assertThat(result).hasSize(1)
            assertThat(result[0].emojiName).isEqualTo("updated_name")
        }

    @Test
    fun `insertEmojiTags with empty list does nothing`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))

            emojiTagDao.insertEmojiTags(emptyList())

            val result = emojiTagDao.getEmojiTagsForMeme(memeId)
            assertThat(result).isEmpty()
        }

    // endregion

    // region Query Tests

    @Test
    fun `getEmojiTagsForMeme returns all tags for a meme`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            val tags =
                listOf(
                    createEmojiTag(memeId, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(memeId, "🔥", "fire"),
                )
            emojiTagDao.insertEmojiTags(tags)

            val result = emojiTagDao.getEmojiTagsForMeme(memeId)

            assertThat(result).hasSize(2)
            assertThat(result.map { it.emoji }).containsExactly("😂", "🔥")
        }

    @Test
    fun `getEmojiTagsForMeme returns empty list for meme without tags`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))

            val result = emojiTagDao.getEmojiTagsForMeme(memeId)

            assertThat(result).isEmpty()
        }

    @Test
    fun `getEmojiTagsForMeme returns empty list for non-existent meme`() =
        runTest {
            val result = emojiTagDao.getEmojiTagsForMeme(999)

            assertThat(result).isEmpty()
        }

    @Test
    fun `observeEmojiTagsForMeme emits updates when tags change`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))

            emojiTagDao.observeEmojiTagsForMeme(memeId).test {
                assertThat(awaitItem()).isEmpty()

                emojiTagDao.insertEmojiTags(
                    listOf(createEmojiTag(memeId, "😂", "face_with_tears_of_joy")),
                )
                val updated = awaitItem()
                assertThat(updated).hasSize(1)
                assertThat(updated[0].emoji).isEqualTo("😂")

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getMemeIdsWithEmoji returns meme ids with specific emoji`() =
        runTest {
            val meme1Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            val meme2Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme2.png"))
            val meme3Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme3.png"))

            emojiTagDao.insertEmojiTags(
                listOf(
                    createEmojiTag(meme1Id, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(meme1Id, "🔥", "fire"),
                    createEmojiTag(meme2Id, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(meme3Id, "🔥", "fire"),
                ),
            )

            val result = emojiTagDao.getMemeIdsWithEmoji("😂")

            assertThat(result).containsExactly(meme1Id, meme2Id)
        }

    @Test
    fun `getMemeIdsWithEmoji returns empty list when no memes have emoji`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            emojiTagDao.insertEmojiTags(
                listOf(createEmojiTag(memeId, "😂", "face_with_tears_of_joy")),
            )

            val result = emojiTagDao.getMemeIdsWithEmoji("🎉")

            assertThat(result).isEmpty()
        }

    @Test
    fun `getMemeIdsWithAnyEmoji returns meme ids with any of the emojis`() =
        runTest {
            val meme1Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            val meme2Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme2.png"))
            val meme3Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme3.png"))
            val meme4Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme4.png"))

            emojiTagDao.insertEmojiTags(
                listOf(
                    createEmojiTag(meme1Id, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(meme2Id, "🔥", "fire"),
                    createEmojiTag(meme3Id, "💯", "hundred_points"),
                    createEmojiTag(meme4Id, "🎉", "party_popper"),
                ),
            )

            val result = emojiTagDao.getMemeIdsWithAnyEmoji(listOf("😂", "🔥"))

            assertThat(result).containsExactly(meme1Id, meme2Id)
        }

    @Test
    fun `getMemeIdsWithAnyEmoji returns distinct meme ids`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            emojiTagDao.insertEmojiTags(
                listOf(
                    createEmojiTag(memeId, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(memeId, "🔥", "fire"),
                ),
            )

            val result = emojiTagDao.getMemeIdsWithAnyEmoji(listOf("😂", "🔥"))

            assertThat(result).containsExactly(memeId)
        }

    @Test
    fun `getMemeIdsWithAllEmojis returns meme ids with all emojis`() =
        runTest {
            val meme1Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            val meme2Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme2.png"))
            val meme3Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme3.png"))

            emojiTagDao.insertEmojiTags(
                listOf(
                    createEmojiTag(meme1Id, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(meme1Id, "🔥", "fire"),
                    createEmojiTag(meme1Id, "💯", "hundred_points"),
                    createEmojiTag(meme2Id, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(meme2Id, "🔥", "fire"),
                    createEmojiTag(meme3Id, "😂", "face_with_tears_of_joy"),
                ),
            )

            val emojisToSearch = listOf("😂", "🔥")
            val result = emojiTagDao.getMemeIdsWithAllEmojis(emojisToSearch, emojisToSearch.size)

            assertThat(result).containsExactly(meme1Id, meme2Id)
        }

    @Test
    fun `getMemeIdsWithAllEmojis returns empty when no memes have all emojis`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            emojiTagDao.insertEmojiTags(
                listOf(createEmojiTag(memeId, "😂", "face_with_tears_of_joy")),
            )

            val emojisToSearch = listOf("😂", "🔥", "💯")
            val result = emojiTagDao.getMemeIdsWithAllEmojis(emojisToSearch, emojisToSearch.size)

            assertThat(result).isEmpty()
        }

    @Test
    fun `getAllEmojisWithCounts returns emoji usage statistics`() =
        runTest {
            val meme1Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            val meme2Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme2.png"))
            val meme3Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme3.png"))

            emojiTagDao.insertEmojiTags(
                listOf(
                    createEmojiTag(meme1Id, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(meme2Id, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(meme3Id, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(meme1Id, "🔥", "fire"),
                    createEmojiTag(meme2Id, "🔥", "fire"),
                    createEmojiTag(meme1Id, "💯", "hundred_points"),
                ),
            )

            emojiTagDao.getAllEmojisWithCounts().test {
                val result = awaitItem()

                assertThat(result).hasSize(3)
                // Ordered by count DESC
                assertThat(result[0].emoji).isEqualTo("😂")
                assertThat(result[0].count).isEqualTo(3)
                assertThat(result[1].emoji).isEqualTo("🔥")
                assertThat(result[1].count).isEqualTo(2)
                assertThat(result[2].emoji).isEqualTo("💯")
                assertThat(result[2].count).isEqualTo(1)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getAllEmojisWithCounts returns empty when no tags exist`() =
        runTest {
            emojiTagDao.getAllEmojisWithCounts().test {
                val result = awaitItem()
                assertThat(result).isEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // endregion

    // region Delete Tests

    @Test
    fun `deleteEmojiTagsForMeme removes all tags for a meme`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            emojiTagDao.insertEmojiTags(
                listOf(
                    createEmojiTag(memeId, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(memeId, "🔥", "fire"),
                ),
            )

            emojiTagDao.deleteEmojiTagsForMeme(memeId)

            val result = emojiTagDao.getEmojiTagsForMeme(memeId)
            assertThat(result).isEmpty()
        }

    @Test
    fun `deleteEmojiTagsForMeme does not affect other memes`() =
        runTest {
            val meme1Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            val meme2Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme2.png"))

            emojiTagDao.insertEmojiTags(
                listOf(
                    createEmojiTag(meme1Id, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(meme2Id, "🔥", "fire"),
                ),
            )

            emojiTagDao.deleteEmojiTagsForMeme(meme1Id)

            assertThat(emojiTagDao.getEmojiTagsForMeme(meme1Id)).isEmpty()
            assertThat(emojiTagDao.getEmojiTagsForMeme(meme2Id)).hasSize(1)
        }

    @Test
    fun `deleteEmojiTagsForMeme with non-existent meme does nothing`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            emojiTagDao.insertEmojiTags(
                listOf(createEmojiTag(memeId, "😂", "face_with_tears_of_joy")),
            )

            emojiTagDao.deleteEmojiTagsForMeme(999)

            assertThat(emojiTagDao.getEmojiTagsForMeme(memeId)).hasSize(1)
        }

    // endregion

    // region Cascade Delete Tests

    @Test
    fun `emoji tags are deleted when parent meme is deleted`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            emojiTagDao.insertEmojiTags(
                listOf(
                    createEmojiTag(memeId, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(memeId, "🔥", "fire"),
                ),
            )

            memeDao.deleteMemeById(memeId)

            val result = emojiTagDao.getEmojiTagsForMeme(memeId)
            assertThat(result).isEmpty()
        }

    @Test
    fun `cascade delete does not affect other memes tags`() =
        runTest {
            val meme1Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            val meme2Id = memeDao.insertMeme(createMeme(filePath = "/storage/meme2.png"))

            emojiTagDao.insertEmojiTags(
                listOf(
                    createEmojiTag(meme1Id, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(meme2Id, "🔥", "fire"),
                ),
            )

            memeDao.deleteMemeById(meme1Id)

            assertThat(emojiTagDao.getEmojiTagsForMeme(meme1Id)).isEmpty()
            assertThat(emojiTagDao.getEmojiTagsForMeme(meme2Id)).hasSize(1)
        }

    // endregion

    // region Flow Emission Tests

    @Test
    fun `observeEmojiTagsForMeme emits when tags are inserted`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))

            emojiTagDao.observeEmojiTagsForMeme(memeId).test {
                assertThat(awaitItem()).isEmpty()

                emojiTagDao.insertEmojiTags(
                    listOf(createEmojiTag(memeId, "😂", "face_with_tears_of_joy")),
                )
                assertThat(awaitItem()).hasSize(1)

                emojiTagDao.insertEmojiTags(
                    listOf(createEmojiTag(memeId, "🔥", "fire")),
                )
                assertThat(awaitItem()).hasSize(2)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeEmojiTagsForMeme emits when tags are deleted`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))
            emojiTagDao.insertEmojiTags(
                listOf(
                    createEmojiTag(memeId, "😂", "face_with_tears_of_joy"),
                    createEmojiTag(memeId, "🔥", "fire"),
                ),
            )

            emojiTagDao.observeEmojiTagsForMeme(memeId).test {
                assertThat(awaitItem()).hasSize(2)

                emojiTagDao.deleteEmojiTagsForMeme(memeId)
                assertThat(awaitItem()).isEmpty()

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getAllEmojisWithCounts emits when tags change`() =
        runTest {
            val memeId = memeDao.insertMeme(createMeme(filePath = "/storage/meme1.png"))

            emojiTagDao.getAllEmojisWithCounts().test {
                assertThat(awaitItem()).isEmpty()

                emojiTagDao.insertEmojiTags(
                    listOf(createEmojiTag(memeId, "😂", "face_with_tears_of_joy")),
                )
                val afterInsert = awaitItem()
                assertThat(afterInsert).hasSize(1)
                assertThat(afterInsert[0].count).isEqualTo(1)

                emojiTagDao.deleteEmojiTagsForMeme(memeId)
                assertThat(awaitItem()).isEmpty()

                cancelAndIgnoreRemainingEvents()
            }
        }

    // endregion
}
