# apollo

## Purpose

GraphQL client abstraction via Apollo 3 (`com.apollographql.apollo3`) — query, mutation, subscription, and the normalized cache layer. GraphQL responses are different from REST in that an HTTP 200 can still carry an `errors` array, and the cache policy orthogonally controls whether a given call touches the network at all. This entry covers the deterministic network- and cache-boundary cases every Apollo-backed data source should exercise.

## Detection rule

- `apollo3-runtime`
- `apollo3-normalized-cache`
- `apollo3-normalized-cache-sqlite`
- `apollo3-api`
- `apollo3-mockserver`

## Stock edge cases

- **Successful query** — HTTP 200 with `data` present and `errors` absent produces a parsed, non-null model.
- **Successful mutation** — mutation completes and returns the expected optimistic/server-confirmed payload.
- **GraphQL errors alongside data** — HTTP 200 with a non-empty `errors` array must not be swallowed; `response.errors` is surfaced to the caller even when `response.data` is non-null.
- **Partial data with errors** — `data` is partially populated and `errors` lists the failed fields; the repository distinguishes this from full success.
- **Network failure** — `ApolloNetworkException` (or underlying `IOException`) propagates, distinct from `ApolloHttpException` and `ApolloGraphQLException`.
- **Cache HIT** — `fetchPolicy(FetchPolicy.CacheOnly)` returns the previously-cached result without hitting the network.
- **Cache MISS** — `CacheOnly` with no entry returns `response.data == null` or emits an `ApolloCompositeException`, not a network call.
- **Subscription emission** — a subscription emits the expected sequence and terminates cleanly on `cancel()`.

## Assertion patterns

Apollo 3 ships `apollo-mockserver` (Ktor-based) and a `FakeHttpInterceptor`; either is acceptable. The example below uses `MockServer` for HTTP-level control and direct cache assertions.

```kotlin
class UserRepositoryTest {

    private lateinit var mockServer: MockServer
    private lateinit var apollo: ApolloClient

    @BeforeEach
    fun setUp() = runTest {
        mockServer = MockServer()
        apollo = ApolloClient.Builder()
            .serverUrl(mockServer.url())
            .normalizedCache(MemoryCacheFactory(maxSizeBytes = 1_000_000))
            .build()
    }

    @AfterEach
    fun tearDown() = runTest {
        apollo.close()
        mockServer.close()
    }

    @Test
    fun `query returns parsed data on 200`() = runTest {
        mockServer.enqueue("""{"data":{"user":{"id":"1","name":"Ada"}}}""")

        val response = apollo.query(GetUserQuery(id = "1")).execute()

        assertThat(response.hasErrors()).isFalse()
        assertThat(response.data?.user?.name).isEqualTo("Ada")
    }

    @Test
    fun `errors payload is surfaced alongside data`() = runTest {
        mockServer.enqueue(
            """{"data":{"user":null},"errors":[{"message":"forbidden","path":["user"]}]}"""
        )

        val response = apollo.query(GetUserQuery(id = "1")).execute()

        assertThat(response.hasErrors()).isTrue()
        assertThat(response.errors!!.first().message).isEqualTo("forbidden")
    }

    @Test
    fun `cache-only miss does not hit network`() = runTest {
        val response = apollo.query(GetUserQuery(id = "missing"))
            .fetchPolicy(FetchPolicy.CacheOnly)
            .execute()

        assertThat(response.data).isNull()
        assertThat(mockServer.takeRequestOrNull()).isNull()
    }

    @Test
    fun `cache-only hit returns cached data`() = runTest {
        mockServer.enqueue("""{"data":{"user":{"id":"1","name":"Ada"}}}""")
        apollo.query(GetUserQuery(id = "1")).execute() // populate cache

        val cached = apollo.query(GetUserQuery(id = "1"))
            .fetchPolicy(FetchPolicy.CacheOnly)
            .execute()

        assertThat(cached.data?.user?.name).isEqualTo("Ada")
    }
}
```

Subscriptions use `apollo.subscription(...).toFlow()` and are best asserted with Turbine: `awaitItem()` per emission, `awaitComplete()` or `cancelAndIgnoreRemainingEvents()` at the end.

## Gotchas

- `response.hasErrors()` can be true even when `response.data != null`. A repository that early-returns on `data != null` silently drops server errors — always assert both fields in the GraphQL-error test.
- The normalized cache identifies entities by operation ID plus cache keys. If `CacheKeyGenerator` is not configured, cache-hit tests that seem correct in isolation can fail in aggregate because entities collide. Use a fresh `ApolloClient` per test.
- Operation IDs are baked in at codegen time and must stay stable between debug and release, and between the client and a persisted-query allowlist on the server. A release-only test failure is almost always a codegen drift, not logic drift.
- `ApolloHttpException`, `ApolloGraphQLException`, `ApolloNetworkException`, and `ApolloCompositeException` form a hierarchy but are not all thrown — Apollo 3 wraps many failures in the `Response.errors` list instead. Prefer asserting on `response.errors` and `response.exception` over `assertThrows`.
- `MockServer.enqueue("...")` takes a raw JSON string; malformed JSON will fail the serializer, not the test — wrap the parse in `assertThrows<ApolloException>` to assert malformed-body handling.
- Subscriptions under `WebSocketNetworkTransport` require an explicit `close()` on the `ApolloClient` to terminate the underlying socket; leaking it causes the next test's subscription to reuse a zombie connection.
