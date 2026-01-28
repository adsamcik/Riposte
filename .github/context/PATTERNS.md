# Coding Patterns

## Naming Conventions

| Type | Pattern | Example | Notes |
|------|---------|---------|-------|
| Source directories | `src/main/kotlin/` | - | Not `java/` |
| Package names | lowercase | `com.mememymood.feature.gallery` | |
| Classes | PascalCase | `MemeRepository`, `GalleryViewModel` | |
| Interfaces | PascalCase, no prefix | `SearchRepository` | Not `ISearchRepository` |
| Functions | camelCase | `getMemeById()` | |
| Properties | camelCase | `isLoading`, `emojiTags` | |
| Backing properties | Underscore prefix | `_uiState` | For MutableStateFlow |
| Constants | SCREAMING_SNAKE | `MAX_IMAGE_SIZE` | Top-level or companion |
| Type parameters | Single uppercase | `T`, `R` | |
| File names | Match class | `GalleryViewModel.kt` | |
| Test files | Suffix with Test | `GalleryViewModelTest.kt` | |
| Route objects | PascalCase + Route | `GalleryRoute`, `MemeDetailRoute` | |

## MVI Architecture Pattern

Every feature screen follows the MVI pattern consistently:

### UI State

Single immutable data class containing all screen state:

```kotlin
data class GalleryUiState(
    val memes: List<Meme> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedMemeIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val filter: GalleryFilter = GalleryFilter.All,
    val gridColumns: Int = 2,
) {
    // Derived properties as computed vals
    val hasSelection: Boolean get() = selectedMemeIds.isNotEmpty()
    val isEmpty: Boolean get() = memes.isEmpty() && !isLoading
}
```

**Where to see it**:
- `feature/gallery/presentation/GalleryUiState.kt`
- `feature/search/presentation/SearchUiState.kt`
- `feature/settings/presentation/SettingsUiState.kt`

### User Intent

Sealed interface representing all possible user actions:

```kotlin
sealed interface GalleryIntent {
    data object LoadMemes : GalleryIntent
    data class OpenMeme(val memeId: Long) : GalleryIntent
    data class ToggleSelection(val memeId: Long) : GalleryIntent
    data object DeleteSelected : GalleryIntent
    // ... all user actions
}
```

**Where to see it**:
- `feature/gallery/presentation/GalleryIntent.kt`
- `feature/search/presentation/SearchIntent.kt`

### Side Effects

Sealed interface for one-time events (navigation, snackbars):

```kotlin
sealed interface GalleryEffect {
    data class NavigateToMeme(val memeId: Long) : GalleryEffect
    data object NavigateToImport : GalleryEffect
    data class ShowSnackbar(val message: String) : GalleryEffect
    data class LaunchShareIntent(val intent: Intent) : GalleryEffect
}
```

**Where to see it**:
- `feature/gallery/presentation/GalleryEffect.kt`
- `feature/search/presentation/SearchEffect.kt`

### ViewModel Structure

```kotlin
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val getMemesUseCase: GetMemesUseCase,
    private val deleteMemeUseCase: DeleteMemesUseCase,
) : ViewModel() {

    // State as MutableStateFlow with backing property
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    // Effects as Channel for one-time events
    private val _effects = Channel<GalleryEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        loadMemes()  // Initial data load
    }

    // Single entry point for all user actions
    fun onIntent(intent: GalleryIntent) {
        when (intent) {
            is GalleryIntent.LoadMemes -> loadMemes()
            is GalleryIntent.OpenMeme -> openMeme(intent.memeId)
            // ... handle all intents
        }
    }

    private fun loadMemes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getMemesUseCase().collectLatest { memes ->
                _uiState.update { it.copy(memes = memes, isLoading = false) }
            }
        }
    }

    private fun sendEffect(effect: GalleryEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
```

## Use Case Pattern

Single-purpose classes for business logic with `operator fun invoke()`:

```kotlin
class GetMemesUseCase @Inject constructor(
    private val memeRepository: MemeRepository,
) {
    operator fun invoke(): Flow<List<Meme>> = 
        memeRepository.getMemes()
}

class DeleteMemesUseCase @Inject constructor(
    private val memeRepository: MemeRepository,
) {
    suspend operator fun invoke(ids: List<Long>) {
        memeRepository.deleteMemes(ids)
    }
}
```

**Where to see it**:
- `feature/gallery/domain/usecase/GalleryUseCases.kt`
- `feature/search/domain/usecase/SearchUseCases.kt`

## Repository Pattern

### Interface (in domain layer)

```kotlin
interface MemeRepository {
    fun getMemes(): Flow<List<Meme>>
    suspend fun getMemeById(id: Long): Meme?
    suspend fun insertMeme(meme: Meme): Long
    suspend fun deleteMemes(ids: List<Long>)
}
```

### Implementation (in data layer)

```kotlin
class MemeRepositoryImpl @Inject constructor(
    private val memeDao: MemeDao,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : MemeRepository {

    override fun getMemes(): Flow<List<Meme>> =
        memeDao.getAllMemes()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)
}
```

### Hilt Binding

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindMemeRepository(impl: MemeRepositoryImpl): MemeRepository
}
```

## Compose Patterns

### Screen Composable

```kotlin
@Composable
fun GalleryScreen(
    onNavigateToMeme: (Long) -> Unit,
    onNavigateToImport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Handle one-time effects
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is GalleryEffect.NavigateToMeme -> onNavigateToMeme(effect.memeId)
                is GalleryEffect.NavigateToImport -> onNavigateToImport()
                // ...
            }
        }
    }

    GalleryContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
private fun GalleryContent(
    uiState: GalleryUiState,
    onIntent: (GalleryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Stateless UI implementation
}
```

### Component with Slot API

```kotlin
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
        )
        content()
    }
}
```

### Modifier Parameter Convention

Always accept `modifier` as the last parameter with default:

```kotlin
@Composable
fun MemeCard(
    meme: Meme,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,  // Last, with default
) {
    // ...
}
```

## Navigation Pattern

### Route Definition (in core:common)

```kotlin
@Serializable
data object GalleryRoute

@Serializable
data class MemeDetailRoute(val memeId: Long)

@Serializable
data class ShareRoute(val memeIds: List<Long>)
```

### NavGraph Extension Functions

```kotlin
fun NavController.navigateToGallery(navOptions: NavOptions? = null) {
    navigate(GalleryRoute, navOptions)
}

fun NavGraphBuilder.galleryScreen(
    onNavigateToMeme: (Long) -> Unit,
    onNavigateToImport: () -> Unit,
) {
    composable<GalleryRoute> {
        GalleryScreen(
            onNavigateToMeme = onNavigateToMeme,
            onNavigateToImport = onNavigateToImport,
        )
    }
}
```

## Testing Patterns

### ViewModel Test Setup

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: GalleryViewModel
    private val getMemesUseCase: GetMemesUseCase = mockk()

    @Before
    fun setup() {
        coEvery { getMemesUseCase() } returns flowOf(emptyList())
        viewModel = GalleryViewModel(getMemesUseCase, /*...*/)
    }
}
```

### Flow Testing with Turbine

```kotlin
@Test
fun `when memes loaded then state contains memes`() = runTest {
    val memes = listOf(testMeme())
    coEvery { getMemesUseCase() } returns flowOf(memes)

    viewModel.uiState.test {
        val initial = awaitItem()
        assertThat(initial.isLoading).isTrue()

        val loaded = awaitItem()
        assertThat(loaded.memes).isEqualTo(memes)
        assertThat(loaded.isLoading).isFalse()
    }
}
```

### Test Naming

Use backticks with descriptive BDD-style names:

```kotlin
@Test
fun `given empty gallery when user loads then shows empty state`()

@Test
fun `when user selects meme then selection mode enabled`()

@Test
fun `when user confirms delete then memes removed and snackbar shown`()
```

## Error Handling

### Result Wrapper

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}
```

### ViewModel Error Handling

```kotlin
private fun loadMemes() {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            getMemesUseCase().collectLatest { memes ->
                _uiState.update { it.copy(memes = memes, isLoading = false) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = e.message) }
            _effects.send(GalleryEffect.ShowError(e.message ?: "Unknown error"))
        }
    }
}
```

## Hilt Module Patterns

### Provides (for instances)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MemeDatabase =
        Room.databaseBuilder(context, MemeDatabase::class.java, "meme_db")
            .addMigrations(MemeDatabase.MIGRATION_1_2, MemeDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideMemeDao(database: MemeDatabase): MemeDao = database.memeDao()
}
```

### Binds (for interfaces)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository
}
```

## Async Patterns

### Flow Collection in Compose

```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

### Dispatchers

```kotlin
class MemeRepositoryImpl @Inject constructor(
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    fun getMemes(): Flow<List<Meme>> =
        memeDao.getAllMemes()
            .flowOn(ioDispatcher)
}
```

### State Updates

```kotlin
_uiState.update { current ->
    current.copy(isLoading = false, memes = newMemes)
}
```
