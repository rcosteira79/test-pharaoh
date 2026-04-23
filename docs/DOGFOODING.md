# Dogfooding `/write-tests`

Before declaring v1 ready, run `/write-tests` against a small real feature branch in `~/Secure/groceries-android` (or another real Android project) and confirm:

- [ ] Discovery correctly populates `project-profile.json`.
  - Frameworks detected match the version catalog.
  - `testInstrumentationRunner` per module is captured.
  - Fake location under the existing test infrastructure is found.
  - MockK presence is surfaced as a refactor proposal.
- [ ] Gate 0 summary genuinely reflects the feature.
- [ ] `TEST_PLAN.md` entries all have traceability markers.
- [ ] Generated tests live in the right source set, follow existing naming, use existing fakes, do NOT use mocks.
- [ ] MockWebServer fixtures for Cucumber tier land in the project's existing fixture location.
- [ ] `./gradlew <module>:<task>` runs to green (or fails with clear mechanical/substantive triage).
- [ ] Any gaps → file as backlog items under Section 8 of the spec.
