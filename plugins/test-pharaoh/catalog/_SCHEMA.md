# Catalog Entry Schema

Every catalog file (`catalog/<framework>.md`) MUST have these sections in this order:

## Purpose

One paragraph describing the pattern this file covers (e.g., "HTTP client abstraction via Retrofit service interfaces").

## Detection rule

A bulleted list of version-catalog TOML aliases that activate this entry. Example for `retrofit.md`:

- `retrofit`
- `retrofit-converter-gson`
- `retrofit-adapter-rxjava2`

## Stock edge cases

A bulleted list of scenarios that SHOULD be tested whenever this pattern is present, regardless of business logic. Each entry: `- **<label>** — <one-sentence description>`.

## Assertion patterns

One or more fenced Kotlin snippets showing how stock edge cases are typically asserted in an Android project.

## Gotchas

Framework-specific pitfalls a test author should know.

---

Filename rule: `<framework>.md` where the framework matches a TOML alias or a stable concept name (e.g., `viewmodel-stateflow.md`).
