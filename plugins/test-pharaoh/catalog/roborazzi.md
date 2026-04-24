# roborazzi

## Purpose

Screenshot (pixel-diff) testing for Jetpack Compose UIs via Roborazzi. Roborazzi runs under Robolectric on the JVM — no emulator required — and captures a Composable's rendered output to a PNG, then diffs against a stored baseline on subsequent runs. The tests in this entry cover the deterministic set of baseline variants that should exist for any screen worth snapshot-testing: light, dark, RTL, large font, and tablet widths.

## Detection rule

- `androidTests-roborazzi-roborazzi`
- `androidTests-roborazzi-compose`
- `androidTests-roborazzi-junit-rule`
- plugin `io.github.takahirom.roborazzi`

## Stock edge cases

- **Light-mode baseline** — capture the default light theme; this is the canonical baseline every screen should have.
- **Dark-mode variant** — capture the same composable under `isSystemInDarkTheme = true` (or an explicit dark theme wrapper) to catch hard-coded colors.
- **RTL layout variant** — capture with `LayoutDirection.Rtl` to catch hard-coded `start` / `end` margins and mirrored iconography issues.
- **Large-font variant** — capture with an elevated `LocalDensity.fontScale` (e.g., 1.3 or 2.0) to catch truncation and overflow.
- **Tablet (sw600dp) variant** — capture at a `DpSize` corresponding to a tablet-width container (e.g., 840dp × 1280dp) to catch phone-only layouts.

## Assertion patterns

Tests use `createComposeRule()` from Compose UI test and wrap `composeRule.setContent { }` around the composable under test, then call `captureRoboImage(...)` with a path. Run with `-Proborazzi.test.record=true` to regenerate baselines; default mode compares.

```kotlin
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HomeScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_lightMode() {
        composeRule.setContent {
            AppTheme(darkTheme = false) { HomeScreen(state = previewState()) }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/home_light.png")
    }

    @Test
    fun home_darkMode() {
        composeRule.setContent {
            AppTheme(darkTheme = true) { HomeScreen(state = previewState()) }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/home_dark.png")
    }

    @Test
    fun home_rtl() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AppTheme { HomeScreen(state = previewState()) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/home_rtl.png")
    }

    @Test
    fun home_largeFont() {
        composeRule.setContent {
            val base = LocalDensity.current
            val scaled = Density(density = base.density, fontScale = 2.0f)
            CompositionLocalProvider(LocalDensity provides scaled) {
                AppTheme { HomeScreen(state = previewState()) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/home_largeFont.png")
    }

    @Test
    @Config(qualifiers = "+w840dp-h1280dp-sw600dp")
    fun home_tablet() {
        composeRule.setContent {
            AppTheme { HomeScreen(state = previewState()) }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/home_tablet.png")
    }
}
```

Build-script snippet (omit `--release` / variant noise):

```kotlin
plugins {
    id("io.github.takahirom.roborazzi")
}

android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}
```

Record baselines once, then commit the PNGs under `src/test/snapshots/` (or the project's configured path):

```bash
./gradlew :app:recordRoborazziDebug
./gradlew :app:verifyRoborazziDebug   # CI check
```

## Gotchas

- Roborazzi stores screenshots under `module/build/outputs/roborazzi/` by default; baselines should be committed to `src/test/snapshots/` (or similar) and explicitly referenced via `captureRoboImage("src/test/snapshots/...")`. Without an explicit path, CI can diff against a stale build-dir artifact and pass for the wrong reason.
- Font rendering depends on installed system fonts. A CI image that is missing Noto / Roboto will render fallback glyphs, which differ pixel-perfectly from a developer machine. Pin the font stack via `FontFamily.Default` resolver or bundle the fonts in the test resources.
- Locale affects digit shaping and number formatting. Lock the test locale (`Locale.setDefault(Locale.US)` in a `@BeforeEach`) so RTL-vs-LTR is the only axis that varies in the RTL variant.
- `RobolectricDeviceQualifiers` and `@Config(qualifiers = ...)` can conflict if both are set. When testing a tablet variant, either set `qualifiers` on the single test method (`@Config(qualifiers = "+w840dp-h1280dp-sw600dp")`) or switch the whole class — do not mix.
- `GraphicsMode.NATIVE` is required for accurate rendering; the legacy `LEGACY` mode produces visibly different shadows and blurs and will fail baselines taken under `NATIVE`.
- Snapshot tests grow repo size fast. Keep baselines per-screen, not per-composable-component, and resist the urge to generate every `@Preview` as a snapshot.
- A change to `AppTheme` or `Typography` invalidates every baseline. Expect a large diff on theme-level changes and review them explicitly; do not auto-accept via `record=true` in CI.
