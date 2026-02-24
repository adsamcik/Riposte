package com.adsamcik.riposte.core.ml

import com.adsamcik.riposte.core.database.dao.QueryEmbeddingCacheDao
import com.adsamcik.riposte.core.database.entity.MemeEmbeddingEntity
import com.adsamcik.riposte.core.database.entity.QueryEmbeddingCacheEntity
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent LRU cache for query embeddings, backed by Room.
 *
 * Provides a transparent caching layer that survives app restarts.
 * Entries are invalidated when the embedding model version changes.
 */
@Singleton
class PersistentQueryEmbeddingCache
    @Inject
    constructor(
        private val dao: QueryEmbeddingCacheDao,
    ) {
        /**
         * Retrieves a cached query embedding, or null if not found.
         * Updates the access timestamp on cache hit (LRU).
         */
        suspend fun get(query: String): FloatArray? {
            val hash = hashQuery(query)
            val entity = dao.get(hash, MemeEmbeddingEntity.CURRENT_MODEL_VERSION) ?: return null

            // Update LRU access time
            dao.touchAccessTime(hash, System.currentTimeMillis())

            return decodeEmbedding(entity.embedding, entity.dimension)
        }

        /**
         * Stores a query embedding in the persistent cache.
         * Evicts oldest entries when the cache exceeds [MAX_CACHE_SIZE].
         */
        suspend fun put(query: String, embedding: FloatArray) {
            val hash = hashQuery(query)
            val now = System.currentTimeMillis()

            val entity =
                QueryEmbeddingCacheEntity(
                    queryHash = hash,
                    query = query.take(MAX_QUERY_LENGTH),
                    modelVersion = MemeEmbeddingEntity.CURRENT_MODEL_VERSION,
                    embedding = encodeEmbedding(embedding),
                    dimension = embedding.size,
                    createdAt = now,
                    accessedAt = now,
                )

            dao.upsert(entity)

            // Evict if over limit
            val count = dao.count()
            if (count > MAX_CACHE_SIZE) {
                val toDelete = count - MAX_CACHE_SIZE
                dao.deleteOldest(toDelete)
                Timber.d("Evicted %d oldest query cache entries", toDelete)
            }
        }

        /**
         * Removes all cache entries for outdated model versions.
         */
        suspend fun invalidateOutdated() {
            dao.deleteOutdatedEntries(MemeEmbeddingEntity.CURRENT_MODEL_VERSION)
        }

        /**
         * Clears the entire cache.
         */
        suspend fun clearAll() {
            dao.clearAll()
        }

        companion object {
            /** Maximum number of cached query embeddings. */
            const val MAX_CACHE_SIZE = 500

            /** Maximum stored query text length. */
            private const val MAX_QUERY_LENGTH = 500

            private const val BYTES_PER_FLOAT = 4

            fun hashQuery(query: String): String {
                val normalized = query.trim().lowercase()
                val digest = MessageDigest.getInstance("SHA-256")
                val bytes = digest.digest(normalized.toByteArray(Charsets.UTF_8))
                return bytes.joinToString("") { "%02x".format(it) }
            }

            fun encodeEmbedding(embedding: FloatArray): ByteArray {
                val buffer =
                    ByteBuffer
                        .allocate(embedding.size * BYTES_PER_FLOAT)
                        .order(ByteOrder.LITTLE_ENDIAN)
                for (value in embedding) {
                    buffer.putFloat(value)
                }
                return buffer.array()
            }

            fun decodeEmbedding(bytes: ByteArray, dimension: Int): FloatArray {
                val array = FloatArray(dimension)
                ByteBuffer
                    .wrap(bytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer()
                    .get(array)
                return array
            }
        }
    }
