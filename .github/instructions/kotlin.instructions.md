---
description: 'Kotlin language guidelines for the Riposte Android project'
applyTo: '**/*.kt'
---

# Kotlin Development Guidelines

## Language Features

### Null Safety
- Embrace Kotlin's null safety; avoid `!!` operator
- Use safe calls `?.` and Elvis operator `?:` appropriately
- Use `requireNotNull()` or `checkNotNull()` when nullability is a programming error
- Prefer non-nullable types in public APIs

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
