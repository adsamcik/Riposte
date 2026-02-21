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

## Coverage Goals

- ViewModels: 90%+
- Use Cases: 100%
- Repositories: 80%+
- UI: Critical paths
