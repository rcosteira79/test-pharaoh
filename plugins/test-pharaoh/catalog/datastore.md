# datastore

## Purpose

Key-value and typed persistence via Jetpack DataStore Preferences (and, where present, DataStore Proto). DataStore is the recommended replacement for `SharedPreferences` and exposes data as a `Flow<Preferences>`; the tests in this entry exercise read defaults, round-trip writes, concurrent writes, deletion, and the corruption-recovery path.

## Detection rule

- `androidx-datastore-preferences`
- `androidx-datastore-preferences-core`
- `androidx-datastore`
- `androidx-datastore-core`

## Stock edge cases

- **Initial read of missing key** — reading a key that has never been written returns the documented default (or `null` for nullable types), not an exception.
- **Write-then-read round-trip** — a value written via `edit { }` is visible on the next collection of the `data` Flow.
- **Concurrent writes** — two `edit { }` blocks launched in parallel both commit; the final state reflects both changes and neither is silently dropped.
- **Deletion of a key** — `preferences.remove(key)` causes the next read to return the default for that key without affecting other keys.
- **Corrupt-file recovery** — a `CorruptionException` from the serializer invokes the registered `ReplaceFileCorruptionHandler`, and the DataStore is readable afterwards.

## Assertion patterns

Build a test DataStore over a JUnit `TemporaryFolder` so each test owns a fresh file path, and drive writes with `runTest`.

```kotlin
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private val testScope = TestScope(UnconfinedTestDispatcher())

    @BeforeEach
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmp.newFile("settings.preferences_pb") },
        )
    }

    @Test
    fun `missing key returns default`() = testScope.runTest {
        val key = intPreferencesKey("count")

        val value = dataStore.data.first()[key] ?: 0

        assertThat(value).isEqualTo(0)
    }

    @Test
    fun `write then read round-trips`() = testScope.runTest {
        val key = stringPreferencesKey("name")

        dataStore.edit { it[key] = "Ada" }

        assertThat(dataStore.data.first()[key]).isEqualTo("Ada")
    }

    @Test
    fun `concurrent writes both commit`() = testScope.runTest {
        val a = intPreferencesKey("a")
        val b = intPreferencesKey("b")

        awaitAll(
            async { dataStore.edit { it[a] = 1 } },
            async { dataStore.edit { it[b] = 2 } },
        )

        val snapshot = dataStore.data.first()
        assertThat(snapshot[a]).isEqualTo(1)
        assertThat(snapshot[b]).isEqualTo(2)
    }

    @Test
    fun `remove key restores default on next read`() = testScope.runTest {
        val key = stringPreferencesKey("name")
        dataStore.edit { it[key] = "Ada" }

        dataStore.edit { it.remove(key) }

        assertThat(dataStore.data.first()[key]).isNull()
    }
}
```

Corrupt-file recovery is best tested by writing garbage bytes to the file path before the first read and registering a `ReplaceFileCorruptionHandler { emptyPreferences() }` on the DataStore under test:

```kotlin
@Test
fun `corrupt file recovers via handler`() = testScope.runTest {
    val file = tmp.newFile("settings.preferences_pb").apply {
        writeBytes(byteArrayOf(0x00, 0xDE.toByte(), 0xAD.toByte()))
    }
    val ds = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = testScope,
        produceFile = { file },
    )

    val prefs = ds.data.first()

    assertThat(prefs.asMap()).isEmpty()
}
```

## Gotchas

- DataStore enforces a single instance per file path across the whole process. Building two `DataStore` instances on the same `TemporaryFolder` file inside one test throws `IllegalStateException: There are multiple DataStores active for the same file`. Either use a fresh file per builder or reuse the instance.
- `runTest` uses a `StandardTestDispatcher` by default; DataStore internally collects on `Dispatchers.IO`. Passing `UnconfinedTestDispatcher()` to the DataStore's `scope` is the simplest way to make writes observable synchronously in tests.
- `dataStore.data` is a cold `Flow` but backed by a hot internal actor; `first()` inside a test completes deterministically only if the DataStore `scope` uses the same dispatcher as `runTest`.
- Test isolation: a file that survives between test runs (e.g., not cleaned up in `@AfterEach`) means test ordering can change outcomes. `TemporaryFolder` handles cleanup automatically; a plain `File.createTempFile` does not.
- Do not test DataStore behavior by reading the binary `.preferences_pb` file directly — the on-disk format is an implementation detail and will change without notice.
- The `CorruptionHandler` only fires on `CorruptionException` thrown by the serializer, not on generic `IOException` (e.g., permission denied). Asserting general IO failures requires a custom `DataStoreFactory` and a failing file system, not the corruption handler.
