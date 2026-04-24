# workmanager

## Purpose

Background work scheduling via Androidx WorkManager: `OneTimeWorkRequest`, `PeriodicWorkRequest`, `Worker`/`CoroutineWorker`, and the accompanying constraint / retry / cancellation machinery. The tests in this entry exercise the enqueue-to-terminal-state lifecycle using `WorkManagerTestInitHelper` and `TestDriver` so the scheduler runs deterministically on the JVM.

## Detection rule

- `androidx-work-runtime`
- `androidx-work-runtime-ktx`
- `androidx-work-testing`

## Stock edge cases

- **Enqueue `OneTimeWorkRequest`** — enqueueing yields an `ENQUEUED` `WorkInfo` retrievable by the request's UUID.
- **Successful Worker returns `Result.success()`** — running the worker via `TestDriver` transitions the `WorkInfo` to `SUCCEEDED` and exposes the output `Data`.
- **Failure returns `Result.failure()`** — a worker that returns `Result.failure()` transitions to `FAILED`; retries are not scheduled when the work was not enqueued with `setBackoffCriteria`.
- **Retry-on-failure honors backoff** — a worker returning `Result.retry()` transitions through `ENQUEUED` again and the next attempt runs after the declared backoff (`TestDriver.setInitialDelayMet` / `setAllConstraintsMet` drives the virtual time).
- **Cancellation removes the request** — `workManager.cancelWorkById(id)` transitions the `WorkInfo` to `CANCELLED` and does not run the worker.
- **Unmet constraints leave work ENQUEUED** — a request with an unsatisfied constraint (e.g., `setRequiredNetworkType(CONNECTED)`) stays `ENQUEUED` until `TestDriver.setAllConstraintsMet(id)` is called.

## Assertion patterns

Initialize a test-mode `WorkManager` in `@BeforeEach` via `WorkManagerTestInitHelper.initializeTestWorkManager(...)`. Use `TestDriver` to advance constraints and delays. Observe terminal state via `WorkManager.getWorkInfoByIdFlow(id)`.

```kotlin
class UploadWorkerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var testDriver: TestDriver

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!
    }

    @Test
    fun `enqueued request is ENQUEUED`() = runTest {
        val request = OneTimeWorkRequestBuilder<UploadWorker>().build()

        workManager.enqueue(request).result.get()

        val info = workManager.getWorkInfoById(request.id).get()
        assertThat(info.state).isEqualTo(WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `successful worker transitions to SUCCEEDED`() = runTest {
        val request = OneTimeWorkRequestBuilder<SuccessWorker>().build()

        workManager.enqueue(request).result.get()
        testDriver.setAllConstraintsMet(request.id)

        val info = workManager.getWorkInfoById(request.id).get()
        assertThat(info.state).isEqualTo(WorkInfo.State.SUCCEEDED)
    }

    @Test
    fun `failing worker transitions to FAILED`() = runTest {
        val request = OneTimeWorkRequestBuilder<FailingWorker>().build()

        workManager.enqueue(request).result.get()
        testDriver.setAllConstraintsMet(request.id)

        val info = workManager.getWorkInfoById(request.id).get()
        assertThat(info.state).isEqualTo(WorkInfo.State.FAILED)
    }

    @Test
    fun `retry worker re-enqueues after backoff`() = runTest {
        val request = OneTimeWorkRequestBuilder<RetryingWorker>()
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()

        workManager.enqueue(request).result.get()
        testDriver.setAllConstraintsMet(request.id)

        assertThat(workManager.getWorkInfoById(request.id).get().state)
            .isEqualTo(WorkInfo.State.ENQUEUED)

        testDriver.setInitialDelayMet(request.id)
        testDriver.setAllConstraintsMet(request.id)
        // second attempt completes
    }

    @Test
    fun `cancellation transitions to CANCELLED`() = runTest {
        val request = OneTimeWorkRequestBuilder<UploadWorker>().build()
        workManager.enqueue(request).result.get()

        workManager.cancelWorkById(request.id).result.get()

        val info = workManager.getWorkInfoById(request.id).get()
        assertThat(info.state).isEqualTo(WorkInfo.State.CANCELLED)
    }

    @Test
    fun `unmet constraint leaves work ENQUEUED`() = runTest {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        workManager.enqueue(request).result.get()

        val info = workManager.getWorkInfoById(request.id).get()
        assertThat(info.state).isEqualTo(WorkInfo.State.ENQUEUED)
    }
}
```

`getWorkInfoByIdFlow(id)` is the idiomatic observer for long-running transitions:

```kotlin
workManager.getWorkInfoByIdFlow(request.id).test {
    assertThat(awaitItem().state).isEqualTo(WorkInfo.State.ENQUEUED)
    testDriver.setAllConstraintsMet(request.id)
    assertThat(awaitItem().state).isEqualTo(WorkInfo.State.SUCCEEDED)
    cancelAndIgnoreRemainingEvents()
}
```

## Gotchas

- Initialization ORDER matters: `WorkManagerTestInitHelper.initializeTestWorkManager(...)` must run BEFORE any code calls `WorkManager.getInstance(context)` under the test classloader. A Hilt or App-level `WorkManager.initialize(...)` in the Application's `onCreate` will win and the test helper's config is ignored, leading to confusing state where `TestDriver` returns `null`.
- `WorkManagerTestInitHelper.getTestDriver(context)` returns `null` if the WorkManager instance was not initialized in test mode. A Kotlin-idiomatic `!!` at setup time is the fast-fail check; silent `null` here masks the real problem (usually a double-init in `App.onCreate`).
- `SynchronousExecutor()` is the safest choice for deterministic tests; a real `Executor` can interleave work in ways that defeat `testDriver.setAllConstraintsMet(id)`.
- `testDriver.setInitialDelayMet(id)` is for initial delay only; `setAllConstraintsMet(id)` is for `Constraints`. Retry backoff is its own clock — after `Result.retry()`, the next run still requires `setAllConstraintsMet(id)` AND (for periodic work) `setPeriodDelayMet(id)` to simulate the backoff/period elapsing.
- `PeriodicWorkRequest` has a minimum period of 15 minutes in production; in tests the helper does not shorten this. Use `setPeriodDelayMet(id)` to advance rather than trying to force a shorter period.
- `CoroutineWorker` runs on `Dispatchers.Default` by default. Tests that need to observe the coroutine completing synchronously should either inject a `TestDispatcher` via `CoroutineWorker.coroutineContext` override or rely on `SynchronousExecutor` plus `getWorkInfoById(id).get()`.
- `getWorkInfoById(id)` returns a `ListenableFuture`; calling `.get()` blocks. In `runTest` this does not consume virtual time but does block the test thread, which is fine for assertions against `TestDriver`-driven transitions but will hang if the worker is `ENQUEUED` and no driver call follows.
