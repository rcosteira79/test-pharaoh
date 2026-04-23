# android-test-agent

Claude Code plugin that writes tests for code introduced on an Android branch, driven by the branch's user story and acceptance criteria.

## Status

v0.x — pre-release. See `docs/superpowers/specs/2026-04-21-android-test-agent-design.md` for the design.

## Install (local, for development)

Place the plugin under `~/.claude/plugins/android-test-agent/`.

## Command

`/write-tests` — run inside an Android project's branch. Paste the user story + ACs when prompted.
