package com.adsamcik.riposte.core.testing

import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Fake implementation of a meme repository for testing.
 *
 * Provides in-memory storage of memes with flow-based observation.
 * Supports error simulation and artificial delays for testing loading states.
 *
 * Usage:
 * ```kotlin
 * val fakeRepo = FakeMemeRepository()
 * fakeRepo.emit(listOf(testMeme1, testMeme2))
 *
 * // Test error handling
 * fakeRepo.setError(IOException("Network error"))
 * viewModel.loadMemes()
 * assertThat(viewModel.uiState.value.error).isNotNull()
 * ```
 */
class FakeMemeRepository {
    @Suppress("ktlint:standard:property-naming")
    private val _memes = MutableStateFlow<List<Meme>>(emptyList())
    private var simulatedError: Throwable? = null
    private var simulatedDelay: Long = 0L

    // ============ Flow Accessors ============

    /**
     * Flow of all memes in the repository.
     */
    val allMemes: Flow<List<Meme>> = _memes.asStateFlow()

    /**
     * Flow of favorite memes only.
     */
    val favorites: Flow<List<Meme>> =
        _memes.map { memes ->
            memes.filter { it.isFavorite }
        }

    /**
     * Gets a flow of a single meme by ID.
     */
    fun getMemeById(id: Long): Flow<Meme?> =
        _memes.map { memes ->
            memes.find { it.id == id }
        }

    /**
     * Gets a flow of memes filtered by emoji.
     */
    fun getMemesByEmoji(emoji: String): Flow<List<Meme>> =
        _memes.map { memes ->
            memes.filter { meme ->
                meme.emojiTags.any { it.emoji == emoji }
            }
        }

    /**
     * Gets a flow of memes matching a text query.
     */
    fun searchMemes(query: String): Flow<List<Meme>> =
        _memes.map { memes ->
            val lowerQuery = query.lowercase()
            memes.filter { meme ->
                meme.title?.lowercase()?.contains(lowerQuery) == true ||
                    meme.description?.lowercase()?.contains(lowerQuery) == true ||
                    meme.textContent?.lowercase()?.contains(lowerQuery) == true ||
                    meme.emojiTags.any { it.name.lowercase().contains(lowerQuery) }
            }
        }

    // ============ Mutable Operations ============

    /**
     * Saves a meme to the repository.
     *
     * @param meme The meme to save.
     * @return The saved meme with assigned ID if new.
     */
    suspend fun saveMeme(meme: Meme): Result<Meme> {
        return executeWithErrorHandling {
            val savedMeme =
                if (meme.id == 0L) {
                    meme.copy(id = generateId())
                } else {
                    meme
                }

            _memes.update { memes ->
                val index = memes.indexOfFirst { it.id == savedMeme.id }
                if (index >= 0) {
                    memes.toMutableList().apply { set(index, savedMeme) }
                } else {
                    memes + savedMeme
                }
            }
            savedMeme
        }
    }

    /**
     * Saves multiple memes to the repository.
     */
    suspend fun saveMemes(memes: List<Meme>): Result<List<Meme>> {
        return executeWithErrorHandling {
            memes.map { meme ->
                saveMeme(meme).getOrThrow()
            }
        }
    }

    /**
     * Deletes a meme by ID.
     */
    suspend fun deleteMeme(id: Long): Result<Unit> {
        return executeWithErrorHandling {
            _memes.update { memes ->
                memes.filter { it.id != id }
            }
        }
    }

    /**
     * Deletes multiple memes by IDs.
     */
    suspend fun deleteMemes(ids: Set<Long>): Result<Unit> {
        return executeWithErrorHandling {
            _memes.update { memes ->
                memes.filter { it.id !in ids }
            }
        }
    }

    /**
     * Toggles the favorite status of a meme.
     */
    suspend fun toggleFavorite(id: Long): Result<Meme> {
        return executeWithErrorHandling {
            var updatedMeme: Meme? = null
            _memes.update { memes ->
                memes.map { meme ->
                    if (meme.id == id) {
                        meme.copy(isFavorite = !meme.isFavorite).also { updatedMeme = it }
                    } else {
                        meme
                    }
                }
            }
            updatedMeme ?: throw IllegalArgumentException("Meme with id $id not found")
        }
    }

    /**
     * Updates the emoji tags for a meme.
     */
    suspend fun updateEmojiTags(
        id: Long,
        tags: List<EmojiTag>,
    ): Result<Meme> {
        return executeWithErrorHandling {
            var updatedMeme: Meme? = null
            _memes.update { memes ->
                memes.map { meme ->
                    if (meme.id == id) {
                        meme.copy(emojiTags = tags).also { updatedMeme = it }
                    } else {
                        meme
                    }
                }
            }
            updatedMeme ?: throw IllegalArgumentException("Meme with id $id not found")
        }
    }

    /**
     * Updates the title and description for a meme.
     */
    suspend fun updateMemeDetails(
        id: Long,
        title: String?,
        description: String?,
    ): Result<Meme> {
        return executeWithErrorHandling {
            var updatedMeme: Meme? = null
            _memes.update { memes ->
                memes.map { meme ->
                    if (meme.id == id) {
                        meme.copy(title = title, description = description)
                            .also { updatedMeme = it }
                    } else {
                        meme
                    }
                }
            }
            updatedMeme ?: throw IllegalArgumentException("Meme with id $id not found")
        }
    }

    // ============ Test Helpers ============

    /**
     * Emits a list of memes, replacing all current memes.
     */
    suspend fun emit(memes: List<Meme>) {
        _memes.value = memes
    }

    /**
     * Gets the current list of memes synchronously.
     */
    fun currentMemes(): List<Meme> = _memes.value

    /**
     * Configures the repository to throw an error on the next operation.
     */
    fun setError(error: Throwable?) {
        simulatedError = error
    }

    /**
     * Configures an artificial delay for operations.
     */
    fun setDelay(delayMs: Long) {
        simulatedDelay = delayMs
    }

    /**
     * Clears all memes from the repository.
     */
    fun clear() {
        _memes.value = emptyList()
    }

    /**
     * Resets the repository to its initial state.
     */
    fun reset() {
        _memes.value = emptyList()
        simulatedError = null
        simulatedDelay = 0L
    }

    // ============ Internal Helpers ============

    private var idCounter = 0L

    private fun generateId(): Long = ++idCounter

    private suspend fun <T> executeWithErrorHandling(block: suspend () -> T): Result<T> {
        if (simulatedDelay > 0) {
            delay(simulatedDelay)
        }

        simulatedError?.let { error ->
            simulatedError = null // Reset after throwing
            return Result.failure(error)
        }

        return try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
