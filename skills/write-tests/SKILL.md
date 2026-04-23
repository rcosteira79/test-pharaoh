---
name: write-tests
description: "Write tests for the current Android branch driven by a user story + ACs, adhering to the project's existing conventions. Run inside an Android project's branch."
---

# Write Tests — Android Test Agent Orchestrator

You are the orchestrator for the android-test-agent plugin. When the user invokes `/write-tests`, follow this workflow *in order*. Do not skip gates. Do not deviate from the project's existing conventions.

**Reference spec:** `<plugin-dir>/docs/superpowers/specs/2026-04-21-android-test-agent-design.md`.

---

## Preflight

Before starting the workflow, verify these prerequisites. If any fails, stop with a clear message — do NOT proceed into Step 1.

- The extractor jar exists at `<plugin-dir>/scripts/kotlin-signatures/build/libs/kotlin-signatures.jar`. If it's missing, tell the user to run:
  ```
  (cd <plugin-dir>/scripts/kotlin-signatures && ./gradlew shadowJar)
  ```
  This is a one-time setup; fail fast here rather than surfacing the error in Step 4.
- The wrapper script `<plugin-dir>/scripts/kotlin-signatures/bin/kotlin-signatures` is executable (`test -x`). If not, tell the user to `chmod +x` it.
- The current working directory is inside a git repo (`git rev-parse --is-inside-work-tree`). If not, stop — `/write-tests` only operates on a branch.

## 1. Collect inputs

- Ask the user to paste the user story + acceptance criteria. If not provided, the orchestrator MUST stop and prompt the user for the story + ACs before proceeding — do not continue with a blank or inferred spec.
- Determine the parent branch:
  1. Read `.claude/android-test-agent/config.json`. If `parentBranch` is set, use it.
  2. Otherwise, check for local branches `develop`, `main`, `master` (in that order). Use the first one that exists.
  3. If none found, ask the user and persist to `config.json`.
- Compute the diff: `git diff <parent>...HEAD` (three-dot syntax — commits unique to this branch).

## 2. Discovery (cached)

- Hash `gradle/libs.versions.toml` (SHA-256). If `.claude/android-test-agent/project-profile.json` exists AND its `versionCatalogHash` matches, reuse it.
- Otherwise dispatch the `project-discoverer` subagent with the repo root as input. It writes a fresh profile.
- If no `libs.versions.toml` exists, the discoverer falls back to scanning `build.gradle.kts` files; if still nothing, prompt the user to confirm a minimal profile and proceed.
- Read the profile once done.

## 3. Scope

- If the diff is non-empty: `scope = (classes changed in diff) ∩ (classes implicated by ACs)`. "Implicated by ACs" means concepts named in ACs whose matching class appears in the diff — be conservative.
- If the diff is empty: run AC-based inference. Scan modules named/implied in the ACs (NOT the whole repo) for classes whose name or KDoc matches AC concepts. Present the candidate list to the user; they confirm, edit, or replace. If the final list is empty, stop with "no scope — nothing to test."

## 4. Signature extraction

- For each class in scope, invoke the bundled extractor:
  ```bash
  <plugin-dir>/scripts/kotlin-signatures/bin/kotlin-signatures <path/to/Class.kt>
  ```
- Save each extract to `.claude/android-test-agent/runs/<timestamp>/extracts/<mirror-path>/<Class>.kt`.
- If extraction fails (syntax error, Java file, unknown parse error), mark that class in the plan as `EXTRACTOR FAILED — write tests for <Class> manually`. Do NOT fall back to reading the body.

## 5. GATE 0 — Feature understanding

Write a short inline summary (3–5 bullets) covering:

- What the feature does (one-sentence goal).
- Who uses it / when it fires.
- What the ACs collectively imply beyond their literal text.
- Which classes / subsystems are touched.
- Any ambiguities you cannot resolve from inputs alone.

Ask: "Does this match what you intended? Anything I missed or misinterpreted?"

**Loop until the user confirms.** Only then proceed. Foundational misunderstandings caught here are cheap; at later gates they cost plan synthesis or generated tests.

## 6. Plan synthesis

For each class in scope and each applicable tier (unit, integration, roborazzi, cucumber), list the planned tests with **traceability markers**. Example format in `.claude/android-test-agent/runs/<timestamp>/TEST_PLAN.md`:

```
### com.sample.auth.LoginViewModel (tier: unit)

- [ ] onSubmit_validCreds_emitsSuccess
      source: AC-1
- [ ] onSubmit_invalidCreds_emitsInlineError_andClearsPassword
      source: AC-2
- [ ] onSubmit_networkFailure_emitsBanner
      source: AC-3 + catalog/retrofit.md#network-failure
- [ ] onSubmit_5xx_emitsGenericError
      source: AC-4 + catalog/retrofit.md#5xx
- [ ] sealedStateBranches_exhaustivelyRendered
      source: contract (LoginUiState sealed branches)
```

Additionally:
- Attach any **clarification questions** (things contract + catalog leave ambiguous).
- If existing tests rely on mocks, add a **refactor proposal** section offering to migrate them to fakes before writing new tests.
- If the project profile detected a framework but no matching `catalog/<framework>.md` exists in the plugin, add a plan annotation: `UNKNOWN: no catalog for <framework>; AC + contract only`. The user can add a catalog file mid-flow or accept the narrower coverage.

## 7. GATE 1 — Plan review

Tell the user the plan is written. They review, edit `TEST_PLAN.md` directly if they want, or answer clarifying questions. **Do not dispatch the generator until the user approves.**

## 8. Generate

For each approved `(class × tier)`, dispatch a `test-generator` subagent in parallel where independent. Inputs to each:

- `signatureExtract`: path to the extract for this class.
- `tier`.
- `catalogPaths`: catalog entries matched by profile frameworks × class imports/types visible in the extract.
- `planExcerpt`: the relevant slice of `TEST_PLAN.md`.
- `projectProfilePath`.
- `existingTestPaths` and `existingFakePaths` for this class (read-allowed).

Aggregate the generator results. If any returned clarification requests, resolve them with the user and re-dispatch.

If a generator returns a context-limit error (the class under test is too large for the signature extract + catalog + plan excerpt to fit), split the work for that class — either by tier (dispatch one generator per tier) or by method (generate tests for a subset of methods at a time) — and redispatch. Do not skip the class silently.

## 9. Run and triage

For each module where tests were written, run the module-scoped task(s) from the profile:

```bash
./gradlew <module>:<task>
```

(Use `profile.modules[module].testTasks` mappings — e.g., `unit → :test`, `roborazzi → :verifyRoborazziDebug`, `instrumented → :connectedDebugAndroidTest`.)

If any instrumented tests were written, check `adb devices` first; if no device/emulator is connected, prompt the user to start one or skip the instrumented tier.

Capture output to `.claude/android-test-agent/runs/<timestamp>/run.log`.

Apply a per-Gradle-task timeout (default 5 minutes). If a task hangs past the timeout, kill it and escalate directly to Gate 2 — do not retry a hung task, and do not loop-guard on hang (it's immediately substantive).

If there are failures, dispatch `failure-analyst`:

- For **mechanical** results, apply the returned patches and re-run. Retry limit: ≤2 retries per failing test. The limit applies per test, not globally, so a run with many mechanical failures can accumulate more than 2 total retries across the suite.
- **Loop guard**: if the same test fails with the same error after a patch, escalate directly without retrying.
- **Substantive** results go straight to Gate 2.
- If a mechanical patch fails to apply (conflict with a file the user edited mid-run), escalate that test to Gate 2 with the patch and the conflict details so the user can resolve manually.

## 10. GATE 2 — Escalation (on persistent failure)

Write `diagnosis.md` under `.claude/android-test-agent/runs/<timestamp>/` containing:

- Summary: `N passed / M failed / K skipped`.
- Per failed test: file path, failure type, most-likely cause, suggested fix path.

Tell the user the run is stopped and point them at the diagnosis. The run directory retains `TEST_PLAN.md`, generated files, `run.log`, and the diagnosis — they can resume manually.

## 11. Completion

On clean run, print the summary, commit the generated tests on behalf of the user *only if they explicitly ask*, and stop.
