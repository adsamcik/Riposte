package com.adsamcik.riposte.core.ml

import com.adsamcik.riposte.core.database.dao.QueryEmbeddingCacheDao
import com.adsamcik.riposte.core.database.entity.MemeEmbeddingEntity
import com.adsamcik.riposte.core.database.entity.QueryEmbeddingCacheEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class PersistentQueryEmbeddingCacheTest {
    @MockK(relaxed = true)
    private lateinit var mockDao: QueryEmbeddingCacheDao

    private lateinit var cache: PersistentQueryEmbeddingCache

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        cache = PersistentQueryEmbeddingCache(mockDao)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== hashQuery Tests ====================

    @Test
    fun `hashQuery produces consistent results for same input`() {
        val hash1 = PersistentQueryEmbeddingCache.hashQuery("hello world")
        val hash2 = PersistentQueryEmbeddingCache.hashQuery("hello world")

        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `hashQuery normalizes case and whitespace`() {
        val hash1 = PersistentQueryEmbeddingCache.hashQuery("  Hello World  ")
        val hash2 = PersistentQueryEmbeddingCache.hashQuery("hello world")

        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `hashQuery produces different hashes for different queries`() {
        val hash1 = PersistentQueryEmbeddingCache.hashQuery("hello")
        val hash2 = PersistentQueryEmbeddingCache.hashQuery("world")

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `hashQuery produces 64 character hex string`() {
        val hash = PersistentQueryEmbeddingCache.hashQuery("test query")

        // SHA-256 = 32 bytes = 64 hex chars
        assertThat(hash).hasLength(64)
        assertThat(hash).matches("[0-9a-f]{64}")
    }

    // ==================== Encoding Tests ====================

    @Test
    fun `encodeEmbedding and decodeEmbedding roundtrip correctly`() {
        val original = floatArrayOf(1.0f, -2.5f, 3.14159f, 0.0f, Float.MAX_VALUE)

        val encoded = PersistentQueryEmbeddingCache.encodeEmbedding(original)
        val decoded = PersistentQueryEmbeddingCache.decodeEmbedding(encoded, original.size)

        assertThat(decoded).usingTolerance(0.0).containsExactly(*original.toTypedArray()).inOrder()
    }

    @Test
    fun `encodeEmbedding preserves float precision`() {
        val original = floatArrayOf(1.23456789f, -0.00001f, 999999.9f)

        val encoded = PersistentQueryEmbeddingCache.encodeEmbedding(original)
        val decoded = PersistentQueryEmbeddingCache.decodeEmbedding(encoded, original.size)

        for (i in original.indices) {
            assertThat(decoded[i]).isEqualTo(original[i])
        }
    }

    @Test
    fun `encodeEmbedding produces correct byte count`() {
        val embedding = FloatArray(768) { it.toFloat() }

        val encoded = PersistentQueryEmbeddingCache.encodeEmbedding(embedding)

        // 768 floats * 4 bytes each = 3072 bytes
        assertThat(encoded).hasLength(768 * 4)
    }

    // ==================== Cache Operation Tests ====================

    @Test
    fun `get returns null for missing query`() =
        runTest {
            coEvery {
                mockDao.get(any(), any())
            } returns null

            val result = cache.get("nonexistent query")

            assertThat(result).isNull()
        }

    @Test
    fun `get updates access time on cache hit`() =
        runTest {
            val embedding = floatArrayOf(1.0f, 2.0f, 3.0f)
            val hash = PersistentQueryEmbeddingCache.hashQuery("test query")
            val entity =
                QueryEmbeddingCacheEntity(
                    queryHash = hash,
                    query = "test query",
                    modelVersion = MemeEmbeddingEntity.CURRENT_MODEL_VERSION,
                    embedding = PersistentQueryEmbeddingCache.encodeEmbedding(embedding),
                    dimension = 3,
                    createdAt = 1000L,
                    accessedAt = 1000L,
                )
            coEvery {
                mockDao.get(hash, MemeEmbeddingEntity.CURRENT_MODEL_VERSION)
            } returns entity

            cache.get("test query")

            coVerify { mockDao.touchAccessTime(hash, any()) }
        }

    @Test
    fun `get returns correct embedding on cache hit`() =
        runTest {
            val embedding = floatArrayOf(1.0f, 2.0f, 3.0f)
            val hash = PersistentQueryEmbeddingCache.hashQuery("test query")
            val entity =
                QueryEmbeddingCacheEntity(
                    queryHash = hash,
                    query = "test query",
                    modelVersion = MemeEmbeddingEntity.CURRENT_MODEL_VERSION,
                    embedding = PersistentQueryEmbeddingCache.encodeEmbedding(embedding),
                    dimension = 3,
                    createdAt = 1000L,
                    accessedAt = 1000L,
                )
            coEvery {
                mockDao.get(hash, MemeEmbeddingEntity.CURRENT_MODEL_VERSION)
            } returns entity

            val result = cache.get("test query")

            assertThat(result).isNotNull()
            assertThat(result!!.toList()).containsExactly(1.0f, 2.0f, 3.0f).inOrder()
        }

    @Test
    fun `put stores entry via DAO`() =
        runTest {
            coEvery { mockDao.count() } returns 1

            val entitySlot = slot<QueryEmbeddingCacheEntity>()
            coEvery { mockDao.upsert(capture(entitySlot)) } returns Unit

            cache.put("test query", floatArrayOf(1.0f, 2.0f))

            coVerify { mockDao.upsert(any()) }
            assertThat(entitySlot.captured.query).isEqualTo("test query")
            assertThat(entitySlot.captured.modelVersion).isEqualTo(MemeEmbeddingEntity.CURRENT_MODEL_VERSION)
            assertThat(entitySlot.captured.dimension).isEqualTo(2)
        }

    @Test
    fun `put evicts oldest when over MAX_CACHE_SIZE`() =
        runTest {
            coEvery { mockDao.count() } returns PersistentQueryEmbeddingCache.MAX_CACHE_SIZE + 10

            cache.put("test query", floatArrayOf(1.0f))

            coVerify { mockDao.deleteOldest(10) }
        }

    @Test
    fun `put does not evict when under MAX_CACHE_SIZE`() =
        runTest {
            coEvery { mockDao.count() } returns PersistentQueryEmbeddingCache.MAX_CACHE_SIZE - 1

            cache.put("test query", floatArrayOf(1.0f))

            coVerify(exactly = 0) { mockDao.deleteOldest(any()) }
        }

    @Test
    fun `invalidateOutdated calls deleteOutdatedEntries`() =
        runTest {
            cache.invalidateOutdated()

            coVerify {
                mockDao.deleteOutdatedEntries(MemeEmbeddingEntity.CURRENT_MODEL_VERSION)
            }
        }

    @Test
    fun `clearAll calls dao clearAll`() =
        runTest {
            cache.clearAll()

            coVerify { mockDao.clearAll() }
        }

    @Test
    fun `MAX_CACHE_SIZE is 500`() {
        assertThat(PersistentQueryEmbeddingCache.MAX_CACHE_SIZE).isEqualTo(500)
    }
}
