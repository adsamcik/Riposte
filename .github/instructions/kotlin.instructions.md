---
description: 'Kotlin language guidelines for the Riposte Android project'
applyTo: '**/*.kt'
---

# Kotlin Development Guidelines

## Language Features

### Null Safety
- Embrace Kotlin's null safety; **never use `!!` in production code**
- Use safe calls `?.` and Elvis operator `?:` appropriately
- Use `requireNotNull(value) { "descriptive message" }` when nullability is a programming error
- Use `checkNotNull()` for state validation in public APIs
- Prefer non-nullable types in public APIs

```kotlin
// ✅ Correct: requireNotNull with message
val meme = requireNotNull(repository.getMemeById(id)) { "Meme $id not found" }

// ✅ Correct: Safe call with fallback
val name = meme?.displayName ?: "Unknown"

// ❌ FORBIDDEN in production: !! operator
val meme = repository.getMemeById(id)!!  // Crashes with unhelpful NPE
```

### Data Classes
```kotlin
data class Meme(
    val id: String,
    val imagePath: String,
    val emojis: List<String>,
    val createdAt: Instant,
)
```

### Sealed Types
```kotlin
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val exception: Throwable) : Result<Nothing>
}
```

### Extension Functions
- Create extensions for common operations
- Place extensions in appropriate files (e.g., `StringExtensions.kt`)
- Keep extensions focused and well-documented

### Scope Functions
| Function | Use When |
|----------|----------|
| `let` | Null checks, transforming values |
| `apply` | Object configuration |
| `also` | Side effects, logging |
| `run` | Object configuration with result |
| `with` | Calling multiple methods on an object |

### Collections
- Prefer immutable collections (`listOf`, `setOf`, `mapOf`)
- Use sequence for large collections with multiple operations
- Use appropriate collection functions (`map`, `filter`, `fold`, etc.)

### Coroutines
- Use structured concurrency with proper scopes
- Prefer `suspend` functions for one-shot operations
- Use `Flow` for streams of data
- Always specify dispatchers explicitly for background work

### Coroutine Error Handling
**Every `viewModelScope.launch` block MUST have error handling.** Unhandled exceptions in coroutines silently cancel the scope or crash the app.

```kotlin
// ✅ Correct: try-catch inside launch
viewModelScope.launch {
    try {
        val result = repository.deleteMeme(id)
        _uiState.update { it.copy(deleteSuccess = true) }
    } catch (e: Exception) {
        _uiState.update { it.copy(error = e.message) }
    }
}

// ✅ Also correct: Result wrapper from use case
viewModelScope.launch {
    when (val result = deleteMemeUseCase(id)) {
        is Result.Success -> _uiState.update { it.copy(deleteSuccess = true) }
        is Result.Error -> _uiState.update { it.copy(error = result.exception.message) }
    }
}

// ❌ FORBIDDEN: Bare launch with no error handling
viewModelScope.launch {
    repository.deleteMeme(id)  // If this throws, scope is silently cancelled
}
```

### Thread-Safe Caching
Use `Mutex` to protect shared mutable state accessed from coroutines. Never use bare `HashMap`/`MutableList` shared across coroutines without synchronization.

```kotlin
// ✅ Correct: Mutex-guarded cache
private val cacheMutex = Mutex()
private val cache = mutableMapOf<Long, Embedding>()

suspend fun getOrCompute(id: Long): Embedding = cacheMutex.withLock {
    cache.getOrPut(id) { computeEmbedding(id) }
}

// ❌ FORBIDDEN: Unguarded shared mutable state
private val cache = mutableMapOf<Long, Embedding>()
suspend fun getOrCompute(id: Long): Embedding {
    return cache.getOrPut(id) { computeEmbedding(id) }  // Race condition
}
```

**When to use `Mutex`:** Any `MutableMap`, `MutableList`, or `var` that is read/written from multiple coroutines (e.g., embedding caches, search result caches, in-memory indexes).

## Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Classes | PascalCase | `MemeRepository` |
| Functions | camelCase | `loadMemes()` |
| Properties | camelCase | `isLoading` |
| Constants | SCREAMING_SNAKE_CASE | `MAX_IMAGE_SIZE` |
| Type parameters | Single uppercase letter | `T`, `R` |
| Backing properties | Underscore prefix | `_uiState` |

## Documentation
- Use KDoc for public APIs
- Include `@param`, `@return`, `@throws` tags
- Provide code examples for complex functions
