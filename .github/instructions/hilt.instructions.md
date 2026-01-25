---
description: 'Hilt dependency injection guidelines for the Meme My Mood Android project'
applyTo: '**/di/**/*.kt,**/*Module.kt,**/*ViewModel.kt'
---

# Hilt Dependency Injection Guidelines

## Module Organization

### Per-Feature Modules
Each feature module should have its own Hilt module:
```
feature/
  gallery/
    src/main/kotlin/
      di/
        GalleryModule.kt
```

### Core Modules
Core modules provide shared dependencies:
```kotlin
// core/database/src/main/kotlin/di/DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): MemeDatabase = Room.databaseBuilder(
        context,
        MemeDatabase::class.java,
        "meme_db"
    ).build()

    @Provides
    fun provideMemeDao(database: MemeDatabase): MemeDao = database.memeDao()
}
```

## Scopes

| Scope | Lifetime | Use For |
|-------|----------|---------|
| `@Singleton` | App lifetime | Database, Retrofit, shared prefs |
| `@ViewModelScoped` | ViewModel lifetime | Use cases, repos per screen |
| `@ActivityScoped` | Activity lifetime | Activity-specific deps |
| Unscoped | Per injection | Lightweight objects |

## ViewModels

```kotlin
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val getMemesUseCase: GetMemesUseCase,
    private val deleteMemesUseCase: DeleteMemesUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // ...
}
```

## Use Cases

```kotlin
class GetMemesUseCase @Inject constructor(
    private val memeRepository: MemeRepository,
) {
    operator fun invoke(): Flow<List<Meme>> = memeRepository.getMemes()
}
```

## Repositories

### Interface Binding
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindMemeRepository(
        impl: OfflineMemeRepository
    ): MemeRepository
}
```

### Implementation
```kotlin
class OfflineMemeRepository @Inject constructor(
    private val memeDao: MemeDao,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : MemeRepository {
    // ...
}
```

## Qualifiers

### Dispatchers
```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Dispatcher(val dispatcher: MemeDispatchers)

enum class MemeDispatchers {
    Default,
    IO,
}

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @Dispatcher(IO)
    fun providesIODispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(Default)
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
```

## Testing

### Test Modules
```kotlin
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideTestDatabase(
        @ApplicationContext context: Context
    ): MemeDatabase = Room.inMemoryDatabaseBuilder(
        context,
        MemeDatabase::class.java
    ).build()
}
```

### Test Rules
```kotlin
@HiltAndroidTest
class GalleryScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }
}
```

## Best Practices

1. **Prefer constructor injection** over field injection
2. **Use `@Binds`** for interface-to-implementation bindings
3. **Use `@Provides`** for third-party or complex objects
4. **Scope appropriately** - don't over-scope
5. **Avoid component dependencies** - use subcomponents or entry points
6. **Test with fakes** - replace modules in tests
