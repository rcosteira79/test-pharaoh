# retrofit

## Purpose

HTTP client abstraction via Retrofit service interfaces, typically with `suspend` functions returning either `T` or `Response<T>`. This entry covers the deterministic set of network-boundary scenarios that every Retrofit-backed repository or data source should exercise regardless of business logic. Retrofit 2 and Retrofit 3 share the same surface for these tests; the floor below applies to both.

## Detection rule

- `retrofit`
- `retrofit-converter-gson`
- `retrofit-converter-moshi`
- `retrofit-converter-kotlinx-serialization`
- `retrofit-adapter-rxjava2`
- `retrofit-adapter-rxjava3`

## Stock edge cases

- **2xx success** — service returns a well-formed body and the caller parses it into the expected model.
- **4xx client error (400)** — malformed request is surfaced as an `HttpException` / `Response.isSuccessful == false` with code 400.
- **4xx client error (401)** — unauthorized response is distinguishable from other 4xx codes so the auth layer can react.
- **4xx client error (404)** — not-found is a distinct branch from generic failure and does not corrupt happy-path state.
- **5xx server error** — server failure is mapped to a retryable / user-visible error, not a parse error.
- **IOException / network failure** — `SocketTimeoutException` and `UnknownHostException` propagate as `IOException`, not as `HttpException`.
- **Malformed body / JSON parse error** — invalid JSON in a 2xx response surfaces as a parse/serialization error, distinct from transport errors.
- **Read timeout** — the client honors `OkHttpClient.readTimeout` and cancels the call instead of hanging.
- **Empty body** — 204 No Content and empty-string bodies for non-nullable types either parse as the documented default or throw a predictable error.

## Assertion patterns

Drive tests with `MockWebServer` so responses are fully deterministic; pair with JUnit 5 and Google Truth for clarity.

```kotlin
class UserServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: UserService

    @BeforeEach
    fun setUp() {
        server = MockWebServer().also { it.start() }
        service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(UserService::class.java)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `returns parsed user on 2xx`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1,"name":"Ada"}"""))

        val user = service.getUser(1)

        assertThat(user).isEqualTo(User(id = 1, name = "Ada"))
    }

    @Test
    fun `surfaces HttpException on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val error = assertThrows<HttpException> { service.getUser(1) }

        assertThat(error.code()).isEqualTo(401)
    }

    @Test
    fun `surfaces IOException on network failure`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertThrows<IOException> { service.getUser(1) }
    }

    @Test
    fun `parse error on malformed body is not HttpException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))

        val error = assertThrows<Throwable> { service.getUser(1) }

        assertThat(error).isNotInstanceOf(HttpException::class.java)
    }
}
```

For `Response<T>` return types, assert `response.isSuccessful`, `response.code()`, and `response.errorBody()?.string()` instead of `assertThrows<HttpException>`.

> **Note:** MockWebServer 4.x uses `SocketPolicy.DISCONNECT_AT_START`; MockWebServer 5.x uses `SocketPolicy.DisconnectAtStart` (class-based). The target project's `okhttp-mockwebserver` version determines which form applies.

## Gotchas

- `HttpException` only wraps non-2xx responses when the service method returns `T` directly. Methods returning `Response<T>` never throw on HTTP failure — tests must branch on `isSuccessful`.
- `suspend` functions are cancellable; a cancelled coroutine propagates as `CancellationException`, not as an `IOException`. Do not assert `IOException` on cancellation paths.
- Retrofit dispatches callbacks on OkHttp's internal executor. When bridging into coroutines, the dispatcher you collect on is not the one the request ran on — assertions about threads must be made at the repository boundary, not inside the service interface.
- `MockWebServer` reuses ports between tests; always call `server.shutdown()` in `@AfterEach` to avoid flaky CI.
- `converter-gson` silently accepts missing fields as nulls, which can hide parse-error test cases. Prefer kotlinx-serialization or Moshi with strict config for the malformed-body test.
- Timeouts are configured on `OkHttpClient`, not on Retrofit. A test that needs to assert timeout behavior must install a client with a short `readTimeout` and pair it with `MockResponse.throttleBody(...)` or `socketPolicy = NO_RESPONSE`.
