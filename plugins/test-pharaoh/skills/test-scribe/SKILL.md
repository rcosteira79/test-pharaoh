---
name: test-scribe
description: "Write tests for the current Android branch driven by a user story + ACs, adhering to the project's existing conventions. Run inside an Android project's branch."
---

# Test Scribe — Test Pharaoh Orchestrator

## If you are running as a subagent: STOP

This skill (`test-pharaoh:test-scribe`) runs only in the main Claude Code session. It coordinates three review gates (feature understanding, plan review, escalation) that require direct user interaction — those gates cannot function from a subagent context.

If you were dispatched as a subagent via the Agent tool — by another plugin, by a workflow coordinator, or even by one of this plugin's own subagents (`architect`, `mason`, `physician`) — STOP now. Return to your caller with:

> `/test-scribe` must be invoked from the main session (it requires interactive review gates). Please exit this subagent and invoke `/test-scribe` directly, or narrow your request to a specific subagent task.

Do not execute the workflow below from within a subagent.

---

You are the orchestrator for the test-pharaoh plugin. When the user invokes `/test-scribe`, follow this workflow *in order*. Do not skip gates. Do not deviate from the project's existing conventions.

---

## Source-reading discipline (applies to every step)

You may NEVER open production `.kt` / `.java` files directly with `Read`, `cat`, `head`, `view`, or any equivalent tool. The only sanctioned mechanism for inspecting a production class's surface during this workflow is the bundled signature extractor (see Step 4):

```
<plugin-dir>/scripts/kotlin-signatures/bin/kotlin-signatures <path/to/Class.kt>
```

This rule applies at **every** step — Preflight, Discovery, Scope inference (Step 3), Gate 0 reasoning, plan synthesis (Step 6), Gate 1 narration, every clarification question, every status update.

**Forbidden:**

- Opening a production `.kt` / `.java` file directly to "understand what it does", to justify scope, to write a Gate 0 summary, or to enrich a clarification question.
- Paraphrasing implementation details in any user-facing message ("X maps `Y ?: Z`", "method M sets field F from G", "this contains the canBeDeselected branch") — those are body-reads even when paraphrased. If you find yourself wanting to write that kind of sentence, STOP and run the extractor first; describe only what the extractor's output shows.

**Allowed:**

- File paths and module structure (`find`, `ls`, `git ls-files`, `git diff`).
- Pattern matching by symbol name (`grep -l`, `grep -n` for declaration sites only — pattern matching is allowed; opening the matched file's body is not).
- Build files (`build.gradle.kts`, `settings.gradle.kts`, `libs.versions.toml`, `*.toml`, `*.properties`).
- The extractor's output saved at `.claude/test-pharaoh/runs/<timestamp>/extracts/...`.
- Existing **test** files (`src/test/**/*.kt`, `src/androidTest/**/*.kt`) — those are not production bodies and are explicitly allowed for context.
- The project profile JSON.

**Sequencing implication:** If you need to know more about a candidate class than its name and grep matches, run the extractor on it before saying anything substantive about it. Do not read the body first and run the extractor later as cover.

If the extractor fails on a class, mark that class as `EXTRACTOR FAILED` in the plan and skip it — do NOT fall back to reading the body.

---

## Preflight

Before starting the workflow, verify these prerequisites. If any fails, stop with a clear message — do NOT proceed into Step 1.

- The extractor jar must exist at `<plugin-dir>/scripts/kotlin-signatures/build/libs/kotlin-signatures.jar`. If it's missing (fresh install, or post-`/plugin update` which wipes `build/`), build it yourself before continuing:
  ```
  (cd <plugin-dir>/scripts/kotlin-signatures && ./gradlew shadowJar)
  ```
  Tell the user you're doing a one-time ~30s build of the signature extractor. Requires JDK 17+. If the build fails (e.g., JDK not installed, no network for first-time Gradle dependency fetch), stop and report the error with a prompt for the user to install JDK 17+ or re-run with network access.
- The wrapper script `<plugin-dir>/scripts/kotlin-signatures/bin/kotlin-signatures` is executable (`test -x`). If not, tell the user to `chmod +x` it.
- The current working directory is inside a git repo (`git rev-parse --is-inside-work-tree`). If not, stop — `/test-scribe` only operates on a branch.

## Branch / worktree selection

Before collecting the story + ACs, ask the user where tests should be written:

- **(a) Current branch** — work on the branch already checked out. Generated tests land here. Pick this if you already have a feature branch ready.
- **(b) New branch** — create a new branch off `HEAD`. Suggest a name based on the feature (e.g., `tests/<feature-slug>`); let the user override. Run: `git checkout -b <name>`.
- **(c) Worktree** — create an isolated git worktree so the user's current workspace is untouched. Do this inline; do not invoke any other skill.

  1. Pick a worktree parent directory. Prefer existing `.worktrees/` at the repo root; otherwise existing `worktrees/`; otherwise default to creating `.worktrees/`.
  2. Verify the parent is gitignored — probe with `git check-ignore -q <parent>/probe`. If not ignored, add the dir to `.gitignore` and commit that change BEFORE creating the worktree; otherwise generated content may leak into the user's git history.
  3. Ask the user for a branch name. Suggest `tests/<feature-slug>` based on the story title; accept overrides.
  4. Create the worktree: `git worktree add <parent>/<dir-safe-name> -b <branch-name>`. Replace `/` with `-` in the directory path only; keep the branch name as-is.
  5. `cd` into the new worktree. Continue to Step 1 from there.

Do not proceed to Step 1 until the user has picked one and, for (b)/(c), confirmed the branch name.

## Initialize run directory

Generate a fresh ISO-8601 UTC timestamp for this run and create its directory **before** moving to Step 1:

```bash
TIMESTAMP=$(date -u +%Y%m%dT%H%M%SZ)   # e.g. 20260426T142105Z
mkdir -p .claude/test-pharaoh/runs/$TIMESTAMP
```

Use `$TIMESTAMP` consistently throughout this workflow for every artefact:
- Signature extracts: `.claude/test-pharaoh/runs/$TIMESTAMP/extracts/...`
- Test plan: `.claude/test-pharaoh/runs/$TIMESTAMP/TEST_PLAN.md`
- Run log: `.claude/test-pharaoh/runs/$TIMESTAMP/run.log`
- Diagnostic files: `.claude/test-pharaoh/runs/$TIMESTAMP/diagnosis.md`

**Hard rules:**

1. **One run = one fresh timestamp.** Generate a new timestamp at the start of every `/test-scribe` invocation. Never reuse an existing run directory.
2. **Never overwrite a previous run's TEST_PLAN.md or run.log.** If you find yourself about to write into a path under an older run dir, stop — you forgot to initialize a new one.
3. **The run directory must exist before Step 4** (signature extraction). Other steps assume `$TIMESTAMP` is set.
4. **Surface the timestamp to the user** in your first user-facing message after Discovery (e.g. "Run dir: `.claude/test-pharaoh/runs/20260426T142105Z/`"). This lets the user follow along and locate artefacts.

## 1. Collect inputs

- Inputs from the user:
  - **User story + acceptance criteria** (required). If the user invoked `/test-scribe` with these inline, use them; otherwise stop and prompt. Never continue with a blank or inferred spec.
  - **Any other relevant context** (optional) — design decisions, product intent, edge cases the user wants emphasised, links to prior discussions or PRs. Weave into Gate 0 reasoning and plan synthesis. Do not prompt for it if not offered; just accept it when given.
- Determine the parent branch:
  1. Read `.claude/test-pharaoh/config.json`. If `parentBranch` is set, use it.
  2. Otherwise, check for local branches `develop`, `main`, `master` (in that order). Use the first one that exists.
  3. If none found, ask the user and persist to `config.json`.
- Compute the diff: `git diff <parent>...HEAD` (three-dot syntax — commits unique to this branch).

## 2. Discovery (cached)

- Hash `gradle/libs.versions.toml` (SHA-256). If `.claude/test-pharaoh/project-profile.json` exists AND its `versionCatalogHash` matches, reuse it.
- Otherwise dispatch the `architect` subagent with the repo root as input. It writes a fresh profile.
- If no `libs.versions.toml` exists, the discoverer falls back to scanning `build.gradle.kts` files; if still nothing, prompt the user to confirm a minimal profile and proceed.
- Read the profile once done.

## 3. Scope

**Per the global source-reading discipline above, you may not read production `.kt` bodies during scope inference.** Use only file paths, module structure, and `grep`/`find` pattern matching by symbol name. If a candidate class needs deeper inspection to justify inclusion, run the extractor on it (Step 4 procedure) and use the extract — do NOT open the source file.

- **If the diff is non-empty:** `scope = (classes changed in diff) ∩ (classes implicated by ACs)`. "Implicated by ACs" means concepts named in ACs whose matching class appears in the diff — be conservative. Identify candidates via `git diff --name-only` plus AC-concept grep; do not open the changed files.
- **If the diff is empty:** run AC-based inference. Scan modules named/implied in the ACs (NOT the whole repo) for files whose name or symbol matches AC concepts via `grep -l`/`find`. For each shortlisted candidate, run the extractor (per Step 4) BEFORE writing scope justifications — your justification text must be derivable from the extract alone, not from the file body. Present the candidate list to the user; they confirm, edit, or replace. If the final list is empty, stop with "no scope — nothing to test."

**When presenting the scope to the user**, justify each candidate in terms the extractor's output supports — public function signatures, return types, parameter lists, class-level KDoc that the extractor exposes. Do NOT paraphrase what a function's body does ("maps X ?: Y", "sets selectByDate from chooseByDate"). If a justification requires body-level detail, that's a signal to either (a) restate the justification at signature level, or (b) drop the candidate and let the user surface it if they want it in scope.

## 4. Signature extraction

- For each class in scope, invoke the bundled extractor:
  ```bash
  <plugin-dir>/scripts/kotlin-signatures/bin/kotlin-signatures <path/to/Class.kt>
  ```
- Save each extract to `.claude/test-pharaoh/runs/<timestamp>/extracts/<mirror-path>/<Class>.kt`.
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

### AC decomposition before listing tests

For every AC, extract:

- **Parameters** (what varies across test cases): roles, entity types, flags, app / feature-flag variants, lifecycle states.
- **Paired verbs** (what the AC asserts can happen): words like "selected/deselected", "shown/hidden", "allowed/blocked", "enabled/disabled". An AC phrased as "X can be A-ed or B-ed" requires both an A-test and a B-test per parameter combination.

Build the matrix (parameters × verbs). **Every cell needs a planned test.** If a cell is genuinely redundant (code-path-identical to a sibling), do not omit it silently — record it in the plan as an explicit skipped-cell note with a one-line justification, e.g.:

> `- [~] user_UNLOADED_newApp_canSelect — skipped: same code path as user_UNLOADED_oldApp_canSelect (gate relies on 'isSelected()' branch, not the flag)`

The user reviews this at Gate 1. They, not the synthesizer, decide whether shared code paths justify fewer tests. Default to symmetric coverage when in doubt.

### Naming the tests

Apply these rules when naming every test you list in the plan. Test names are set *here*, not by mason — so get them right at synthesis time.

1. **Match the project's existing test-naming convention.** Before naming anything, read 2–3 existing test files in the target module. Common styles include backticked prose, snake_case, and camelCase. The architect's `project-profile.json` may record `conventions.testNamingStyle`; when present, use it — but still spot-check against existing tests in the target module, since conventions can vary between modules. Never invent a style that doesn't match the module.

2. **No drift-prone parentheticals.** A parenthetical that reads like one action sitting next to a test body asserting a different action is broken. Never let a parenthetical disagree with what the test asserts.

3. **Echo AC language literally — verbs *and* conditions, from the AC that asserts the behavior under test.** Copy the AC's wording verbatim for both the action verb *and* any condition attached to it. Do not paraphrase, generalize, or substitute phrasing from a sibling AC about UI text or labels. When multiple ACs use different phrasings, pick the AC that asserts the behavior, not a sibling AC about UI text. Naming drift breaks traceability.

4. **Prefer the pattern `<subject> can be <AC-verb>ed <condition>`** when the AC is phrased as a capability. Other sentence shapes are fine as long as rule 3 holds. If the project convention forbids prose (e.g. strict snake_case), adapt the pattern to that style while keeping the AC verbs + conditions as tokens.

5. **One test per matrix cell — no compression.** Do not collapse multiple matrix cells into one name. One test = one matrix cell; compression breaks traceability.

6. **Regression guards describe the guarded branch, not a forced capability phrase.** If the branch being guarded is an error, disabled, or invariant state, the test name asserts that state directly. Rule 4's pattern is for AC-driven capability tests; regression guards describe contract invariants and may need a different shape.

### Tier selection

Before listing tests, decide which tiers apply for each class in scope. Default tier selection:

- **unit**: always, when the class has behavior worth testing.
- **integration**: when the class orchestrates IO boundaries (DB, network, system services) and behavior depends on real collaborators at those boundaries.
- **roborazzi**: when the class is a Composable with visual variants worth regression-guarding (light/dark/RTL/font-scale/tablet).
- **instrumented** (the exact label comes from `project-profile.json → instrumentedTier.tierName` — e.g. `"cucumber"`, `"espresso"`, `"compose-ui"`): **treat as a decision point whenever the user story or any AC contains user-facing interaction signals.** You may propose to include or to omit, but you must surface the decision explicitly at Gate 1 as a clarification question — never make it silently. If `instrumentedTier.present = false` (project has no instrumented-test setup at all), surface that at Gate 1 too — interaction-driven ACs may not be testable end-to-end without introducing that setup, which is a scoping decision the user owns.

**User-facing interaction signals** — if any of the following appear in the story or ACs, you must explicitly ask the user about the instrumented tier at Gate 1:

- Human-actor phrasing: "Member", "User", "Customer", "Shopper", "As a <role>…"
- Interaction verbs: tap, click, select, deselect, choose, swipe, scroll, pull-to-refresh, navigate, enter, open, close, see, view, reveal, dismiss
- UI feedback elements: snackbar, toast, banner, dialog, screen, modal, bottom sheet
- App-state transitions: "navigates to", "opens", "closes", "reveals", "dismisses"

**Rationale**: unit tests verify pure data transformation. An AC phrased as a user capability (e.g. "Member can select voucher on expiry date") is an end-to-end behavioural claim — the underlying mapping passing does not prove the user can perform the action in the running app. Only the instrumented tier closes that gap. But whether to spend the time on instrumented scenarios is a project-specific judgement call the user owns.

**The Gate 1 clarification question is mandatory when interaction signals are present.** Phrase it along the lines of:

> The ACs include user-interaction signals: [list the specific signals you detected, quoted from the story/ACs]. I have [included / omitted] the instrumented tier in this plan because [one-line rationale]. Do you want me to [add / keep / remove] instrumented-tier coverage? If so, I propose the following scenarios: [list].

Never drop the instrumented tier without posing this question. The user decides.

### Listing the tests

**Write the plan to `.claude/test-pharaoh/runs/$TIMESTAMP/TEST_PLAN.md`.** The file is mandatory — presenting the plan inline in chat *only* is a workflow violation; the file must exist on disk before you reach Gate 1. Use the timestamp generated in the "Initialize run directory" step.

For each class in scope and each applicable tier (unit, integration, roborazzi, cucumber), list the planned tests with **traceability markers** in the file. Example format (the naming style shown here is one of several valid styles — use whatever matches the target module per the "Naming the tests" rules above):

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

## 7. GATE 1 — Plan review and implementation authorization

This gate has two distinct stages. **Do not conflate them.** Approval of the plan's *shape* is not authorization to *generate code*. You must obtain both, in sequence, before any mason is dispatched.

### 7a. Plan review

Tell the user the plan has been written and give them the path: `.claude/test-pharaoh/runs/<timestamp>/TEST_PLAN.md`. They review the file, edit it directly, or answer any clarification questions the plan raised. Iterate here as long as needed — plan correctness is cheaper to fix than generated tests.

Do NOT interpret generic approval ("looks good", "that's fine", "great", "sounds right", "nice") as authorization to implement. Those phrases confirm the plan is readable; they do not unlock generation. Implementation authorization is a separate, explicit step — see 7b.

### 7b. Implementation authorization (mandatory, standalone)

Once the user has finished reviewing the plan AND every clarification question from 7a has been resolved, you must pose Gate 1b as **its own standalone prompt** — not bundled with any other question, not implied, not combined with a clarification response. Exact wording doesn't matter, but it must be unmistakably a new, single-purpose question:

> The plan is finalized. Ready for me to generate these N tests? Reply "go" / "implement" / "proceed" to start. Anything else — including further plan edits — I'll treat as non-authorization.

**Hard rules:**

1. **Gate 1b stands alone.** Do NOT fold it into the same turn where you resolve a Cucumber / clarification / refactor question. Close those first. When every clarification is resolved, pose 7b by itself.

2. **Only a direct response to the Gate 1b prompt counts as authorization.** Answers to *earlier* questions — no matter how affirmative they sound (e.g. "yes add the Cucumber tests", "that's fine, proceed with the plan") — are NOT the go-signal. They're responses to those earlier questions, even if the user's prose sounds like consent. Re-ask 7b explicitly after any such response.

3. **Valid go-signals** (only when given in direct response to the 7b prompt): `go`, `implement`, `proceed`, `yes`, `generate`, `write the tests`, or any equally unambiguous directive to start generating code.

4. **Invalid — treat as non-authorization and re-pose 7b:**
   - Silence or a change of topic
   - Follow-up questions about the plan
   - Generic plan approvals: "looks good", "sounds right", "great plan"
   - Answers to clarification questions, even affirmative ones
   - Plan edits
   - Anything ambiguous

5. **Re-authorize on plan change.** If the plan is edited after a go-signal, the authorization expires. Pose 7b again before dispatching.

Under no circumstances may you dispatch a mason without a go-signal that was given *in direct response to a standalone 7b prompt*.

## 8. Generate

**Precondition**: 7b has been completed and the user has given an explicit go-signal. If 7b has not been completed or the signal was ambiguous, return to 7b — do not proceed.

For each `(class × tier)` in the authorized plan, dispatch a `mason` subagent in parallel where independent. Inputs to each:

- `signatureExtract`: path to the extract for this class.
- `tier`.
- `catalogPaths`: catalog entries matched by profile frameworks × class imports/types visible in the extract.
- `planExcerpt`: the relevant slice of `TEST_PLAN.md`.
- `projectProfilePath`.
- `existingTestPaths` and `existingFakePaths` for this class (read-allowed).

Aggregate the generator results. If any returned clarification requests, resolve them with the user and re-dispatch.

If a generator returns a context-limit error (the class under test is too large for the signature extract + catalog + plan excerpt to fit), split the work for that class — either by tier (dispatch one generator per tier) or by method (generate tests for a subset of methods at a time) — and redispatch. Do not skip the class silently.

## 9. Run and triage

**Run the complete module task for every tier touched by the generated tests — no `--tests` filter, no single-class shortcut.** The physician can only triage what it sees in `run.log`; partial coverage of the generated changes is a workflow failure, not a time-saver.

### 9a. Determine the task set

For every module that received new or modified test files in Step 8, enumerate one Gradle task per tier in the plan:

- **unit** → full module unit-test task (e.g. `:loyalty:testQaUnitTest`). Runs the whole test source set.
- **integration** → whatever the profile records for this module (commonly same task as unit).
- **roborazzi** → `:verifyRoborazziDebug` or profile-specified equivalent. Full run.
- **instrumented** (cucumber / espresso / compose-ui, per `profile.instrumentedTier.tierName`) → `:connectedXxxAndroidTest` if a device is available (see 9c); **regardless of device availability, also run the instrumented-source compile task** (e.g. `:loyalty:compileQaAndroidTestKotlin`) so compilation errors in the generated feature files / step definitions / dispatcher changes are caught.

**Prohibited:**
- `--tests "<fully-qualified.ClassName>"` or any filter that narrows below the module task. Always run the full module task.
- Skipping a tier because the most recent mason produced only a subset of files. The full module task sweeps everything, including side effects on pre-existing tests.

### 9b. Invoke and capture

Invoke each task (sequentially or in parallel where independent). **Every invocation must have its stdout + stderr appended to `.claude/test-pharaoh/runs/<timestamp>/run.log`.** If you cannot write to `run.log`, stop and escalate — running tests without persisting output is itself a workflow violation.

Apply a per-Gradle-task timeout (default 5 minutes). If a task hangs past the timeout, kill it, append a `TIMEOUT <task>` line to `run.log`, and escalate directly to Gate 2 — do not retry hung tasks.

### 9c. Instrumented-tier device check

Before invoking `connectedXxxAndroidTest`, run `adb devices` and append its output to `run.log`. If no device/emulator is connected:

- Still run the instrumented-source compile task (per 9a).
- Record the decision in `run.log` with a line like `SKIPPED :loyalty:connectedQaAndroidTest — no device (compile-only verified)`.
- Surface the situation in the final Completion summary — do not silently skip.

### 9d. Dispatch physician on any failure

If any task produced failures (compilation errors, test failures, setup errors), dispatch `physician` with `run.log` and the full list of generated test files.

- **Mechanical** results → apply the returned patches and re-run the FULL module task(s), not just the failing class. Retry limit: ≤2 retries per failing test.
- **Loop guard**: if the same test fails with the same signature after a patch, escalate without retrying.
- **Substantive** results → Gate 2.
- If a mechanical patch conflicts with a user edit → Gate 2 with the patch + conflict details.

### 9e. Coverage verification before Completion

Before marking Step 9 complete, verify `run.log` contains a result row (pass, fail, skipped-with-reason, or not-executed-with-reason) for every test listed in `TEST_PLAN.md`. If any planned test has no corresponding row, the run is incomplete — return to 9a, extend the task set, and re-run. Missing coverage may not be swept under the rug; it must be either executed or explicitly recorded in `run.log` with a reason before you advance to Step 11.

## 10. GATE 2 — Escalation (on persistent failure)

Write `diagnosis.md` under `.claude/test-pharaoh/runs/<timestamp>/` containing:

- Summary: `N passed / M failed / K skipped`.
- Per failed test: file path, failure type, most-likely cause, suggested fix path.

Tell the user the run is stopped and point them at the diagnosis. The run directory retains `TEST_PLAN.md`, generated files, `run.log`, and the diagnosis — they can resume manually.

## 11. Completion

On clean run, print the summary, commit the generated tests on behalf of the user *only if they explicitly ask*, and stop.
