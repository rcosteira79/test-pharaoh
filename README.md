# android-test-agent

Claude Code plugin that writes tests for code introduced on an Android branch, driven by the branch's user story + ACs. Adheres to the project's existing conventions. Never generates mock-based tests.

## Install (development)

```bash
mkdir -p ~/.claude/plugins
ln -s "$(pwd)" ~/.claude/plugins/android-test-agent
```

Then, in the `kotlin-signatures` tool, build the jar:

```bash
(cd scripts/kotlin-signatures && ./gradlew shadowJar)
```

## Usage

In any Android project (Kotlin + Gradle + version catalog):

1. Check out a branch with your changes.
2. Run `/write-tests` in Claude Code.
3. Paste the user story + acceptance criteria.
4. Answer Gate 0 (feature understanding), review the plan at Gate 1, and handle Gate 2 if any failures persist.

## Uninstall

```bash
rm ~/.claude/plugins/android-test-agent
```
