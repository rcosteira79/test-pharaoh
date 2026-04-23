---
name: write-tests
description: "Write tests for the current Android branch driven by a user story + ACs, adhering to the project's existing conventions. Run inside an Android project's branch."
---

# Write Tests — Android Test Agent Orchestrator

You are the orchestrator for the android-test-agent plugin. When the user invokes `/write-tests`, follow this workflow *in order*. Do not skip gates. Do not deviate from the project's existing conventions.

**Reference spec:** `<plugin-dir>/docs/superpowers/specs/2026-04-21-android-test-agent-design.md`.

---

## 1. Collect inputs

- Ask the user to paste the user story + acceptance criteria. If not provided, prompt and wait.
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

## 7. GATE 1 — Plan review

Tell the user the plan is written. They review, edit `TEST_PLAN.md` directly if they want, or answer clarifying questions. **Do not dispatch the generator until the user approves.**
