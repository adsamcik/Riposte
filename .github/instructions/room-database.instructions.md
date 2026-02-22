---
description: 'Room database guidelines for the Riposte Android project'
applyTo: '**/database/**/*.kt,**/*Dao.kt,**/*Entity.kt,**/*Database.kt'
---

# Room Database Guidelines

## Entity Design

### Basic Entity
```kotlin
@Entity(tableName = "memes")
data class MemeEntity(
    @PrimaryKey
    val id: String,
    val imagePath: String,
    val createdAt: Long,
    val updatedAt: Long,
)
```

### With Indices
```kotlin
@Entity(
    tableName = "meme_tags",
    indices = [
        Index(value = ["memeId"]),
        Index(value = ["emoji"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = MemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["memeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MemeTagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memeId: String,
    val emoji: String,
)
```

## DAO Patterns

### Basic CRUD
```kotlin
@Dao
interface MemeDao {

    @Query("SELECT * FROM memes ORDER BY createdAt DESC")
    fun getMemes(): Flow<List<MemeEntity>>

    @Query("SELECT * FROM memes WHERE id = :id")
    suspend fun getMemeById(id: String): MemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeme(meme: MemeEntity)

    @Update
    suspend fun updateMeme(meme: MemeEntity)

    @Delete
    suspend fun deleteMeme(meme: MemeEntity)

    @Query("DELETE FROM memes WHERE id = :id")
    suspend fun deleteMemeById(id: String)
}
```

### FTS4 Full-Text Search
```kotlin
@Entity(tableName = "memes_fts")
@Fts4(contentEntity = MemeEntity::class)
data class MemeFtsEntity(
    val description: String,
    val tags: String,
)

@Dao
interface SearchDao {
    @Query("""
        SELECT memes.* FROM memes
        JOIN memes_fts ON memes.id = memes_fts.rowid
        WHERE memes_fts MATCH :query
    """)
    fun searchMemes(query: String): Flow<List<MemeEntity>>
}
```

### Relations
```kotlin
data class MemeWithTags(
    @Embedded
    val meme: MemeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "memeId"
    )
    val tags: List<MemeTagEntity>
)

@Transaction
@Query("SELECT * FROM memes WHERE id = :id")
suspend fun getMemeWithTags(id: String): MemeWithTags?
```

## Type Converters

```kotlin
class Converters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toStringList(value: String): List<String> = 
        if (value.isEmpty()) emptyList() else value.split(",")
}
```

## Database Configuration

```kotlin
@Database(
    entities = [
        MemeEntity::class,
        MemeTagEntity::class,
        MemeFtsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MemeDatabase : RoomDatabase() {
    abstract fun memeDao(): MemeDao
    abstract fun searchDao(): SearchDao
}
```

## Migrations

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memes ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
    }
}

// In database builder
Room.databaseBuilder(context, MemeDatabase::class.java, "meme_db")
    .addMigrations(MIGRATION_1_2)
    .build()
```

## Best Practices

1. **Use Flow for observable queries**
2. **Use suspend for one-shot operations**
3. **Always test migrations**
4. **Export schemas for version control**
5. **Use transactions for related operations**
6. **Index frequently queried columns**
7. **Avoid complex queries in DAOs** - prefer simpler queries
8. **Remove unused DAO methods** — dead code in DAOs is misleading and increases maintenance cost. If a method has no callers, delete it.

## JOIN Query Safety

**All DAO queries with JOINs MUST use `SELECT DISTINCT` or `GROUP BY`.** JOINs across one-to-many relations (e.g., memes → tags, memes → embeddings) produce row multiplication that causes duplicate results and Compose key crashes.

```kotlin
// ✅ Correct: DISTINCT prevents row multiplication
@Query("""
    SELECT DISTINCT memes.* FROM memes
    JOIN meme_tags ON memes.id = meme_tags.memeId
    WHERE meme_tags.emoji IN (:emojis)
""")
fun getMemesByEmojis(emojis: List<String>): Flow<List<MemeEntity>>

// ✅ Correct: GROUP BY for aggregation
@Query("""
    SELECT memes.*, COUNT(meme_tags.id) as tagCount FROM memes
    JOIN meme_tags ON memes.id = meme_tags.memeId
    GROUP BY memes.id
    ORDER BY tagCount DESC
""")
fun getMemesByTagCount(): Flow<List<MemeEntity>>

// ❌ FORBIDDEN: Bare JOIN without DISTINCT or GROUP BY
@Query("""
    SELECT memes.* FROM memes
    JOIN meme_embeddings ON memes.id = meme_embeddings.memeId
    WHERE meme_embeddings.similarity > :threshold
""")
fun getMemesAboveThreshold(threshold: Float): Flow<List<MemeEntity>>
// ^ If a meme has multiple embeddings, this returns duplicate rows
```

## Repository Deduplication

Repository methods returning lists from JOINs **SHOULD** apply `distinctBy` as a defense-in-depth layer, even when the DAO query uses DISTINCT:

```kotlin
// ✅ Defense in depth: dedup at repository layer
override fun getMemesByEmojis(emojis: List<String>): Flow<List<Meme>> =
    memeDao.getMemesByEmojis(emojis)
        .map { entities -> entities.distinctBy { it.id }.map { it.toDomain() } }
        .flowOn(ioDispatcher)
```
