# hilt

## Purpose

Dependency injection via Hilt (the Dagger-based Android DI library). The tests in this entry exercise the DI graph boundary: that a requested binding resolves to the expected type, that `@TestInstallIn` module swaps override production bindings in tests, that `@HiltViewModel` classes are constructible through the graph, and that scoped vs unscoped lifecycle contracts hold.

## Detection rule

- `google-hilt`
- `google-hilt-compiler`
- `google-hilt-navigationcompose`
- `androidTests-hilt`
- `androidTests-hilt-testing`

## Stock edge cases

- **Binding resolution** — requesting an interface from the graph yields the concrete `@Binds`/`@Provides` implementation declared in the matching `@InstallIn` module.
- **Test module swap** — a `@TestInstallIn(components = [...], replaces = [...])` module replaces the production binding so the injected type is the fake, not the real implementation.
- **`@HiltViewModel` construction** — a `@HiltViewModel` class annotated with `@Inject constructor(...)` can be obtained from `ViewModelProvider` (directly in a `@HiltAndroidTest`) with all dependencies resolved.
- **Scoped vs unscoped lifecycle** — a binding scoped to `@SingletonComponent` returns the same instance across injections; an unscoped binding returns a fresh instance each time.
- **Qualifier disambiguation** — two bindings of the same type differentiated by qualifier annotations resolve to the matching one at the injection site, not a wrong one.

## Assertion patterns

Tests run under `@HiltAndroidTest` with the `HiltAndroidRule`, `HiltTestApplication`, and a Hilt-aware test runner. Override modules with `@UninstallModules` + a test-local `@Module @InstallIn` or with `@TestInstallIn`.

```kotlin
@HiltAndroidTest
@UninstallModules(NetworkModule::class)
class UserRepositoryHiltTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var repository: UserRepository

    @BeforeEach
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `repository is injected with fake network`() {
        assertThat(repository).isNotNull()
        assertThat(repository.api).isInstanceOf(FakeUserApi::class.java)
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object TestNetworkModule {
        @Provides fun provideApi(): UserApi = FakeUserApi()
    }
}
```

A `@TestInstallIn` module lives in `src/androidTest/java` and replaces a production module without per-test annotation:

```kotlin
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class],
)
object FakeNetworkModule {
    @Provides @Singleton fun provideApi(): UserApi = FakeUserApi()
}
```

Scope assertion:

```kotlin
@HiltAndroidTest
class SingletonScopeTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var first: UserRepository
    @Inject lateinit var second: UserRepository

    @BeforeEach fun setUp() { hiltRule.inject() }

    @Test
    fun `singleton-scoped binding is the same instance`() {
        assertThat(first).isSameInstanceAs(second)
    }
}
```

Custom instrumentation runner (required for `@HiltAndroidTest` to boot `HiltTestApplication`):

```kotlin
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?, name: String?, ctx: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, ctx)
}
```

Register it in `build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        testInstrumentationRunner = "com.example.HiltTestRunner"
    }
}
```

## Gotchas

- Without a custom `AndroidJUnitRunner` that launches `HiltTestApplication`, `@HiltAndroidTest` throws at injection time with a vague message about the Application class. This is the single most common Hilt test setup mistake.
- `hiltRule.inject()` must run before any test accesses an `@Inject lateinit` field. Annotating the setup with `@BeforeEach` (JUnit 5) or `@Before` (JUnit 4) only helps if the rule itself is initialized — `@get:Rule val hiltRule` is required, a bare `val hiltRule` does not trigger rule evaluation.
- `@TestInstallIn` is build-type-scoped; a module under `src/androidTest/java` is invisible to unit tests under `src/test/java`. Robolectric-based Hilt tests (`@HiltAndroidTest` + Robolectric) need the module under `src/sharedTest` or an equivalent source set.
- `@UninstallModules` removes a module only for the annotated test class; other test classes in the same APK still see the production binding. Prefer `@TestInstallIn` when the swap is the default for the whole test suite.
- Hilt-generated code requires kapt or KSP. In a multi-module project, if a module declares `@AndroidEntryPoint` without applying the Hilt Gradle plugin, the build fails at runtime with `ClassCastException` on the generated `_HiltModules` — not at compile time.
- `@HiltViewModel` requires `hilt-navigation-compose` (for Compose) or `ViewModelProvider` backed by a Hilt-aware factory. Passing a plain `ViewModelProvider(owner)` with no factory returns a ViewModel that ignores Hilt and crashes on the first injected dep.
- Hilt's `@ViewModelScoped` bindings are owned by the `ViewModelComponent`. Asserting instance identity across two `@Inject` sites in the same test requires both to be injected through the same ViewModel, not through the activity.
