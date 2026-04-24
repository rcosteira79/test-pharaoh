# Test Pharaoh

Write tests for Android feature branches from your user story + acceptance criteria. Adheres to your project's existing conventions. Never uses mocks.

*You, the Pharaoh, command. The scribe records your decree; the architect surveys the land; the masons lay the stones; the physician tends the cracks. The work is a pyramid of tests.*

## Prerequisites

- [Claude Code](https://claude.com/claude-code)
- An Android project (Kotlin + Gradle — version catalog strongly preferred)
- JDK 17+ on `PATH` (the plugin builds a small signature-extractor JAR the first time you invoke it)

## Install

```
/plugin marketplace add rcosteira79/test-pharaoh
/plugin install test-pharaoh@test-pharaoh
```

The first `/test-scribe` invocation performs a one-time ~30s build of the bundled signature extractor. Subsequent runs skip it. Plugin updates trigger a rebuild on the next run.

## Usage

In your Android repo, on a branch with your feature changes:

```
/test-scribe
```

The scribe will:

1. Ask where to work — current branch, a new branch, or an isolated git worktree.
2. Prompt you for the user story + acceptance criteria.
3. **Gate 0 — feature understanding.** The scribe summarises what it believes the feature does; you confirm or correct. Iterates until you approve.
4. Synthesize `TEST_PLAN.md` with traceability markers (AC-N → test name).
5. **Gate 1 — plan review.** You read the plan, edit freely, approve.
6. Generate tests in parallel across `(class × tier)` units.
7. Run the Gradle tasks from your project profile; triage failures (mechanical patches retry ≤ 2×).
8. **Gate 2 — escalation** (only on persistent substantive failures). The physician writes a diagnosis; you rule.

## The court

| Role | Name | Does |
|---|---|---|
| Commander | **You**, the Pharaoh | Commission the work. Rule on the three gates. |
| Coordinator | `test-scribe` | Record your decree. Commission the build. Report. |
| Surveyor | `architect` | Reads `libs.versions.toml`, module configs, existing fakes, mock-library usage, error-wrapper conventions. Never touches class bodies. |
| Builder | `mason` | One `(class × tier)` per invocation. Hand-written fakes only. Sees signatures, never bodies. |
| Healer | `physician` | Triages Gradle failures. Mechanical → patch and retry. Substantive → escalate to the Pharaoh. |

## Principles

- **Never mocks.** Hand-written fakes only. If your existing tests use MockK or Mockito, Gate 1 proposes migrating them first.
- **Signatures only.** The mason never reads the body of the class under test — tests are written against the contract, not the implementation. Prevents tautological "whatever the code does must be right" tests.
- **Adhere to your conventions.** Fixture placement, dispatcher patterns, error wrappers, assertion libraries, runner wiring — all match what the architect detects in your project.

## Tiers supported

- **Unit** — JUnit 5 + assertion library per your profile.
- **Integration** — real class + real collaborators; fakes only at IO boundaries.
- **Roborazzi** — one `@Test` per visual variant (light/dark/RTL/large-font/tablet).
- **Cucumber** — Gherkin features + step defs + MockWebServer, wired into your Hilt test infrastructure.

## Framework catalog

Deterministic test floors for Retrofit, Apollo, Room, DataStore, Hilt, Coroutines/Flow, RxJava2, WorkManager, Compose, Roborazzi, Navigation 3, and ViewModel/StateFlow. The scribe merges catalog-stock cases with your AC-derived ones.

If the architect detects a framework without a catalog entry, the plan is annotated `UNKNOWN: no catalog — AC + contract only`. Contributions welcome.

## License

MIT
