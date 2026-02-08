package com.adsamcik.riposte.core.common.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import com.adsamcik.riposte.core.common.result.Resource

/**
 * Wraps a Flow to emit Resource states (Loading, Success, Error).
 */
fun <T> Flow<T>.asResource(): Flow<Resource<T>> {
    return this
        .map<T, Resource<T>> { Resource.Success(it) }
        .onStart { emit(Resource.Loading) }
        .catch { e -> emit(Resource.Error(e.message ?: "Unknown error", e)) }
}

/**
 * Executes a suspend block and wraps the result in a Resource.
 */
suspend fun <T> safeCall(block: suspend () -> T): Resource<T> {
    return try {
        Resource.Success(block())
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Unknown error", e)
    }
}
