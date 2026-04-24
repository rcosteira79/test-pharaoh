# nav3

## Purpose

Navigation via Jetpack Navigation 3 (the post-NavHost, back-stack-as-state API currently in beta). Nav3 represents the navigation graph as an observable `NavBackStack` list rather than a Navigator-hosted graph, which makes its test shape closer to state-machine tests than to the old `NavController.setGraph` flow. This entry covers the deterministic navigation-graph behaviors every app using Nav3 should exercise.

## Detection rule

- `androidx-navigation3-runtime`
- `androidx-navigation3-ui`
- `androidx-lifecycle-viewmodel-navigation3`

## Stock edge cases

- **Navigate to a new destination** — pushing a destination key extends the back stack by one entry whose top is the expected destination.
- **Back navigation** — popping the top entry leaves the back stack one entry shorter, with the previous destination on top.
- **Argument passing across destinations** — a destination key that carries parameters (as a data-class property) round-trips those parameters to the composable rendered for that entry.
- **Deep link resolution** — parsing a URI / intent extras into a destination key yields the correct back-stack state (usually a single entry or a specific seeded stack).
- **Conditional navigation gate** — an auth-guarded destination, when accessed while `isLoggedIn == false`, redirects to the login destination instead of appearing on the stack.

## Assertion patterns

Nav3 exposes the stack as a `SnapshotStateList<NavKey>` via `rememberNavBackStack(...)`. Tests construct the back stack directly and assert its contents; UI-level tests pair this with a Compose `setContent { NavDisplay(backStack, ...) }` and drive via `onNodeWithX`.

```kotlin
class NavigationTest {

    @Test
    fun navigate_push_addsToBackStack() {
        val backStack = mutableStateListOf<NavKey>(HomeKey)

        backStack.add(DetailsKey(id = 42))

        assertThat(backStack).containsExactly(HomeKey, DetailsKey(id = 42)).inOrder()
    }

    @Test
    fun back_popsTopEntry() {
        val backStack = mutableStateListOf<NavKey>(HomeKey, DetailsKey(id = 42))

        backStack.removeAt(backStack.lastIndex)

        assertThat(backStack).containsExactly(HomeKey)
    }

    @Test
    fun details_receivesIdArgument() {
        val backStack = mutableStateListOf<NavKey>(HomeKey, DetailsKey(id = 42))

        val top = backStack.last() as DetailsKey

        assertThat(top.id).isEqualTo(42)
    }
}
```

For a UI-level assertion that the right screen renders for a back-stack state:

```kotlin
class NavDisplayTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun detailsKey_rendersDetailsScreen() {
        val backStack = mutableStateListOf<NavKey>(HomeKey, DetailsKey(id = 42))

        composeRule.setContent {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeAt(backStack.lastIndex) },
                entryProvider = entryProvider {
                    entry<HomeKey> { HomeScreen() }
                    entry<DetailsKey> { key -> DetailsScreen(id = key.id) }
                },
            )
        }

        composeRule.onNodeWithText("Details for 42").assertIsDisplayed()
    }

    @Test
    fun deepLinkToDetails_seedsStack() {
        val backStack = mutableStateListOf<NavKey>().apply { addAll(parseDeepLink(Uri.parse("app://details/7"))) }

        assertThat(backStack).containsExactly(HomeKey, DetailsKey(id = 7)).inOrder()
    }

    @Test
    fun authGuard_redirectsWhenLoggedOut() {
        val isLoggedIn = false
        val backStack = mutableStateListOf<NavKey>(HomeKey)

        navigateGuarded(backStack, ProfileKey, isLoggedIn) // helper under test

        assertThat(backStack.last()).isEqualTo(LoginKey)
    }
}
```

## Gotchas

- Nav3 is beta as of this writing. APIs like `rememberNavBackStack`, `NavDisplay`, and `entryProvider` have shifted between versions and may still change. Reference the Nav3 skill (`navigation-3`) for current canonical patterns and keep test examples minimal.
- Jetpack Navigation 2 and Nav3 are orthogonal: do NOT mix `NavController.navigate(...)` assertions into Nav3 tests. If the project has both on the classpath during a migration, write separate catalog floors per graph.
- Because the back stack is just a `SnapshotStateList`, tests can mutate it directly without going through a Navigator. This is intentional and preferred for unit tests, but it also means a bug that would be caught by a `NavController`'s validation (e.g., popping an empty stack) must be asserted explicitly in the test, not assumed.
- `rememberNavBackStack(...)` scopes the list to a composition. In a test, build the `mutableStateListOf<NavKey>()` outside `setContent { }` and pass it in — otherwise it is recreated on every recomposition and previous pushes are lost.
- Deep-link parsing lives in application code, not the Nav3 library. A failing deep-link test usually points to the parsing helper, not to Nav3 itself.
- `NavKey` implementations should be `@Serializable` data classes so back-stack state survives process death via `rememberSaveable`. A `NavKey` that is not serializable silently fails to restore — a rotation / process-death test is the only way to catch it.
- ViewModel scoping via `androidx-lifecycle-viewmodel-navigation3` attaches a ViewModel to a single back-stack entry. Assertions about per-destination ViewModel state must be made via `viewModelStoreOwner(entry)` from the entry itself, not from the `LocalViewModelStoreOwner` at the hosting composition.
