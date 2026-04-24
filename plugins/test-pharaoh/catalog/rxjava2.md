# rxjava2

## Purpose

Reactive streams via RxJava 2: `Observable`, `Flowable`, `Single`, `Maybe`, `Completable`, and the accompanying `Scheduler` abstraction. The tests in this entry cover the deterministic set of emission, error, completion, disposal, and thread-confinement cases that every RxJava-producing or RxJava-consuming component should be exercised against — using RxJava's own `TestObserver` / `TestSubscriber` and overriding schedulers via `RxJavaPlugins`.

## Detection rule

- `rxjava2`
- `rxandroid`
- `rxkotlin`
- `rxrelay`

## Stock edge cases

- **onNext** — source emits the expected values in order to a `TestObserver`.
- **onError** — source terminates with the expected exception type and no further `onNext` reaches the observer.
- **onComplete** — `Observable` / `Completable` terminate cleanly after the final emission.
- **Disposable.dispose** — disposing the subscription stops further emissions and invokes upstream cancellation (no side effects after dispose).
- **Thread-confinement** — the observer receives emissions on the scheduler declared by `observeOn` (typically `AndroidSchedulers.mainThread()`), regardless of upstream threading.
- **Error propagation through operators** — an exception thrown inside `map` / `flatMap` / `filter` terminates the chain with that exception; downstream `onNext` is not called.

## Assertion patterns

Replace Android schedulers with `Schedulers.trampoline()` or the test-only `TestScheduler` via `RxJavaPlugins` and `RxAndroidPlugins` before each test, and assert with `TestObserver`.

```kotlin
class UserRepositoryRxTest {

    @BeforeEach
    fun setUp() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
    }

    @AfterEach
    fun tearDown() {
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun `single emits value and completes`() {
        val source = Single.just(User(id = 1, name = "Ada"))

        val observer = source.test()

        observer.assertValue(User(id = 1, name = "Ada"))
        observer.assertComplete()
        observer.assertNoErrors()
    }

    @Test
    fun `observable emits error`() {
        val source = Observable.error<User>(IllegalStateException("boom"))

        val observer = source.test()

        observer.assertError(IllegalStateException::class.java)
        observer.assertNoValues()
    }

    @Test
    fun `completable completes`() {
        val source = Completable.complete()

        source.test().assertComplete()
    }

    @Test
    fun `dispose stops emissions`() {
        val subject = PublishSubject.create<Int>()
        val observer = subject.test()

        subject.onNext(1)
        observer.dispose()
        subject.onNext(2)

        observer.assertValue(1)
        observer.assertNotComplete()
    }

    @Test
    fun `error inside map terminates chain`() {
        val source = Observable.just(1, 2, 3).map {
            if (it == 2) throw IllegalArgumentException("bad") else it
        }

        val observer = source.test()

        observer.assertValue(1)
        observer.assertError(IllegalArgumentException::class.java)
    }

    @Test
    fun `observeOn main is honored under trampoline`() {
        val observer = Observable.just("hi")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .test()

        observer.assertValue("hi").assertComplete()
    }
}
```

For tests that depend on virtual time (e.g., `debounce`, `interval`, `delay`), install a `TestScheduler` instead of `trampoline()` and advance with `scheduler.advanceTimeBy(Δ, TimeUnit.MILLISECONDS)`.

## Gotchas

- `RxJavaPlugins` and `RxAndroidPlugins` set process-wide state. Always call `reset()` in `@AfterEach`, or tests that depend on the real `AndroidSchedulers.mainThread()` will silently run on trampoline and pass for the wrong reason.
- `Schedulers.trampoline()` runs work synchronously on the calling thread; it is safe for most tests but hides real concurrency bugs (e.g., two subscribers racing). For those, use a `TestScheduler` per subscription.
- `TestObserver.assertValue(predicate)` takes a `Predicate<T>`, not a `T`. Passing a `T` calls the overload that does equality; passing a lambda calls the predicate overload. A test that reads `assertValue { it.id == 1 }` is not the same as `assertValue(user)`.
- RxJava 2 does not deliver cancellation through coroutines. If the production code bridges via `kotlinx-coroutines-rx2` (`rxSingle { }`, `awaitSingle()`), the bridge uses `Dispatchers.Unconfined` by default — test it with an explicit dispatcher from the caller, not by swapping Rx schedulers.
- `PublishSubject` does not replay. Subscribing after an emission misses that emission. Use `BehaviorSubject` or `ReplaySubject` if the test needs late-subscriber observation.
- Undeliverable exceptions (errors emitted after disposal) go to `RxJavaPlugins.onError`, which by default rethrows on the current thread and crashes the test runner. Install a handler in `@BeforeEach` (`RxJavaPlugins.setErrorHandler { }`) when testing dispose-then-emit paths.
- RxJava 2 and RxJava 3 are different Maven coordinates (`io.reactivex.rxjava2` vs `io.reactivex.rxjava3`). This entry's TOML aliases match the Rx2 coordinates; if a project also declares `rxjava3`, create a separate assertion layer — the two SDKs do not interoperate directly.
