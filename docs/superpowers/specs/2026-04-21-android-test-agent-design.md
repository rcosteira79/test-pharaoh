# Android Test Agent — Design Spec

**Status**: Draft (approved in brainstorm)
**Date**: 2026-04-21
**Plugin name**: `android-test-agent`
**Command**: `/write-tests`

## 1. Purpose

A Claude Code plugin that writes appropriate tests for code introduced on an Android branch, using the branch's user story and ACs as an independent specification. The workflow mirrors the superpowers pattern: align on feature intent → plan with human review → generate → run → iterate → escalate on persistent failure.

The plugin exists to eliminate a specific failure mode of ad-hoc "generate tests from code" tools: tests that verify whatever the implementation does rather than whatever the feature should do. By taking the story and ACs as independent input, and constraining the generator to a signatures-only view of the code under test, the tool can only produce tests traceable to the spec, the type contract, or a pre-registered catalog.

## 2. Scope

**In scope (v1)**:

- Any Android Kotlin project using Gradle with a version catalog (`libs.versions.toml`).
- Four test tiers: JVM unit, integration (with hand-written fakes), Roborazzi screenshot, end-to-end Cucumber on-device (Compose interactions + MockWebServer network stubbing).
- Personal distribution (installed under `~/.claude/plugins/`), designed for expansion into a shareable form factor.

**Out of scope (v1)**:

- Java source files (skipped by the extractor).
- Projects without any Gradle version catalog (a fallback exists, but it is not a primary path).
- Non-Android / non-Kotlin projects.
- KMP (`commonMain`/`androidMain`/`iosMain` awareness) — reserved for a future iteration, hence the `android-` prefix in the plugin name.

## 3. Form factor

A plugin bundling:

- A **main-session skill** (`skills/write-tests/SKILL.md`) invoked as `/write-tests`. Owns workflow state, all human-review gates, and subagent dispatch.
- Three **subagents** for bounded, parallelizable work: `project-discoverer`, `test-generator`, `failure-analyst`.
- A **bundled CLI tool** (`scripts/kotlin-signatures/`) — a pre-built Kotlin/JVM jar that strips function bodies from `.kt` files and emits a signatures-only view.
- A **catalog** (`catalog/*.md`) of stock edge-case lists per framework, loaded on demand by the generator.
- An optional **SessionStart hook** that surfaces `/write-tests` when an Android project is detected.

### 3.1 Directory layout

```
android-test-agent/
├── skills/
│   └── write-tests/SKILL.md
├── agents/
│   ├── project-discoverer.md
│   ├── test-generator.md
│   └── failure-analyst.md
├── scripts/
│   └── kotlin-signatures/            # pre-built JVM jar + wrapper script
├── catalog/
│   ├── retrofit.md
│   ├── apollo.md
│   ├── room.md
│   ├── datastore.md
│   ├── coroutines-flow.md
│   ├── rxjava2.md
│   ├── hilt.md
│   ├── compose-ui.md
│   ├── nav3.md
│   ├── roborazzi.md
│   ├── viewmodel-stateflow.md
│   └── workmanager.md
├── hooks/
│   └── session-start.sh              # optional
├── docs/
│   └── superpowers/specs/
└── tests/
    └── fixtures/                     # sample project + ACs + failure logs
```

### 3.2 Per-project state (written into the target repo)

```
.claude/android-test-agent/
├── project-profile.json              # discovery output, hashed to libs.versions.toml
├── config.json                        # per-project overrides (parentBranch, etc.)
└── runs/<timestamp>/
    ├── TEST_PLAN.md                  # gate 1 artifact
    ├── run.log
    └── diagnosis.md                  # gate 2 escalation payload
```

## 4. Components

### 4.1 Orchestrator skill

Invoked as `/write-tests`. Takes the user story and ACs as input (pasted into the session). Derives the branch diff against the detected parent. Owns all workflow state and the three review gates (0 feature understanding, 1 plan, 2 escalation).

### 4.2 `project-discoverer` subagent

Runs on first invocation or whenever the version-catalog hash changes. Responsibilities:

- Parse `gradle/libs.versions.toml`; fall back to module `build.gradle.kts` files if the catalog is missing.
- Detect frameworks and their versions (DI, networking, persistence, UI, async, navigation, testing).
- Scan per-module `testInstrumentationRunner` declarations from `build.gradle.kts` and `src/androidTest/AndroidManifest.xml`.
- Locate existing fake infrastructure by pattern — class names matching `Fake*`/`*Fake`, source sets under `*/src/test*/` or `*/src/androidTest/`, modules whose name contains `test`/`testing`/`testutils`. "No existing fake infrastructure" is a valid state.
- Detect mock-library usage (MockK, Mockito). Surfaces later as a refactor proposal; the plugin does not generate new mock-based tests.
- Detect error-wrapper conventions per module (Box<T> vs Result<T>).
- Detect Cucumber end-to-end setup: MockWebServer fixture location, hook classes (Before/After, Hilt test-module swaps), response-fixture format, base test class used by Cucumber runners.
- Read `AGENTS.md` if present.

Writes `.claude/android-test-agent/project-profile.json` with structure including:

- `versionCatalogHash`
- `frameworks: { name → { version, moduleAliases } }`
- `modules: { moduleName → { instrumentationRunner, testTasks, errorWrapper, fakeLocation } }`
- `conventions: { mockLibrary, dispatcher, … }`
- `notesFromAgentsMd: [ … ]`

### 4.3 `test-generator` subagent

Dispatched per (class × tier), in parallel where independent. Inputs:

- Signature extract of the target class (no body).
- Relevant catalog markdown files (matched by detected frameworks and by imports/types visible in the extract).
- Plan excerpt for this specific (class × tier), including traceability markers.
- Project profile.
- Existing tests and fakes for reference (read-allowed — these are not the subject under test).

**Hard constraint**: the generator cannot read the body of the class under test. This is enforced at the input boundary — only the extract is placed in context. If the generator decides the signature is insufficient to specify a test, it returns a clarification request; it does not fall back to reading the body.

Writes test file(s) at `src/test/kotlin/<mirror of production package>/` within the same module as the class under test. Fakes needed for dependencies are created alongside, mirroring the production package, not grouped into a `fakes/` subdirectory. Cross-module fake sharing is not automated — the developer can promote a fake manually after generation.

**Augment, do not replace**: when a class in scope already has tests, the generator adds new test methods to the existing file (or creates a sibling file if the structure doesn't fit). It never rewrites or deletes human-authored tests.

**Cucumber tier specifics**: Cucumber is end-to-end. For each feature-file scenario, the generator also produces MockWebServer stub fixtures covering the backend calls the feature makes. Stub file locations, response-fixture format, hook wiring, and base test class come from the project profile (populated by Discovery). Feature files are Gherkin; step definitions wire into the detected Cucumber + Hilt test infrastructure and use the project's existing dispatcher/thread-pool setup so timing matches production.

### 4.4 `failure-analyst` subagent

Triages test-run output into two categories:

- **Mechanical**: missing imports, dependency-injection wiring gaps, timing flakes, assertion-DSL misuse. Returns a patch.
- **Substantive**: assertion mismatches against intent, behavior divergence. Returns an escalation payload.

Includes a loop guard — if the same test fails with the same error signature after a patch, skip further mechanical attempts on that test and escalate directly.

### 4.5 `kotlin-signatures` CLI

A pre-built JVM jar, built once by the plugin maintainer and shipped in the repo. Takes a `.kt` file path, emits signatures-only Kotlin source to stdout.

Preserves:

- Class, object, interface, sealed class, sealed interface declarations.
- Function signatures including annotations, modifiers, receivers, parameter types, default-parameter presence (values stripped), return types.
- Properties with types and visibility (initializers stripped).
- Sealed-class subtypes declared in the same file.
- Generic bounds and variance.
- KDoc comments.

Drops:

- Function bodies.
- Property initializers.
- `init {}` blocks.

Built on tree-sitter-kotlin Java bindings. JVM chosen for guaranteed availability on Android dev machines.

### 4.6 Catalog

One markdown file per framework. Each file contains:

- **Purpose** — what pattern it covers (e.g., "HTTP client abstraction via Retrofit service interfaces").
- **Detection rule** — version-catalog TOML alias(es) that activate this entry (e.g., `retrofit`, `retrofit-converter-gson`).
- **Stock edge cases** — the deterministic list of scenarios that should be tested when this pattern is present, regardless of business logic (e.g., 2xx success, 4xx client errors, 5xx server errors, network failure, malformed body, timeout).
- **Assertion patterns** — short snippets showing how each case is typically asserted in this project.
- **Gotchas** — framework-specific pitfalls (e.g., dispatcher coupling between coroutines and legacy schedulers, scope cancellation timing).

Catalog entries are a deterministic *floor* of coverage. AC-derived cases stack on top.

## 5. Data flow

```
User → Orchestrator
   pasted: user story + ACs
   implicit: git diff <parent>...HEAD  (three-dot: commits unique to this branch)

Orchestrator
   hash libs.versions.toml → compare with project-profile.json
   if mismatch / missing:
     ──► dispatch Discoverer → writes project-profile.json

   if diff is non-empty:
     scope = (classes in diff) ∩ (classes implicated by ACs)
   else:
     ──► AC-based scope inference
           scan modules named/implied in the ACs (not the whole repo)
           for classes matching AC concepts (name/keyword match)
     ──► present candidate list to user; user confirms, edits, or replaces
     scope = user-confirmed list

   for each class in scope:
     ──► invoke kotlin-signatures jar → signature extract

   resolve catalog files: project-profile.frameworks × imports/types in extracts

─── GATE 0: Feature understanding ───
   Orchestrator writes a short inline summary (3–5 bullets): feature goal,
   who uses it / when, what the ACs collectively imply, classes touched,
   residual ambiguities.
   User confirms or clarifies. Loop until confirmed.

   synthesize TEST_PLAN.md:
     per (class × tier), list planned tests with traceability:
       "source: AC-3"
       "source: contract (LoginError sealed branches)"
       "source: catalog/retrofit.md#4xx"
       "source: inferred"
     + clarification questions for residual gaps
     + mock-refactor recommendations where existing tests rely on mocks

─── GATE 1: Plan review ───
   User approves / edits / clarifies.

   for each approved (class × tier), dispatch in parallel:
     ──► test-generator subagent
           inputs: signature extract, catalog md, plan excerpt, profile,
                   existing tests/fakes (read-allowed; body of target forbidden)
           writes: test file(s) in correct source set

   for each module where tests were written:
     ──► ./gradlew <module>:<task per tier, from profile.modules[module].testTasks>
           (module-scoped — module's locally-configured runner used)

   if failures:
     ──► dispatch failure-analyst
         → mechanical patches → orchestrator applies, ≤2 retries
         → loop guard: same error after patch → skip to escalation
         → substantive → diagnosis.md

─── GATE 2: Escalation (on persistent failure) ───
   User sees: summary (passed / failed / skipped), per-test diagnosis,
   suggested fix paths. State retained under runs/<ts>/.
```

### 5.1 Parent-branch detection

1. `.claude/android-test-agent/config.json` → `parentBranch` if set.
2. Otherwise, the first existing of: `develop`, `main`, `master`.
3. None found → orchestrator prompts the user and persists the choice.

### 5.2 Per-module test-task dispatch

The orchestrator never invokes Gradle at the root. It identifies which modules received generated tests and dispatches module-scoped tasks (`:module:test`, `:module:connectedDebugAndroidTest`, etc.). Gradle picks up the module's locally-declared `testInstrumentationRunner` automatically. This ensures a project with multiple runners (e.g., a Hilt test runner on `:app`, a Cucumber runner on a dedicated test module) uses the correct one per invocation.

### 5.3 Gate 0 rationale

Catching a misinterpretation of the feature at Gate 0 costs a re-read. Catching it at Gate 1 wastes plan synthesis. Catching it after generation wastes tests. The gate is cheap and protects everything downstream.

## 6. Error handling

### 6.1 Input and invocation

- Missing ACs → orchestrator asks before proceeding.
- No diff vs. parent → fall back to AC-based scope inference (see §5): orchestrator scans modules named/implied in the ACs for classes matching AC concepts and presents a candidate list. User confirms, edits, or replaces before proceeding. If the user-confirmed list is empty, orchestrator stops.
- Parent branch not detected → prompt, persist to `config.json`.

### 6.2 Discovery

- No `libs.versions.toml` → fall back to scanning module `build.gradle.kts`; still nothing → report and ask the user to confirm a minimal profile.
- Multiple DI frameworks detected → surface conflict; user picks primary.
- No existing test infrastructure → profile records "greenfield"; generator bootstraps fakes next to real classes.

### 6.3 Signature extraction

- Syntax error / unparseable → skip class; plan entry becomes `EXTRACTOR FAILED — write tests for <Class> manually`.
- Java file → skip (Java out of scope for v1).

### 6.4 Planning

- Missing catalog entry for a detected framework → plan flags `UNKNOWN: no catalog for <framework>; AC + contract only`. The user can add the catalog mid-flow or accept.
- Ambiguous AC → plan entry becomes a clarification question, blocking approval until answered. Foundational ambiguity should have surfaced at Gate 0; Gate 1 clarifications are detail-level.

### 6.5 Generation

- Context limit → orchestrator splits work (by tier or by method) and redispatches.
- Uncompilable tests → caught downstream in the run phase and routed as mechanical.
- Signature-only violation → prevented by the input boundary (the body is never in context).

### 6.6 Test run

- Gradle compile failure → mechanical.
- Genuine test failure → substantive → Gate 2.
- Timeout (default 5 min per task) → escalate.
- No connected device / emulator for the instrumented tier → detected pre-run via `adb devices`; prompt the user to start one or skip that tier.

### 6.7 Failure repair

- ≤2 mechanical retries per failing test; beyond that → escalate.
- Patch fails to apply (conflict with a user-edited file) → escalate.
- Loop guard: same test fails with the same error after a patch → skip further mechanical attempts, escalate directly.

### 6.8 Escalation payload (Gate 2)

Structured report:

- Summary: N passed / M failed / K skipped.
- Per failed test: file path, failure type, most-likely cause, suggested fix path.
- `.claude/android-test-agent/runs/<ts>/` retains TEST_PLAN.md, generated files, logs, and diagnosis — the user can resume manually.

### 6.9 Global principle

Every error becomes either a clarification question (pre-generation), a plan annotation (pre-generation), or a structured escalation (post-run). The orchestrator never silently drops work.

## 7. Testing the plugin itself

### 7.1 Signature extractor

Standard JVM Gradle tests. Fixture `.kt` files covering sealed classes, generics, nested classes, KDoc, type aliases, extension functions, default parameter values, and init blocks. Golden-file comparison against expected signature output. Runs in CI.

### 7.2 Catalog linter

Script verifying: every catalog file has required sections (purpose, detection rule, stock edge cases); filename matches expected TOML alias format; no broken internal links. Runs in CI.

### 7.3 Subagent fixtures

A `tests/fixtures/` directory containing:

- A minimal sample Android project (ViewModel + Repo + DataSource across two modules, version catalog, Hilt setup, one existing fake).
- Synthetic user stories + ACs paired with expected plan outlines.
- Captured failure logs for analyst validation.

Tests:

- `project-discoverer` → profile JSON structure match.
- `test-generator` → structural check on generated tests (expected method names present, no mock library usage, augment-not-replace when existing tests are present).
- `failure-analyst` → triage-category match.

Invoked via Claude Code subagents. Manual locally for v1; automated once a reliable harness exists.

### 7.4 End-to-end dogfooding

The first real validation is `/write-tests` against a small real feature branch in a real Android project (initial dogfooding choice: `~/Secure/groceries-android`), comparing the generated tests against what would have been hand-written. This is the primary v1 quality bar. Gaps surfaced here feed the backlog.

### 7.5 Does the plugin follow its own medicine?

- Signature extractor: yes. Pure code, TDD-friendly.
- Subagent prompts and catalog entries: no. Evaluated via prompt quality and fixture runs, not unit tests.

## 8. Open items / deferred to v2

- KMP support (`commonMain`/`androidMain`/`iosMain` awareness).
- Java source support.
- Automated cross-module fake sharing (currently manual after generation).
- Automated CI integration for subagent fixtures.
- Public / shareable distribution — requires expanding the catalog and discovery rules beyond the frameworks present in the initial dogfooding stack.
