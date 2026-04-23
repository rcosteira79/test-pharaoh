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
