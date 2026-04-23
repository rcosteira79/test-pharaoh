# viewmodel-stateflow

## Purpose

Android `ViewModel`s that expose UI state as a `StateFlow`, drive transitions from incoming intents/events, and persist critical state via `SavedStateHandle`. This entry covers the deterministic set of behaviors every such ViewModel should be tested against: initial state equals the declared default, state transitions reflect handled events, saved state survives simulated config change, and `viewModelScope` cancels on `onCleared`.

## Detection rule

- `androidx-lifecycle-viewmodel`
- `androidx-lifecycle-viewmodel-ktx`
- `androidx-lifecycle-viewmodel-savedstate`
- `androidx-lifecycle-viewmodel-compose`
- `androidx-lifecycle-runtime`
- `androidx-lifecycle-runtime-ktx`

## Stock edge cases

- **Initial state is default** — collecting the ViewModel's `uiState` immediately after construction yields the declared default value.
- **State transitions on event** — calling a public event / intent method on the ViewModel updates the state to the documented next value.
- **SavedStateHandle survives config change** — writing to `SavedStateHandle`, reconstructing the ViewModel from the same handle, restores the same state.
- **`viewModelScope` cancels on `onCleared`** — a coroutine launched from `viewModelScope` is cancelled (observable via `isActive == false` or a finally block) after `onCleared()` runs.
- **Error state is modeled, not thrown** — a failing upstream (e.g., repository returns `Result.failure`) surfaces as an `UiState.Error` (or equivalent) rather than as an unhandled exception that crashes the collector.

## Assertion patterns

Tests use `MainDispatcherRule` (a custom rule wrapping `Dispatchers.setMain` / `resetMain`) so `viewModelScope` runs on a controlled test dispatcher. Collect via Turbine.

```kotlin
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(ctx: ExtensionContext?) = Dispatchers.setMain(dispatcher)
    override fun afterEach(ctx: ExtensionContext?) = Dispatchers.resetMain()
}

@ExtendWith(MainDispatcherRule::class)
class CounterViewModelTest {

    @Test
    fun `initial state is default`() = runTest {
        val vm = CounterViewModel(SavedStateHandle())

        vm.uiState.test {
            assertThat(awaitItem()).isEqualTo(CounterUiState(count = 0))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `increment event transitions state`() = runTest {
        val vm = CounterViewModel(SavedStateHandle())

        vm.uiState.test {
            assertThat(awaitItem().count).isEqualTo(0)

            vm.onIncrement()

            assertThat(awaitItem().count).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `savedStateHandle survives reconstruction`() = runTest {
        val handle = SavedStateHandle(mapOf("count" to 5))
        val vm = CounterViewModel(handle)

        vm.uiState.test {
            assertThat(awaitItem().count).isEqualTo(5)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `repository failure surfaces as error state`() = runTest {
        val repo = FakeUserRepository().apply { nextResult = Result.failure(IOException()) }
        val vm = UserViewModel(repo, SavedStateHandle())

        vm.uiState.test {
            assertThat(awaitItem()).isInstanceOf(UserUiState.Loading::class.java)
            vm.load()
            assertThat(awaitItem()).isInstanceOf(UserUiState.Error::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `viewModelScope cancels on onCleared`() = runTest {
        val vm = CounterViewModel(SavedStateHandle())
        var cancelled = false
        vm.viewModelScope.launch {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }

        vm.invokeOnCleared()  // test-only helper; see Gotchas

        assertThat(cancelled).isTrue()
    }
}
```

Where `invokeOnCleared()` is a test-only helper because `onCleared()` is `protected`:

```kotlin
fun ViewModel.invokeOnCleared() {
    ViewModel::class.java.getDeclaredMethod("onCleared").apply {
        isAccessible = true
        invoke(this@invokeOnCleared)
    }
}
```

## Gotchas

- Without `Dispatchers.setMain(testDispatcher)`, `viewModelScope` uses `Dispatchers.Main.immediate`, which on the JVM is backed by `MainCoroutineDispatcher` and throws `IllegalStateException` because no main looper is installed. Every ViewModel test needs a main dispatcher rule; forgetting it yields a cryptic `Module with the Main dispatcher had failed to initialize` error.
- `UnconfinedTestDispatcher` executes launched coroutines eagerly on the calling thread, which makes most ViewModel tests simpler. `StandardTestDispatcher` is correct when the test must assert ordering across `delay` or `advanceTimeBy`.
- `SavedStateHandle` is a `MutableLiveData`-backed bag. Reads and writes via `.get<T>(key)` / `.set(key, value)` are synchronous, but reads via `.getStateFlow(key, default)` emit asynchronously — Turbine's `awaitItem()` is required to observe the restored value.
- `onCleared()` is `protected` and not normally called from outside the framework. Tests either use a reflection helper (above) or wrap the ViewModel in a `ViewModelStore` and call `viewModelStore.clear()`, which is a more faithful simulation of lifecycle teardown.
- Hot `StateFlow` exposed via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Default)` only starts the upstream after a subscriber appears. The first `awaitItem()` under Turbine is that subscriber, so the initial-state assertion must accept that the emission is the default, not a value from the upstream.
- `runTest` schedules virtual time; an event handler that calls `delay(5_000)` will not actually wait 5 seconds — assert after `advanceTimeBy(5_000)` or accept that `UnconfinedTestDispatcher` eagerly skips the delay.
- A ViewModel that stores `SavedStateHandle` internally but never writes to it cannot be restored. A config-change test that passes with an empty handle is not a config-change test; verify the test writes at least one value before reconstructing.
