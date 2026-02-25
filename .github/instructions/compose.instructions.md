---
description: 'Jetpack Compose UI guidelines for the Riposte Android project'
applyTo: '**/ui/**/*.kt,**/feature/**/*.kt,**/composable/**/*.kt,**/*Screen.kt,**/*Component.kt'
---

# Jetpack Compose Guidelines

## Composable Functions

### Naming
- Screen-level composables: `FeatureScreen` (e.g., `GalleryScreen`)
- Reusable components: Descriptive names (e.g., `MemeCard`, `EmojiPicker`)
- Preview functions: `FeatureScreenPreview` or `ComponentPreview`

### Structure
```kotlin
@Composable
fun MemeCard(
    meme: Meme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Implementation
}

@Preview(showBackground = true)
@Composable
private fun MemeCardPreview() {
    RiposteTheme {
        MemeCard(
            meme = previewMeme,
            onClick = {},
        )
    }
}
```

### State Hoisting
- Keep composables stateless when possible
- Hoist state to the caller
- Pass events up, state down

```kotlin
// ✅ Good: State hoisted
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
)

// ❌ Avoid: State inside composable (unless necessary)
@Composable
fun SearchBar() {
    var query by remember { mutableStateOf("") }
}
```

### Modifier Parameter
- Always accept `Modifier` as the first optional parameter
- Use `Modifier = Modifier` as default
- Chain modifiers in a single expression

```kotlin
@Composable
fun MemeGrid(
    memes: List<Meme>,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        // ...
    )
}
```

## Side Effects

### LaunchedEffect
Use for coroutines triggered by composition:
```kotlin
LaunchedEffect(key1) {
    // Runs when key1 changes
    viewModel.loadData()
}
```

### DisposableEffect
Use when cleanup is needed:
```kotlin
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event -> }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
    }
}
```

### collectAsStateWithLifecycle
Always use for collecting flows:
```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

### Effect Collection Safety
One-shot effects (navigation, snackbars) use a `Channel` in the ViewModel. **Always** collect them in a `LaunchedEffect`, never with `collectAsStateWithLifecycle`:
```kotlin
// ✅ Correct: LaunchedEffect for Channel-based effects
LaunchedEffect(Unit) {
    viewModel.effects.collect { effect ->
        when (effect) {
            is FeatureEffect.NavigateToDetail -> navController.navigate(...)
            is FeatureEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
        }
    }
}

// ❌ WRONG: collectAsStateWithLifecycle loses events — Channel is not a StateFlow
val effect by viewModel.effects.collectAsStateWithLifecycle(initialValue = null)
```

**Rules:**
- `StateFlow<UiState>` → `collectAsStateWithLifecycle` (survives config changes, lifecycle-aware)
- `Channel<Effect>` / `SharedFlow<Effect>` → `LaunchedEffect(Unit) { flow.collect { ... } }` (consumes once, no replay)

## Performance

### Remember Expensive Calculations
```kotlin
val filteredMemes = remember(memes, query) {
    memes.filter { it.matchesQuery(query) }
}
```

### Stable Types
- Use `@Stable` for types with properties that may change but are observable
- Use `@Immutable` for truly immutable types
- Prefer primitives and immutable collections in state

### Keys in Lists
Always provide stable, **unique** keys. Defensively deduplicate data before using IDs as keys:
```kotlin
// ✅ Good: Deduplicate and use prefixed keys
val uniqueMemes = remember(memes) { memes.distinctBy { it.id } }
LazyColumn {
    items(
        items = uniqueMemes,
        key = { "meme_${it.id}" }
    ) { meme ->
        MemeCard(meme = meme)
    }
}

// ✅ Good: Paging items with dedup tracking and prefixed keys
val seenIds = mutableSetOf<Long>()
for (index in 0 until pagedMemes.itemCount) {
    val peeked = pagedMemes.peek(index)
    if (peeked != null && !seenIds.add(peeked.id)) continue
    item(key = peeked?.let { "paged_${it.id}" } ?: "paged_loading_$index") { ... }
}

// ❌ FORBIDDEN: Raw IDs as keys
items(items = memes, key = { it.id })  // Crashes if duplicates exist

// ❌ FORBIDDEN: Paged items with raw nullable IDs
item(key = peeked?.id ?: "fallback_$index")  // No dedup, raw ID
```

**Key safety rules (mandatory — not optional):**
1. **Always dedup defensively** — use `.distinctBy { it.id }` before passing to `items(key = ...)`
2. **Always prefix keys** — `"section_${it.id}"` not raw `it.id` — prevents cross-list collisions
3. **Never trust data uniqueness** — DAO JOINs, race conditions, and multi-embedding rows can produce duplicates
4. **Paging items need explicit dedup** — use a `seenIds` set to track emitted IDs in paging loops
5. **Never use raw Long/Int as a key** — always convert to a prefixed String

## Material 3

### Theme Usage
```kotlin
MaterialTheme.colorScheme.primary
MaterialTheme.typography.headlineMedium
MaterialTheme.shapes.medium
```

### Dynamic Colors
```kotlin
val colorScheme = if (dynamicColors && Build.VERSION.SDK_INT >= 31) {
    dynamicDarkColorScheme(context) // or dynamicLightColorScheme
} else {
    DarkColorScheme // or LightColorScheme
}
```

## Navigation

### Type-Safe Navigation
```kotlin
@Serializable
data class MemeDetailRoute(val memeId: String)

composable<MemeDetailRoute> { backStackEntry ->
    val route = backStackEntry.toRoute<MemeDetailRoute>()
    MemeDetailScreen(memeId = route.memeId)
}
```

## Testing

### Preview Annotations
```kotlin
@Preview(name = "Light", uiMode = UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Large Font", fontScale = 1.5f)
annotation class ThemePreviews
```

### Test Tags
```kotlin
Modifier.testTag("meme_card_$id")
```
