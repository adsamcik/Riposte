---
description: 'Testing guidelines for the Riposte Android project'
applyTo: '**/test/**/*.kt,**/androidTest/**/*.kt,**/*Test.kt,**/*Tests.kt'
---

# Testing Guidelines

## Unit Tests

### ViewModel Testing
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: GalleryViewModel
    private lateinit var getMemes: GetMemesUseCase

    @Before
    fun setup() {
        getMemes = mockk()
        viewModel = GalleryViewModel(getMemes)
    }

    @Test
    fun `initial state is loading`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
        }
    }
}
```

### Use Case Testing
```kotlin
class GetMemesUseCaseTest {

    private lateinit var useCase: GetMemesUseCase
    private lateinit var repository: MemeRepository

    @Before
    fun setup() {
        repository = mockk()
        useCase = GetMemesUseCase(repository)
    }

    @Test
    fun `returns memes from repository`() = runTest {
        val memes = listOf(testMeme())
        coEvery { repository.getMemes() } returns flowOf(memes)

        useCase().test {
            assertThat(awaitItem()).isEqualTo(memes)
            awaitComplete()
        }
    }
}
```

### Repository Testing
```kotlin
class MemeRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: MemeRepository
    private lateinit var dao: MemeDao
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setup() {
        dao = mockk()
        testDispatcher = UnconfinedTestDispatcher()
        repository = OfflineMemeRepository(dao, testDispatcher)
    }
}
```

## Libraries

| Library | Purpose |
|---------|---------|
| JUnit 4 | Test framework |
| MockK | Mocking |
| Turbine | Flow testing |
| Truth | Assertions |
| Robolectric | Android unit tests |

## Naming Convention

Use backticks with descriptive names:
```kotlin
@Test
fun `when user clicks save then meme is persisted`()

@Test
fun `given empty list when loading then shows empty state`()
```

## Test Doubles

Located in `core/testing`:
- `FakeMemeRepository`
- `TestDispatcherRule`
- `TestMemeFactory`

## Flow Testing with Turbine

```kotlin
viewModel.uiState.test {
    // Initial state
    val initial = awaitItem()
    assertThat(initial.isLoading).isTrue()

    // After loading
    val loaded = awaitItem()
    assertThat(loaded.isLoading).isFalse()
    assertThat(loaded.memes).hasSize(3)
}
```

## UI Tests

### Compose Testing
```kotlin
@HiltAndroidTest
class GalleryScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun galleryDisplaysMemes() {
        composeRule.onNodeWithTag("meme_grid").assertIsDisplayed()
    }
}
```

### Test Tags
```kotlin
// In production code
Modifier.testTag("meme_card_${meme.id}")

// In test
composeRule.onNodeWithTag("meme_card_123").performClick()
```

## Best Practices

1. **Arrange-Act-Assert** pattern
2. **One assertion per test** (when practical)
3. **Test behavior, not implementation**
4. **Use meaningful test data**
5. **Isolate tests** - no shared mutable state
6. **Fast tests** - mock slow dependencies
7. **Readable tests** - clear names and structure

## Adversarial Input Testing

Always include tests with **duplicate/malformed data** flowing from data sources. DAO JOINs, race conditions, and multi-row expansions can produce duplicates that crash Compose `key` providers.

### Pattern: Duplicate Data Regression Tests
```kotlin
@Test
fun `allMemeIds with duplicates are deduplicated`() = runTest {
    // Mock data source returning duplicates (simulates DAO JOIN expansion)
    coEvery { getAllMemeIdsUseCase() } returns listOf(1L, 2L, 2L, 3L, 3L, 3L)

    viewModel = createViewModel()
    advanceUntilIdle()

    val ids = viewModel.uiState.value.allMemeIds
    assertThat(ids).containsNoDuplicates()
}
```

**When to add adversarial tests:**
- Any ViewModel that feeds data to `LazyList`, `LazyGrid`, or `HorizontalPager` with `key = { ... }`
- Any UseCase that aggregates data from JOINs or multiple sources
- Any repository method that could return duplicate IDs

### Pattern: PagingData Adversarial Tests
Test that paging code paths handle duplicates independently from regular list paths:
```kotlin
@Test
fun `paged memes with duplicate IDs do not crash`() = runTest {
    // Simulate PagingSource returning duplicates (JOIN expansion)
    val pagingData = PagingData.from(
        listOf(testMeme(id = 1), testMeme(id = 2), testMeme(id = 2), testMeme(id = 3))
    )
    coEvery { getPagedMemesUseCase() } returns flowOf(pagingData)

    viewModel = createViewModel()
    advanceUntilIdle()

    // Verify the UI state doesn't crash — paging dedup is applied
    val differ = AsyncPagingDataDiffer(
        diffCallback = MemeComparator,
        updateCallback = NoopListCallback,
        mainDispatcher = UnconfinedTestDispatcher(),
        workerDispatcher = UnconfinedTestDispatcher(),
    )
    viewModel.pagedMemes.test {
        differ.submitData(awaitItem())
        advanceUntilIdle()
        val snapshot = differ.snapshot()
        assertThat(snapshot.items.map { it.id }.distinct()).hasSize(snapshot.items.size)
    }
}
```

### Pattern: PagingData Compose UI Tests

Unit tests CANNOT catch key collisions — they only happen during Compose composition. Any screen using `collectAsLazyPagingItems()` MUST also have an adversarial Compose UI test:

```kotlin
@Test
fun `paging with duplicate IDs does not crash`() {
    val dupes = listOf(testMeme(1L), testMeme(2L), testMeme(2L))
    val flow = MutableStateFlow(PagingData.from(dupes))
    composeTestRule.setContent {
        val items = flow.collectAsLazyPagingItems()
        ScreenUnderTest(pagedMemes = items)
    }
    composeTestRule.waitForIdle() // no crash = pass
}
```

### Pattern: Error Propagation in ViewModels
Every ViewModel action that calls a use case or repository **must** be tested for error propagation. Verify errors update UI state rather than silently failing:
```kotlin
@Test
fun `when delete fails then error state is shown`() = runTest {
    coEvery { deleteMemeUseCase(any()) } throws IOException("disk full")

    viewModel.onIntent(FeatureIntent.DeleteMeme(id = 1L))
    advanceUntilIdle()

    viewModel.uiState.test {
        val state = awaitItem()
        assertThat(state.error).isNotNull()
        assertThat(state.error).contains("disk full")
    }
}

@Test
fun `when load fails then loading state is cleared`() = runTest {
    coEvery { getMemesUseCase() } returns flow { throw IOException("network") }

    viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.uiState.test {
        val state = awaitItem()
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNotNull()
    }
}
```

**Error test checklist for ViewModels:**
1. ✅ Use case throws → error visible in `uiState`
2. ✅ Loading indicator is cleared on error
3. ✅ Previous valid data is preserved (not wiped on error)
4. ✅ Error can be dismissed/retried

### Pattern: Concurrency Tests for Shared State
Test that caches and shared mutable state are thread-safe under concurrent access:
```kotlin
@Test
fun `concurrent cache access does not lose entries`() = runTest {
    val cache = EmbeddingCache() // Uses Mutex internally

    val jobs = (1..100).map { id ->
        launch(UnconfinedTestDispatcher()) {
            cache.getOrCompute(id.toLong()) { computeEmbedding(id.toLong()) }
        }
    }
    jobs.joinAll()

    assertThat(cache.size()).isEqualTo(100)
}

@Test
fun `concurrent cache reads and writes do not throw ConcurrentModificationException`() = runTest {
    val cache = EmbeddingCache()

    val writer = launch {
        repeat(50) { i -> cache.put(i.toLong(), fakeEmbedding()) }
    }
    val reader = launch {
        repeat(50) { cache.getAll() }
    }

    writer.join()
    reader.join()
    // No ConcurrentModificationException = pass
}
```

## Coverage Goals

- ViewModels: 90%+
- Use Cases: 100%
- Repositories: 80%+
- UI: Critical paths
