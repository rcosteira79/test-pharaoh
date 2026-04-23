# compose-ui

## Purpose

UI layer built with Jetpack Compose: `@Composable` functions, state-driven rendering, and user-interaction handling. The tests in this entry exercise the semantic surface of a composable — that it renders the expected content for an initial state, re-renders on state change, routes user interactions to the declared callbacks, and recomposes only when its inputs change.

## Detection rule

- `compose-ui-ui`
- `compose-ui-tooling`
- `compose-material3`
- `compose-material2`
- `compose-foundation`
- `androidTests-compose-ui-test-junit4`
- `androidTests-compose-ui-test-manifest`

## Stock edge cases

- **Initial render** — on first composition with a given state, the expected text, tag, or content description is present in the semantics tree.
- **State-change re-render** — updating a `MutableState` (or a hoisted `State<T>`) between composition and assertion causes the new value to appear without a second `setContent` call.
- **User interaction fires expected intent** — `performClick` / `performTextInput` / `performScrollTo` invokes the callback passed to the composable with the expected argument.
- **Recomposition guard** — a composable does not recompose more than expected when parent state changes that don't affect its inputs (asserted via a counting side-effect inside the composable under test).
- **Disabled state** — a disabled button / text field does not fire its callback on click/input.
- **Content description / accessibility** — the composable exposes a non-empty `contentDescription` or merged semantics for screen readers.

## Assertion patterns

Use `createComposeRule()` for pure composables, or `createAndroidComposeRule<T : Activity>()` when the test needs a real `Activity` (e.g., to exercise a `NavHost`). Drive the UI with `setContent { }` and assert with the `onNodeWithX` matchers.

```kotlin
class CounterScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialRender_showsZero() {
        composeRule.setContent {
            CounterScreen(count = 0, onIncrement = {})
        }

        composeRule.onNodeWithText("0").assertIsDisplayed()
        composeRule.onNodeWithTag("increment").assertHasClickAction()
    }

    @Test
    fun stateChange_reRendersNewValue() {
        val count = mutableStateOf(0)

        composeRule.setContent {
            CounterScreen(count = count.value, onIncrement = { count.value++ })
        }

        composeRule.onNodeWithTag("increment").performClick()

        composeRule.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun click_fires_onIncrement() {
        var clicks = 0

        composeRule.setContent {
            CounterScreen(count = 0, onIncrement = { clicks++ })
        }

        composeRule.onNodeWithTag("increment").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun disabledButton_doesNotFireCallback() {
        var clicks = 0

        composeRule.setContent {
            CounterScreen(count = 0, enabled = false, onIncrement = { clicks++ })
        }

        composeRule.onNodeWithTag("increment").performClick()

        assertThat(clicks).isEqualTo(0)
    }
}
```

Recomposition-counting pattern:

```kotlin
@Test
fun parentStateUnrelatedToChild_doesNotRecomposeChild() {
    var childRecompositions = 0

    composeRule.setContent {
        val unrelated by remember { mutableStateOf(0) }
        Column {
            Text("parent=$unrelated")
            key(Unit) {
                SideEffect { childRecompositions++ }
                CounterScreen(count = 0, onIncrement = {})
            }
        }
    }

    composeRule.waitForIdle()
    assertThat(childRecompositions).isEqualTo(1)
}
```

For animation-sensitive tests, freeze the clock:

```kotlin
@Test
fun fadeIn_atHalfProgress_rendersHalfAlpha() {
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent { FadingCard(visible = true) }

    composeRule.mainClock.advanceTimeBy(150L) // half of a 300ms fade

    composeRule.onNodeWithTag("card").assertIsDisplayed()
}
```

## Gotchas

- `setContent { }` can only be called once per test on `createComposeRule()` / `createAndroidComposeRule()`. To test two different initial states, write two tests, not two `setContent` calls in one test.
- Compose's test clock auto-advances by default. Animation-driven assertions are flaky under `autoAdvance = true` — set it to `false` and call `mainClock.advanceTimeBy(ms)` explicitly for the expected frame.
- `onNodeWithText("0")` matches the rendered substring, not the `Text` composable's raw argument. A locale that formats `0` differently (e.g., bidi-wrapped) will miss the match; prefer `onNodeWithTag(...)` or `onNode(hasText("0"))` with stable input.
- Semantics are merged by default. A `Text` inside a `Row` with `modifier = Modifier.semantics(mergeDescendants = true) { }` is reachable via the parent node, not the `Text` itself — `useUnmergedTree = true` on the matcher is the workaround.
- `composeRule.waitForIdle()` returns when there are no pending recompositions AND no animation frames scheduled. A coroutine launched from `LaunchedEffect` that calls `Dispatchers.IO` is not tracked by the rule — the test must await the resulting state change with `composeRule.waitUntil { }`.
- `performClick` does not dispatch hardware events; it invokes the `onClick` lambda recorded in semantics. This works for `Modifier.clickable`, buttons, and standard material components, but a custom gesture detector bypassed via `pointerInput` will not fire.
- `createAndroidComposeRule<X>()` inflates the activity for real and therefore needs a test manifest entry. Tests that run under Robolectric also need `compose-ui-test-manifest` on the classpath; without it, the activity crashes on inflate with a null-theme error.
