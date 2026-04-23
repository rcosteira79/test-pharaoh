# coroutines-flow

## Purpose

Reactive streams via kotlinx.coroutines: `Flow` (cold), `StateFlow` (hot, state-holding), `SharedFlow` (hot, configurable replay), and their operators. This entry covers the deterministic set of emission, error, cancellation, and completion scenarios that any Flow-producing or Flow-consuming component should be tested against — driven by `kotlinx-coroutines-test` and asserted with Turbine.

## Detection rule

- `kotlinx-coroutines-core`
- `kotlinx-coroutines-android`
- `tests-kotlinx-coroutines-test`
- `tests-turbine`

## Stock edge cases

- **Initial value emission (StateFlow)** — a subscriber receives the current value on collection, without waiting for a new emission.
- **Subsequent emissions** — updates to a `MutableStateFlow` or calls to `emit` on a `SharedFlow` are observed in order by the subscriber.
- **Cancellation propagation** — cancelling the parent scope cancels the collecting coroutine and stops the upstream flow (verified for cold `Flow` and for `stateIn(scope)` hot flows).
- **Error in an operator** — an exception thrown inside `map`, `filter`, or `transform` terminates the flow with that exception and is observable at the collector.
- **Completion (cold Flow)** — a cold `Flow` built from `flow { ... }` terminates with `awaitComplete()` after its last `emit`.
- **Replay semantics (SharedFlow)** — a `MutableSharedFlow(replay = N)` replays the most recent N emissions to a late subscriber.
- **Conflation (StateFlow)** — two rapid updates to the same value dedupe: a subscriber sees the latest value, not every intermediate value.

## Assertion patterns

Use `runTest` from `kotlinx-coroutines-test` and Turbine's `test { ... }` DSL. `TestScope` and `TestDispatcher` make virtual time deterministic.

```kotlin
class CounterViewModelTest {

    @Test
    fun `initial value is emitted on collection`() = runTest {
        val state = MutableStateFlow(0)

        state.test {
            assertThat(awaitItem()).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `subsequent emissions are observed in order`() = runTest {
        val state = MutableStateFlow(0)

        state.test {
            assertThat(awaitItem()).isEqualTo(0)

            state.value = 1
            state.value = 2

            assertThat(awaitItem()).isEqualTo(1)
            assertThat(awaitItem()).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `error in operator surfaces at collector`() = runTest {
        val source = flow {
            emit(1)
            throw IllegalStateException("boom")
        }

        source.test {
            assertThat(awaitItem()).isEqualTo(1)
            val error = awaitError()
            assertThat(error).isInstanceOf(IllegalStateException::class.java)
        }
    }

    @Test
    fun `cold flow completes after last emit`() = runTest {
        val source = flow {
            emit(1)
            emit(2)
        }

        source.test {
            assertThat(awaitItem()).isEqualTo(1)
            assertThat(awaitItem()).isEqualTo(2)
            awaitComplete()
        }
    }

    @Test
    fun `shared flow replays last N to late subscriber`() = runTest {
        val shared = MutableSharedFlow<Int>(replay = 1)
        shared.emit(1)
        shared.emit(2)

        shared.test {
            assertThat(awaitItem()).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

For a hot flow exposed via `stateIn(scope)`, inject a `TestScope` so the `SharingStarted` policy (`WhileSubscribed`, `Eagerly`, `Lazily`) can be advanced deterministically:

```kotlin
val uiState: StateFlow<UiState> = repository.userFlow
    .map(::toUiState)
    .stateIn(testScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
```

## Gotchas

- `runTest` returns the `TestScope`'s `coroutineContext`, which carries a `TestDispatcher`. Any `flowOn(Dispatchers.IO)` inside the production code does NOT use the test dispatcher — emissions happen on a real thread and timings become non-deterministic. Inject the dispatcher from the caller; do not hard-code `Dispatchers.IO` inside a Flow.
- `StandardTestDispatcher` queues coroutines until `advanceUntilIdle()` / `runCurrent()`. `UnconfinedTestDispatcher` runs them eagerly. Use `StandardTestDispatcher` when you need to assert ordering with `delay()`; use `UnconfinedTestDispatcher` when the test only cares about final values.
- `SharingStarted.WhileSubscribed(stopTimeoutMillis)` needs at least one collector to start the upstream. Turbine's `test { }` provides that collector; a bare `first()` may not, depending on timing.
- Turbine's `awaitItem()` is a suspending call — it will time out (default 3 s, configurable) and fail the test rather than hang. If a legitimate emission is slower than the default, set `timeout` on the `test { }` call, do not wrap in `runBlocking`.
- `MutableStateFlow` conflates equal values. A test that emits `1, 1, 2` will see `1, 2` at the collector. Tests asserting every intermediate state must use `MutableSharedFlow` instead.
- `cancelAndIgnoreRemainingEvents()` is required on Turbine tests of hot flows (StateFlow, SharedFlow, `stateIn`) because they never complete. Tests that simply `return@test` without cancelling leak the collection coroutine into the next test.
- Exception handling inside `flow { }` builders is not the same as `catch { }` — throwing inside the builder cancels the flow; catching it with `try/catch` around `emit` violates the Flow exception-transparency rule and will throw at runtime. Use the `catch` operator.
