# room

## Purpose

Local persistence via the Room ORM: `@Entity`, `@Dao`, and `@Database` abstractions over SQLite. The tests in this entry exercise the DAO boundary — insert/update/delete correctness, constraint enforcement, reactive emission on data change, and migration survival — using an in-memory database so the disk layer is isolated from the production instance.

## Detection rule

- `androidx-room-runtime`
- `androidx-room-ktx`
- `androidx-room-compiler`
- `androidx-room-rxjava2`
- `androidx-room-rxjava3`
- `androidx-room-testing`

## Stock edge cases

- **Insert** — a single entity round-trips through the DAO and is readable by primary key.
- **Update** — updating an existing row by primary key changes the expected columns and does not insert a new row.
- **Delete** — deleting by primary key removes the row and subsequent reads return empty / null.
- **Query-empty** — a query against an empty table returns `emptyList()` (never `null`) for `List<T>` return types.
- **Unique-constraint violation** — inserting a duplicate against a `@PrimaryKey` or `@Index(unique = true)` throws `SQLiteConstraintException` (or is swallowed when `OnConflictStrategy.IGNORE` is declared).
- **Flow emission on DAO change** — a `@Query` that returns `Flow<T>` re-emits when a write on the same table occurs, and does not emit for writes on unrelated tables.
- **Migration survival (read-only)** — data written under schema version N is readable under version N+1 after the declared `Migration` runs; no silent fallback to destructive migration.

## Assertion patterns

Build the database with `Room.inMemoryDatabaseBuilder` and `allowMainThreadQueries()` for synchronous tests, or dispatch via `runTest` for `suspend` DAOs. For migration tests, use `MigrationTestHelper` from `androidx.room:room-testing`.

```kotlin
class UserDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UserDao

    @BeforeEach
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.userDao()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert then read by id returns the entity`() = runTest {
        val user = UserEntity(id = 1, name = "Ada")

        dao.insert(user)

        assertThat(dao.getById(1)).isEqualTo(user)
    }

    @Test
    fun `query on empty table returns empty list`() = runTest {
        assertThat(dao.getAll()).isEmpty()
    }

    @Test
    fun `duplicate primary key throws constraint exception`() = runTest {
        dao.insert(UserEntity(id = 1, name = "Ada"))

        assertThrows<SQLiteConstraintException> {
            dao.insert(UserEntity(id = 1, name = "Grace"))
        }
    }

    @Test
    fun `flow re-emits on insert`() = runTest {
        dao.observeAll().test {
            assertThat(awaitItem()).isEmpty()

            dao.insert(UserEntity(id = 1, name = "Ada"))

            assertThat(awaitItem()).containsExactly(UserEntity(id = 1, name = "Ada"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

Migration test using `MigrationTestHelper`:

```kotlin
@get:Rule
val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDatabase::class.java,
)

@Test
fun migrate1To2_preservesUserNames() {
    helper.createDatabase(TEST_DB, version = 1).use { v1 ->
        v1.execSQL("INSERT INTO users (id, name) VALUES (1, 'Ada')")
    }

    val v2 = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
    v2.query("SELECT name FROM users WHERE id = 1").use { c ->
        c.moveToFirst()
        assertThat(c.getString(0)).isEqualTo("Ada")
    }
}
```

## Gotchas

- `allowMainThreadQueries()` is only for tests — production code using it will ANR under load. Do not copy test DAOs into shared fixtures that production might import.
- `suspend` DAOs use the `Dispatchers.IO` defined by Room's internal `queryDispatcher`. In a test using `runTest` with `StandardTestDispatcher`, DAO calls complete off the test scheduler and `advanceUntilIdle()` does not help — prefer `runTest` with a real IO dispatcher or use `withContext(Dispatchers.IO)` in the DAO and assert after `advanceUntilIdle()`.
- `fallbackToDestructiveMigration()` silently wipes data when a declared migration is missing. A migration-survival test passes against `fallbackToDestructiveMigration()` because the table is empty, not because the migration ran — do not include `fallbackToDestructiveMigration()` in the test builder.
- Flow-based queries emit on the `InvalidationTracker` thread; `Turbine.test { }` handles this correctly, but naive `collect { }` inside `launch { }` can race with the write.
- Room's generated DAO implementations are tied to the exact schema. If the schema JSON under `schemas/` is out of date, compilation succeeds but migration tests fail at runtime with a misleading mismatch — regenerate on every schema change.
- `@Query` that returns `Flow<List<T>>` emits an initial `emptyList()` for an empty table; tests must await that first emission before asserting subsequent writes, otherwise the first `awaitItem()` consumes the write-induced emission and the test is off-by-one.
