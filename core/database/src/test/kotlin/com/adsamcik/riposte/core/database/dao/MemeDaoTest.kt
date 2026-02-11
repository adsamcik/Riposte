package com.adsamcik.riposte.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.adsamcik.riposte.core.database.MemeDatabase
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
class MemeDaoTest {
    private lateinit var database: MemeDatabase
    private lateinit var memeDao: MemeDao

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
        mimeType: String = "image/png",
        width: Int = 1024,
        height: Int = 768,
        fileSizeBytes: Long = 102400,
        importedAt: Long = System.currentTimeMillis(),
        emojiTagsJson: String = "[]",
        title: String? = null,
        description: String? = null,
        textContent: String? = null,
        embedding: ByteArray? = null,
        isFavorite: Boolean = false,
    ) = MemeEntity(
        id = id,
        filePath = filePath,
        fileName = fileName,
        mimeType = mimeType,
        width = width,
        height = height,
        fileSizeBytes = fileSizeBytes,
        importedAt = importedAt,
        emojiTagsJson = emojiTagsJson,
        title = title,
        description = description,
        textContent = textContent,
        embedding = embedding,
        isFavorite = isFavorite,
    )

    // endregion

    // region Insert Tests

    @Test
    fun `insertMeme returns generated id`() =
        runTest {
            val meme = createMeme(filePath = "/storage/test/meme1.png")

            val id = memeDao.insertMeme(meme)

            assertThat(id).isGreaterThan(0)
        }

    @Test
    fun `insertMeme with replace strategy updates existing meme`() =
        runTest {
            val meme = createMeme(filePath = "/storage/test/meme1.png", title = "Original")
            val id = memeDao.insertMeme(meme)

            val updatedMeme = meme.copy(id = id, title = "Updated")
            memeDao.insertMeme(updatedMeme)

            val retrieved = memeDao.getMemeById(id)
            assertThat(retrieved?.title).isEqualTo("Updated")
        }

    @Test
    fun `insertMemes inserts multiple memes and returns ids`() =
        runTest {
            val memes =
                listOf(
                    createMeme(filePath = "/storage/test/meme1.png", fileName = "meme1.png"),
                    createMeme(filePath = "/storage/test/meme2.png", fileName = "meme2.png"),
                    createMeme(filePath = "/storage/test/meme3.png", fileName = "meme3.png"),
                )

            val ids = memeDao.insertMemes(memes)

            assertThat(ids).hasSize(3)
            ids.forEach { id -> assertThat(id).isGreaterThan(0) }
        }

    // endregion

    // region Query Tests

    @Test
    fun `getMemeById returns correct meme`() =
        runTest {
            val meme =
                createMeme(
                    filePath = "/storage/test/findme.png",
                    title = "Find Me",
                )
            val id = memeDao.insertMeme(meme)

            val result = memeDao.getMemeById(id)

            assertThat(result).isNotNull()
            assertThat(result?.title).isEqualTo("Find Me")
            assertThat(result?.filePath).isEqualTo("/storage/test/findme.png")
        }

    @Test
    fun `getMemeById returns null for non-existent id`() =
        runTest {
            val result = memeDao.getMemeById(999)

            assertThat(result).isNull()
        }

    @Test
    fun `getAllMemes returns memes ordered by importedAt descending`() =
        runTest {
            val now = System.currentTimeMillis()
            val memes =
                listOf(
                    createMeme(filePath = "/storage/oldest.png", importedAt = now - 2000),
                    createMeme(filePath = "/storage/newest.png", importedAt = now),
                    createMeme(filePath = "/storage/middle.png", importedAt = now - 1000),
                )
            memeDao.insertMemes(memes)

            memeDao.getAllMemes().test {
                val result = awaitItem()
                assertThat(result).hasSize(3)
                assertThat(result[0].filePath).isEqualTo("/storage/newest.png")
                assertThat(result[1].filePath).isEqualTo("/storage/middle.png")
                assertThat(result[2].filePath).isEqualTo("/storage/oldest.png")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getAllMemes emits empty list when database is empty`() =
        runTest {
            memeDao.getAllMemes().test {
                val result = awaitItem()
                assertThat(result).isEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getFavoriteMemes returns only favorites ordered by importedAt`() =
        runTest {
            val now = System.currentTimeMillis()
            memeDao.insertMemes(
                listOf(
                    createMeme(
                        filePath = "/storage/fav1.png",
                        importedAt = now - 1000,
                        isFavorite = true,
                    ),
                    createMeme(
                        filePath = "/storage/notfav.png",
                        importedAt = now,
                        isFavorite = false,
                    ),
                    createMeme(
                        filePath = "/storage/fav2.png",
                        importedAt = now - 500,
                        isFavorite = true,
                    ),
                ),
            )

            memeDao.getFavoriteMemes().test {
                val result = awaitItem()
                assertThat(result).hasSize(2)
                assertThat(result[0].filePath).isEqualTo("/storage/fav2.png")
                assertThat(result[1].filePath).isEqualTo("/storage/fav1.png")
                result.forEach { assertThat(it.isFavorite).isTrue() }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeMemeById emits updates when meme changes`() =
        runTest {
            val meme = createMeme(filePath = "/storage/observable.png", title = "Original")
            val id = memeDao.insertMeme(meme)

            memeDao.observeMemeById(id).test {
                assertThat(awaitItem()?.title).isEqualTo("Original")

                memeDao.updateMeme(meme.copy(id = id, title = "Updated"))
                assertThat(awaitItem()?.title).isEqualTo("Updated")

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeMemeById emits null for non-existent meme`() =
        runTest {
            memeDao.observeMemeById(999).test {
                assertThat(awaitItem()).isNull()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getMemeCount returns correct count`() =
        runTest {
            assertThat(memeDao.getMemeCount()).isEqualTo(0)

            memeDao.insertMemes(
                listOf(
                    createMeme(filePath = "/storage/1.png"),
                    createMeme(filePath = "/storage/2.png"),
                    createMeme(filePath = "/storage/3.png"),
                ),
            )

            assertThat(memeDao.getMemeCount()).isEqualTo(3)
        }

    @Test
    fun `memeExistsByPath returns true for existing path`() =
        runTest {
            memeDao.insertMeme(createMeme(filePath = "/storage/exists.png"))

            val exists = memeDao.memeExistsByPath("/storage/exists.png")

            assertThat(exists).isTrue()
        }

    @Test
    fun `memeExistsByPath returns false for non-existent path`() =
        runTest {
            val exists = memeDao.memeExistsByPath("/storage/nonexistent.png")

            assertThat(exists).isFalse()
        }

    // endregion

    // region Update Tests

    @Test
    fun `updateMeme updates all fields`() =
        runTest {
            val original =
                createMeme(
                    filePath = "/storage/original.png",
                    title = "Original",
                    description = "Original Description",
                )
            val id = memeDao.insertMeme(original)

            val updated =
                original.copy(
                    id = id,
                    title = "Updated Title",
                    description = "Updated Description",
                    textContent = "OCR Text",
                )
            memeDao.updateMeme(updated)

            val result = memeDao.getMemeById(id)
            assertThat(result?.title).isEqualTo("Updated Title")
            assertThat(result?.description).isEqualTo("Updated Description")
            assertThat(result?.textContent).isEqualTo("OCR Text")
        }

    @Test
    fun `toggleFavorite toggles favorite status from false to true`() =
        runTest {
            val meme = createMeme(filePath = "/storage/toggle.png", isFavorite = false)
            val id = memeDao.insertMeme(meme)

            memeDao.toggleFavorite(id)

            val result = memeDao.getMemeById(id)
            assertThat(result?.isFavorite).isTrue()
        }

    @Test
    fun `toggleFavorite toggles favorite status from true to false`() =
        runTest {
            val meme = createMeme(filePath = "/storage/toggle.png", isFavorite = true)
            val id = memeDao.insertMeme(meme)

            memeDao.toggleFavorite(id)

            val result = memeDao.getMemeById(id)
            assertThat(result?.isFavorite).isFalse()
        }

    @Test
    fun `setFavorite sets favorite to true`() =
        runTest {
            val meme = createMeme(filePath = "/storage/setfav.png", isFavorite = false)
            val id = memeDao.insertMeme(meme)

            memeDao.setFavorite(id, true)

            val result = memeDao.getMemeById(id)
            assertThat(result?.isFavorite).isTrue()
        }

    @Test
    fun `setFavorite sets favorite to false`() =
        runTest {
            val meme = createMeme(filePath = "/storage/setfav.png", isFavorite = true)
            val id = memeDao.insertMeme(meme)

            memeDao.setFavorite(id, false)

            val result = memeDao.getMemeById(id)
            assertThat(result?.isFavorite).isFalse()
        }

    @Test
    fun `updateEmbedding updates embedding for meme`() =
        runTest {
            val meme = createMeme(filePath = "/storage/embed.png", embedding = null)
            val id = memeDao.insertMeme(meme)

            val embedding = byteArrayOf(1, 2, 3, 4, 5)
            memeDao.updateEmbedding(id, embedding)

            val result = memeDao.getMemeById(id)
            assertThat(result?.embedding).isEqualTo(embedding)
        }

    @Test
    fun `getMemesWithoutEmbeddings returns memes with null embeddings`() =
        runTest {
            memeDao.insertMemes(
                listOf(
                    createMeme(filePath = "/storage/noEmbed1.png", embedding = null),
                    createMeme(filePath = "/storage/hasEmbed.png", embedding = byteArrayOf(1, 2, 3)),
                    createMeme(filePath = "/storage/noEmbed2.png", embedding = null),
                ),
            )

            val result = memeDao.getMemesWithoutEmbeddings(10)

            assertThat(result).hasSize(2)
            result.forEach { assertThat(it.embedding).isNull() }
        }

    @Test
    fun `getMemesWithoutEmbeddings respects limit`() =
        runTest {
            memeDao.insertMemes(
                (1..10).map { i ->
                    createMeme(filePath = "/storage/meme$i.png", embedding = null)
                },
            )

            val result = memeDao.getMemesWithoutEmbeddings(3)

            assertThat(result).hasSize(3)
        }

    // endregion

    // region Delete Tests

    @Test
    fun `deleteMeme removes meme from database`() =
        runTest {
            val meme = createMeme(filePath = "/storage/delete.png")
            val id = memeDao.insertMeme(meme)

            val insertedMeme = memeDao.getMemeById(id)!!
            memeDao.deleteMeme(insertedMeme)

            val result = memeDao.getMemeById(id)
            assertThat(result).isNull()
        }

    @Test
    fun `deleteMemeById removes meme by id`() =
        runTest {
            val meme = createMeme(filePath = "/storage/deletebyid.png")
            val id = memeDao.insertMeme(meme)

            memeDao.deleteMemeById(id)

            val result = memeDao.getMemeById(id)
            assertThat(result).isNull()
        }

    @Test
    fun `deleteMemesByIds removes multiple memes`() =
        runTest {
            val ids =
                memeDao.insertMemes(
                    listOf(
                        createMeme(filePath = "/storage/del1.png"),
                        createMeme(filePath = "/storage/del2.png"),
                        createMeme(filePath = "/storage/keep.png"),
                    ),
                )

            memeDao.deleteMemesByIds(listOf(ids[0], ids[1]))

            assertThat(memeDao.getMemeById(ids[0])).isNull()
            assertThat(memeDao.getMemeById(ids[1])).isNull()
            assertThat(memeDao.getMemeById(ids[2])).isNotNull()
        }

    @Test
    fun `deleteMemesByIds with empty list does nothing`() =
        runTest {
            val meme = createMeme(filePath = "/storage/keep.png")
            val id = memeDao.insertMeme(meme)

            memeDao.deleteMemesByIds(emptyList())

            assertThat(memeDao.getMemeById(id)).isNotNull()
        }

    // endregion

    // region Flow Emission Tests

    @Test
    fun `getAllMemes emits new value when meme is inserted`() =
        runTest {
            memeDao.getAllMemes().test {
                assertThat(awaitItem()).isEmpty()

                memeDao.insertMeme(createMeme(filePath = "/storage/new.png"))
                assertThat(awaitItem()).hasSize(1)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getAllMemes emits new value when meme is deleted`() =
        runTest {
            val id = memeDao.insertMeme(createMeme(filePath = "/storage/tobedeleted.png"))

            memeDao.getAllMemes().test {
                assertThat(awaitItem()).hasSize(1)

                memeDao.deleteMemeById(id)
                assertThat(awaitItem()).isEmpty()

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getFavoriteMemes emits when favorite status changes`() =
        runTest {
            val meme = createMeme(filePath = "/storage/fav.png", isFavorite = false)
            val id = memeDao.insertMeme(meme)

            memeDao.getFavoriteMemes().test {
                assertThat(awaitItem()).isEmpty()

                memeDao.setFavorite(id, true)
                assertThat(awaitItem()).hasSize(1)

                memeDao.setFavorite(id, false)
                assertThat(awaitItem()).isEmpty()

                cancelAndIgnoreRemainingEvents()
            }
        }

    // endregion
}
