package com.adsamcik.riposte.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.adsamcik.riposte.core.database.MemeDatabase
import com.adsamcik.riposte.core.database.entity.MemeEmbeddingEntity
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemeEmbeddingDaoTest {
    private lateinit var database: MemeDatabase
    private lateinit var memeDao: MemeDao
    private lateinit var embeddingDao: MemeEmbeddingDao

    @Before
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                MemeDatabase::class.java,
            ).allowMainThreadQueries().build()

        memeDao = database.memeDao()
        embeddingDao = database.memeEmbeddingDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insertEmbedding and getEmbeddingByMemeId work correctly`() =
        runTest {
            // Given
            val memeId = insertTestMeme()
            val embedding = createTestEmbedding(memeId)

            // When
            embeddingDao.insertEmbedding(embedding)
            val retrieved = embeddingDao.getEmbeddingByMemeId(memeId)

            // Then
            assertThat(retrieved).isNotNull()
            assertThat(retrieved!!.memeId).isEqualTo(memeId)
            assertThat(retrieved.dimension).isEqualTo(128)
            assertThat(retrieved.modelVersion).isEqualTo("test:1.0.0")
        }

    @Test
    fun `insertEmbedding replaces existing embedding for same meme`() =
        runTest {
            // Given
            val memeId = insertTestMeme()
            val embedding1 = createTestEmbedding(memeId, modelVersion = "v1:1.0.0")
            val embedding2 = createTestEmbedding(memeId, modelVersion = "v2:1.0.0")

            // When
            embeddingDao.insertEmbedding(embedding1)
            embeddingDao.insertEmbedding(embedding2)
            val retrieved = embeddingDao.getEmbeddingByMemeId(memeId)

            // Then
            assertThat(retrieved!!.modelVersion).isEqualTo("v2:1.0.0")
        }

    @Test
    fun `getAllValidEmbeddings excludes those needing regeneration`() =
        runTest {
            // Given
            val meme1Id = insertTestMeme("meme1")
            val meme2Id = insertTestMeme("meme2")

            embeddingDao.insertEmbedding(createTestEmbedding(meme1Id, needsRegeneration = false))
            embeddingDao.insertEmbedding(createTestEmbedding(meme2Id, needsRegeneration = true))

            // When
            val validEmbeddings = embeddingDao.getAllValidEmbeddings()

            // Then
            assertThat(validEmbeddings).hasSize(1)
            assertThat(validEmbeddings[0].memeId).isEqualTo(meme1Id)
        }

    @Test
    fun `getMemeIdsWithoutEmbeddings returns correct IDs`() =
        runTest {
            // Given
            val meme1Id = insertTestMeme("meme1")
            val meme2Id = insertTestMeme("meme2")
            val meme3Id = insertTestMeme("meme3")

            // Only meme1 has embedding
            embeddingDao.insertEmbedding(createTestEmbedding(meme1Id))

            // When
            val idsWithoutEmbeddings = embeddingDao.getMemeIdsWithoutEmbeddings()

            // Then
            assertThat(idsWithoutEmbeddings).containsExactly(meme2Id, meme3Id)
        }

    @Test
    fun `markForRegeneration updates the flag`() =
        runTest {
            // Given
            val memeId = insertTestMeme()
            embeddingDao.insertEmbedding(createTestEmbedding(memeId, needsRegeneration = false))

            // When
            embeddingDao.markForRegeneration(memeId)
            val retrieved = embeddingDao.getEmbeddingByMemeId(memeId)

            // Then
            assertThat(retrieved!!.needsRegeneration).isTrue()
        }

    @Test
    fun `deleteOutdatedEmbeddings removes embeddings with different version`() =
        runTest {
            // Given
            val meme1Id = insertTestMeme("meme1")
            val meme2Id = insertTestMeme("meme2")

            embeddingDao.insertEmbedding(createTestEmbedding(meme1Id, modelVersion = "old:1.0.0"))
            embeddingDao.insertEmbedding(createTestEmbedding(meme2Id, modelVersion = "new:2.0.0"))

            // When
            embeddingDao.deleteOutdatedEmbeddings("new:2.0.0")

            // Then
            val embedding1 = embeddingDao.getEmbeddingByMemeId(meme1Id)
            val embedding2 = embeddingDao.getEmbeddingByMemeId(meme2Id)

            assertThat(embedding1).isNull()
            assertThat(embedding2).isNotNull()
            assertThat(embedding2!!.needsRegeneration).isFalse()
        }

    @Test
    fun `deleteEmbeddingByMemeId removes the embedding`() =
        runTest {
            // Given
            val memeId = insertTestMeme()
            embeddingDao.insertEmbedding(createTestEmbedding(memeId))

            // When
            embeddingDao.deleteEmbeddingByMemeId(memeId)
            val retrieved = embeddingDao.getEmbeddingByMemeId(memeId)

            // Then
            assertThat(retrieved).isNull()
        }

    @Test
    fun `embedding is cascade deleted when meme is deleted`() =
        runTest {
            // Given
            val memeId = insertTestMeme()
            embeddingDao.insertEmbedding(createTestEmbedding(memeId))

            // When
            memeDao.deleteMemeById(memeId)
            val retrieved = embeddingDao.getEmbeddingByMemeId(memeId)

            // Then
            assertThat(retrieved).isNull()
        }

    @Test
    fun `countValidEmbeddings returns correct count`() =
        runTest {
            // Given
            val meme1Id = insertTestMeme("meme1")
            val meme2Id = insertTestMeme("meme2")
            val meme3Id = insertTestMeme("meme3")

            embeddingDao.insertEmbedding(createTestEmbedding(meme1Id, needsRegeneration = false))
            embeddingDao.insertEmbedding(createTestEmbedding(meme2Id, needsRegeneration = false))
            embeddingDao.insertEmbedding(createTestEmbedding(meme3Id, needsRegeneration = true))

            // When
            val validCount = embeddingDao.countValidEmbeddings()

            // Then
            assertThat(validCount).isEqualTo(2)
        }

    @Test
    fun `getEmbeddingCountByModelVersion groups correctly`() =
        runTest {
            // Given
            val meme1Id = insertTestMeme("meme1")
            val meme2Id = insertTestMeme("meme2")
            val meme3Id = insertTestMeme("meme3")

            embeddingDao.insertEmbedding(createTestEmbedding(meme1Id, modelVersion = "v1:1.0.0"))
            embeddingDao.insertEmbedding(createTestEmbedding(meme2Id, modelVersion = "v1:1.0.0"))
            embeddingDao.insertEmbedding(createTestEmbedding(meme3Id, modelVersion = "v2:1.0.0"))

            // When
            val counts = embeddingDao.getEmbeddingCountByModelVersion()

            // Then
            assertThat(counts).hasSize(2)
            val v1Count = counts.find { it.modelVersion == "v1:1.0.0" }
            val v2Count = counts.find { it.modelVersion == "v2:1.0.0" }
            assertThat(v1Count!!.count).isEqualTo(2)
            assertThat(v2Count!!.count).isEqualTo(1)
        }

    // ============ Adversarial / Duplicate Data Tests ============

    @Test
    fun `getMemesWithEmbeddings returns multiple rows for meme with multiple embedding types`() =
        runTest {
            // Given
            val memeId = insertTestMeme()
            val contentEmbedding = createTestEmbedding(memeId).copy(embeddingType = "content")
            val intentEmbedding = createTestEmbedding(memeId).copy(embeddingType = "intent")
            embeddingDao.insertEmbedding(contentEmbedding)
            embeddingDao.insertEmbedding(intentEmbedding)

            // When
            val results = embeddingDao.getMemesWithEmbeddings()

            // Then — one row per (meme, embeddingType)
            assertThat(results).hasSize(2)
            assertThat(results.map { it.memeId }.distinct()).containsExactly(memeId)
            assertThat(results.map { it.embeddingType }).containsExactly("content", "intent")
        }

    @Test
    fun `getContentEmbeddingsExcluding returns only content embeddings`() =
        runTest {
            // Given
            val memeId = insertTestMeme()
            val contentEmbedding = createTestEmbedding(memeId).copy(embeddingType = "content")
            val intentEmbedding = createTestEmbedding(memeId).copy(embeddingType = "intent")
            embeddingDao.insertEmbedding(contentEmbedding)
            embeddingDao.insertEmbedding(intentEmbedding)

            // When — excludeMemeId = -1 so nothing is excluded
            val results = embeddingDao.getContentEmbeddingsExcluding(excludeMemeId = -1)

            // Then — only content embedding returned
            assertThat(results).hasSize(1)
            assertThat(results[0].memeId).isEqualTo(memeId)
        }

    @Test
    fun `getContentEmbeddingsExcluding excludes specified meme`() =
        runTest {
            // Given
            val meme1Id = insertTestMeme("meme1")
            val meme2Id = insertTestMeme("meme2")
            embeddingDao.insertEmbedding(createTestEmbedding(meme1Id))
            embeddingDao.insertEmbedding(createTestEmbedding(meme2Id))

            // When — exclude meme1
            val results = embeddingDao.getContentEmbeddingsExcluding(excludeMemeId = meme1Id)

            // Then — only meme2 returned
            assertThat(results).hasSize(1)
            assertThat(results[0].memeId).isEqualTo(meme2Id)
        }

    @Test
    fun `getMemeIdsWithoutEmbeddings ignores memes with any embedding type`() =
        runTest {
            // Given — meme1 has only "intent" embedding (no "content"), meme2 has no embeddings
            val meme1Id = insertTestMeme("meme1")
            val meme2Id = insertTestMeme("meme2")
            val intentEmbedding = createTestEmbedding(meme1Id).copy(embeddingType = "intent")
            embeddingDao.insertEmbedding(intentEmbedding)

            // When
            val idsWithout = embeddingDao.getMemeIdsWithoutEmbeddings()

            // Then — meme1 has an embedding row so is NOT in the result
            assertThat(idsWithout).containsExactly(meme2Id)
        }

    @Test
    fun `countValidEmbeddings counts distinct memes not total embeddings`() =
        runTest {
            // Given — one meme with two valid embeddings (content + intent)
            val memeId = insertTestMeme()
            val contentEmbedding = createTestEmbedding(memeId).copy(embeddingType = "content")
            val intentEmbedding = createTestEmbedding(memeId).copy(embeddingType = "intent")
            embeddingDao.insertEmbedding(contentEmbedding)
            embeddingDao.insertEmbedding(intentEmbedding)

            // When
            val count = embeddingDao.countValidEmbeddings()

            // Then — COUNT(DISTINCT memeId) means 1, not 2
            assertThat(count).isEqualTo(1)
        }

    private suspend fun insertTestMeme(name: String = "test"): Long {
        val entity =
            MemeEntity(
                filePath = "/test/path/$name.jpg",
                fileName = "$name.jpg",
                mimeType = "image/jpeg",
                width = 100,
                height = 100,
                fileSizeBytes = 1000,
                importedAt = System.currentTimeMillis(),
                emojiTagsJson = "😂,🔥",
            )
        return memeDao.insertMeme(entity)
    }

    private fun createTestEmbedding(
        memeId: Long,
        dimension: Int = 128,
        modelVersion: String = "test:1.0.0",
        needsRegeneration: Boolean = false,
    ): MemeEmbeddingEntity {
        val embeddingBytes = ByteArray(dimension * 4)
        return MemeEmbeddingEntity(
            memeId = memeId,
            embedding = embeddingBytes,
            dimension = dimension,
            modelVersion = modelVersion,
            generatedAt = System.currentTimeMillis(),
            needsRegeneration = needsRegeneration,
        )
    }
}
