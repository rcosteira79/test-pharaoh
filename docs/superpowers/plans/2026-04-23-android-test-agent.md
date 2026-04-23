# Android Test Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Claude Code plugin (`android-test-agent`) that writes tests for changed Android code using the branch's user story and ACs as an independent specification, with the code under test exposed only through a signatures-only extract.

**Architecture:** Plugin bundles a main-session orchestrator skill (`/write-tests`), three subagents (`project-discoverer`, `test-generator`, `failure-analyst`), a bundled Kotlin/JVM CLI that strips bodies from `.kt` files, markdown catalog entries per framework, and sample fixtures. Per-project runtime state lives under `.claude/android-test-agent/` in the target repo.

**Tech Stack:** Claude Code plugin format (package.json + skills/, agents/, scripts/, hooks/, catalog/), Kotlin/Gradle JVM CLI (tree-sitter-kotlin Java bindings, Shadow plugin for fat jar), JUnit5 for extractor tests, Bash for wrappers, Python (or Bash) for the catalog linter.

**Reference spec:** `docs/superpowers/specs/2026-04-21-android-test-agent-design.md`.

---

## Phase 0 — Plugin skeleton

### Task 0.1: Plugin manifest and README

**Files:**
- Create: `package.json`
- Create: `README.md`
- Create: `.gitignore`

- [ ] **Step 1: Create `package.json`**

```json
{
  "name": "android-test-agent",
  "version": "0.1.0",
  "description": "Writes tests for Android branches from user stories and ACs, with code-under-test exposed only as signatures.",
  "type": "module"
}
```

- [ ] **Step 2: Create minimal `README.md`**

```markdown
# android-test-agent

Claude Code plugin that writes tests for code introduced on an Android branch, driven by the branch's user story and acceptance criteria.

## Status

v0.x — pre-release. See `docs/superpowers/specs/2026-04-21-android-test-agent-design.md` for the design.

## Install (local, for development)

Place the plugin under `~/.claude/plugins/android-test-agent/`.

## Command

`/write-tests` — run inside an Android project's branch. Paste the user story + ACs when prompted.
```

- [ ] **Step 3: Create `.gitignore`**

```
# Build outputs
scripts/kotlin-signatures/build/
scripts/kotlin-signatures/.gradle/

# Generated fat jar in final location (keep only the committed release jar)
# (comment out once we decide what to commit)

# Worktrees
.worktrees/

# OS noise
.DS_Store
```

- [ ] **Step 4: Commit**

```bash
git add package.json README.md .gitignore
git commit -m "feat: plugin skeleton (manifest + README + gitignore)"
```

---

### Task 0.2: Directory skeleton for plugin structure

**Files:**
- Create: empty directories with `.gitkeep` where needed

- [ ] **Step 1: Create directory structure**

```bash
mkdir -p skills/write-tests
mkdir -p agents
mkdir -p scripts/kotlin-signatures
mkdir -p catalog
mkdir -p hooks
mkdir -p tests/fixtures
touch skills/write-tests/.gitkeep \
      agents/.gitkeep \
      scripts/kotlin-signatures/.gitkeep \
      catalog/.gitkeep \
      hooks/.gitkeep \
      tests/fixtures/.gitkeep
```

- [ ] **Step 2: Commit**

```bash
git add skills agents scripts catalog hooks tests
git commit -m "chore: create plugin directory skeleton"
```

---

## Phase 1 — `kotlin-signatures` JVM CLI

This phase builds a standalone Kotlin/JVM CLI that takes a `.kt` file path and emits a signatures-only version (no bodies, no initializers, no init blocks).

**Parsing library**: `kotlin-compiler-embeddable` PSI. (The design spec originally listed tree-sitter-kotlin Java bindings as primary with PSI as fallback; during execution we confirmed no stable tree-sitter-kotlin artifact exists on Maven Central under the expected coordinates. `io.github.bonede:tree-sitter4j` exists but is a small third-party wrapper with sustainability risk. `kotlin-compiler-embeddable` is the official Kotlin-team path and the spec's approved fallback — see §4.5 of the design spec.)

**Implementation note for Tasks 1.2–1.8**: the code snippets below still show a tree-sitter-style visitor *for illustration of intent*. The real implementation uses the PSI API — `KtFile`, `KtNamedFunction`, `KtProperty`, `KtClassInitializer`, `KtParameter` — via `KotlinCoreEnvironment` + `PsiFileFactory`. Test assertions and task structure are unchanged; only the parser internals differ. Each task's implementer translates the intent to the appropriate PSI visitor.

### Task 1.1: Gradle project for the CLI

**Files:**
- Create: `scripts/kotlin-signatures/build.gradle.kts`
- Create: `scripts/kotlin-signatures/settings.gradle.kts`
- Create: `scripts/kotlin-signatures/gradle.properties`
- Create: `scripts/kotlin-signatures/src/main/kotlin/com/androidtestagent/signatures/Main.kt`
- Create: `scripts/kotlin-signatures/src/test/kotlin/com/androidtestagent/signatures/MainTest.kt`
- Create: `scripts/kotlin-signatures/README.md`

- [ ] **Step 1: Install Gradle wrapper**

Run:
```bash
cd scripts/kotlin-signatures
gradle wrapper --gradle-version 8.10
```

Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/`.

- [ ] **Step 2: Create `settings.gradle.kts`**

```kotlin
rootProject.name = "kotlin-signatures"
```

- [ ] **Step 3: Create `gradle.properties`**

```
kotlin.code.style=official
org.gradle.jvmargs=-Xmx1g
```

- [ ] **Step 4: Create `build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.androidtestagent"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin compiler embeddable — gives us PSI to parse .kt files.
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.androidtestagent.signatures.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("kotlin-signatures")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}
```

- [ ] **Step 5: Create stub `Main.kt`**

```kotlin
package com.androidtestagent.signatures

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage: kotlin-signatures <path-to-kotlin-file>")
        kotlin.system.exitProcess(2)
    }
    val input = java.io.File(args[0])
    if (!input.exists()) {
        System.err.println("error: file not found: ${args[0]}")
        kotlin.system.exitProcess(1)
    }
    val source = input.readText()
    val output = stripBodies(source)
    print(output)
}

fun stripBodies(source: String): String {
    // Placeholder: implemented across later tasks.
    return source
}
```

- [ ] **Step 6: Create failing smoke test**

In `scripts/kotlin-signatures/src/test/kotlin/com/androidtestagent/signatures/MainTest.kt`:

```kotlin
package com.androidtestagent.signatures

import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun `smoke: empty input returns empty string`() {
        assertEquals("", stripBodies(""))
    }
}
```

- [ ] **Step 7: Run tests to verify passing setup**

```bash
cd scripts/kotlin-signatures
./gradlew test --info
```

Expected: `BUILD SUCCESSFUL`, smoke test passes.

- [ ] **Step 8: Create `scripts/kotlin-signatures/README.md`**

```markdown
# kotlin-signatures

Strips function bodies, property initializers, and `init {}` blocks from Kotlin source files, emitting a signatures-only view.

## Build

```bash
./gradlew shadowJar
```

Output: `build/libs/kotlin-signatures.jar`

## Run

```bash
java -jar kotlin-signatures.jar path/to/File.kt
```

## Fallback

If tree-sitter-kotlin bindings prove unreliable, swap to `kotlin-compiler-embeddable` PSI (heavier dep, no JNI).
```

- [ ] **Step 9: Commit**

```bash
git add scripts/kotlin-signatures
git commit -m "feat(signatures): gradle project + stub CLI with smoke test"
```

---

### Task 1.2: Strip function bodies from top-level functions

**Files:**
- Modify: `scripts/kotlin-signatures/src/main/kotlin/com/androidtestagent/signatures/Main.kt`
- Modify: `scripts/kotlin-signatures/src/test/kotlin/com/androidtestagent/signatures/MainTest.kt`

- [ ] **Step 1: Write failing test**

Add to `MainTest.kt`:

```kotlin
    @Test
    fun `top-level function body is stripped`() {
        val input = """
            fun add(a: Int, b: Int): Int {
                return a + b
            }
        """.trimIndent()

        val output = stripBodies(input)

        // Signature retained, body replaced with `= TODO()` or dropped
        assertTrue(output.contains("fun add(a: Int, b: Int): Int"))
        assertFalse(output.contains("return a + b"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test
```

Expected: `top-level function body is stripped` FAILS.

- [ ] **Step 3: Implement using tree-sitter**

Replace `stripBodies` in `Main.kt`:

```kotlin
package com.androidtestagent.signatures

import io.github.treesitter.ktreesitter.Language
import io.github.treesitter.ktreesitter.Parser

private val kotlinLanguage: Language by lazy {
    // Load packaged tree-sitter-kotlin grammar.
    Language("kotlin")
}

fun stripBodies(source: String): String {
    if (source.isBlank()) return ""
    val parser = Parser(kotlinLanguage)
    val tree = parser.parse(source)
    val root = tree.rootNode
    val edits = mutableListOf<IntRange>()

    fun visit(node: io.github.treesitter.ktreesitter.Node) {
        if (node.type == "function_body") {
            edits += node.startByte..node.endByte
        }
        for (i in 0 until node.childCount) visit(node.child(i)!!)
    }
    visit(root)

    // Apply deletions from end to start to preserve offsets.
    val bytes = source.toByteArray(Charsets.UTF_8).toMutableList()
    for (range in edits.sortedByDescending { it.first }) {
        repeat(range.last - range.first) { bytes.removeAt(range.first) }
    }
    return String(bytes.toByteArray(), Charsets.UTF_8)
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew test
```

Expected: both tests pass.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat(signatures): strip top-level function bodies"
```

---

### Task 1.3: Strip member function bodies (class/object/interface)

**Files:**
- Modify: `scripts/kotlin-signatures/src/test/kotlin/com/androidtestagent/signatures/MainTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
    @Test
    fun `member function body is stripped`() {
        val input = """
            class Greeter {
                fun hello(name: String): String {
                    return "Hello, ${'$'}name"
                }
            }
        """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("class Greeter"))
        assertTrue(output.contains("fun hello(name: String): String"))
        assertFalse(output.contains("return \"Hello"))
    }
```

- [ ] **Step 2: Run test to verify it passes**

The tree-sitter rule `function_body` matches both top-level and member functions, so this test should already pass.

```bash
./gradlew test
```

Expected: all tests pass. If it fails, the tree-sitter grammar may use a different node type for member bodies — adjust the visitor to also match `class_body`-nested `function_body`.

- [ ] **Step 3: Commit**

```bash
git add .
git commit -m "test(signatures): cover class member function bodies"
```

---

### Task 1.4: Strip property initializers

**Files:**
- Modify: `scripts/kotlin-signatures/src/main/kotlin/com/androidtestagent/signatures/Main.kt`
- Modify: `scripts/kotlin-signatures/src/test/kotlin/com/androidtestagent/signatures/MainTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
    @Test
    fun `property initializer is stripped`() {
        val input = """
            class Config {
                val endpoint: String = "https://api.example.com"
                var retries: Int = 3
            }
        """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("val endpoint: String"))
        assertTrue(output.contains("var retries: Int"))
        assertFalse(output.contains("https://api.example.com"))
        assertFalse(output.contains("= 3"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test
```

Expected: property initializer test FAILS.

- [ ] **Step 3: Extend `stripBodies` to handle property initializers**

Modify the visitor in `Main.kt`:

```kotlin
fun visit(node: Node) {
    when (node.type) {
        "function_body" -> edits += node.startByte..node.endByte
        "property_declaration" -> {
            // Find child `=` and the initializer that follows
            for (i in 0 until node.childCount) {
                val child = node.child(i)!!
                if (child.type == "=" && i + 1 < node.childCount) {
                    val initializer = node.child(i + 1)!!
                    edits += child.startByte..initializer.endByte
                    break
                }
            }
        }
    }
    for (i in 0 until node.childCount) visit(node.child(i)!!)
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat(signatures): strip property initializers"
```

---

### Task 1.4a: Strip delegated-property delegate bodies

**Files:**
- Modify: `scripts/kotlin-signatures/src/main/kotlin/com/androidtestagent/signatures/StripBodies.kt`
- Modify: `scripts/kotlin-signatures/src/test/kotlin/com/androidtestagent/signatures/MainTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `delegated property delegate body is stripped`() {
    val input = """
        class Service {
            val logger by lazy { Logger("svc") }
            val cache: Cache by Delegates.observable(Cache.empty()) { _, _, _ -> Unit }
        }
    """.trimIndent()

    val output = stripBodies(input)

    assertTrue(output.contains("val logger"))
    assertTrue(output.contains("val cache: Cache"))
    assertFalse(output.contains("Logger(\"svc\")"))
    assertFalse(output.contains("Delegates.observable"))
}
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Extend `visitProperty`** to also handle the delegate case. `KtProperty.delegate` is a `KtPropertyDelegate` containing `byKeywordNode` + the delegate expression. Strip the range from `byKeywordNode.startOffset` through `delegate.textRange.endOffset` (or preserve the `by` keyword and strip just the expression — your call, but don't leak the body).

- [ ] **Step 4: Run tests, all green**

- [ ] **Step 5: Commit**: `feat(signatures): strip delegated-property delegates`

---

### Task 1.4b: Strip property accessor bodies

**Files:**
- Modify: `scripts/kotlin-signatures/src/main/kotlin/com/androidtestagent/signatures/StripBodies.kt`
- Modify: `scripts/kotlin-signatures/src/test/kotlin/com/androidtestagent/signatures/MainTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `property accessor bodies are stripped`() {
    val input = """
        class User(private val first: String, private val last: String) {
            val full: String
                get() = "${'$'}first ${'$'}last"
            var name: String = ""
                set(value) {
                    field = value.trim()
                }
        }
    """.trimIndent()

    val output = stripBodies(input)

    assertTrue(output.contains("val full: String"))
    assertTrue(output.contains("var name: String"))
    assertFalse(output.contains("\"${'$'}first ${'$'}last\""))
    assertFalse(output.contains("value.trim()"))
}
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Override `visitPropertyAccessor`** in `StripBodies.kt` — for each `KtPropertyAccessor`, strip its `bodyExpression` range (handles both `{ ... }` and `= expr` forms, mirroring the function case).

- [ ] **Step 4: Run tests, all green**

- [ ] **Step 5: Commit**: `feat(signatures): strip property accessor bodies`

---

### Task 1.5: Strip `init {}` blocks

**Files:**
- Modify: `scripts/kotlin-signatures/src/main/kotlin/com/androidtestagent/signatures/Main.kt`
- Modify: `scripts/kotlin-signatures/src/test/kotlin/com/androidtestagent/signatures/MainTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
    @Test
    fun `init blocks are removed entirely`() {
        val input = """
            class Service {
                private val id = 0
                init {
                    println("booting")
                }
                fun serve() {}
            }
        """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("class Service"))
        assertTrue(output.contains("fun serve()"))
        assertFalse(output.contains("init"))
        assertFalse(output.contains("println"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test
```

Expected: `init blocks are removed entirely` FAILS.

- [ ] **Step 3: Add `anonymous_initializer` handling to visitor**

In the `when` block:

```kotlin
"anonymous_initializer", "class_initializer" -> {
    edits += node.startByte..node.endByte
}
```

(Check the grammar — the correct node type is typically `anonymous_initializer`. If the test still fails, inspect the tree with `println(root.sexp())` and use the actual type.)

- [ ] **Step 4: Run tests**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat(signatures): drop init blocks"
```

---

### Task 1.6: Preserve sealed-class branches and nested types

**Files:**
- Modify: `scripts/kotlin-signatures/src/test/kotlin/com/androidtestagent/signatures/MainTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
    @Test
    fun `sealed class branches survive`() {
        val input = """
            sealed class LoginError {
                data object InvalidCredentials : LoginError()
                data class Throttled(val retryAfter: Long) : LoginError()
                data object Network : LoginError()
            }
        """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("sealed class LoginError"))
        assertTrue(output.contains("InvalidCredentials : LoginError()"))
        assertTrue(output.contains("data class Throttled(val retryAfter: Long)"))
        assertTrue(output.contains("Network : LoginError()"))
    }
```

- [ ] **Step 2: Run test**

Sealed-class branches are just class/object declarations at class-body level, so no new logic is needed. Run to confirm:

```bash
./gradlew test
```

Expected: passes. If it fails, diagnose which node type is being over-eagerly stripped.

- [ ] **Step 3: Commit**

```bash
git add .
git commit -m "test(signatures): cover sealed-class branches"
```

---

### Task 1.7: Preserve KDoc, annotations, generics, and default-parameter *presence*

**Files:**
- Modify: `scripts/kotlin-signatures/src/main/kotlin/com/androidtestagent/signatures/Main.kt`
- Modify: `scripts/kotlin-signatures/src/test/kotlin/com/androidtestagent/signatures/MainTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
    @Test
    fun `kdoc, annotations, generics preserved; default values dropped`() {
        val input = """
            /** Fetches a user. */
            @Deprecated("use v2")
            fun <T : Any> fetch(
                id: String,
                cache: Cache<T> = DefaultCache()
            ): Result<T> {
                return Result.success(default)
            }
        """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("/** Fetches a user. */"))
        assertTrue(output.contains("@Deprecated(\"use v2\")"))
        assertTrue(output.contains("fun <T : Any> fetch"))
        assertTrue(output.contains("cache: Cache<T>"))
        // Default-parameter marker retained (either the `= …` stripped entirely or replaced with `/* default */`).
        assertFalse(output.contains("DefaultCache()"))
    }
```

- [ ] **Step 2: Run test to verify default-value stripping is missing**

```bash
./gradlew test
```

Expected: fails on default-value preservation (the visitor currently doesn't handle parameter defaults).

- [ ] **Step 3: Extend visitor to strip parameter default values**

In the `when` block:

```kotlin
"parameter" -> {
    // Drop default-value expression if present: `param: Type = expr` → `param: Type`.
    for (i in 0 until node.childCount) {
        val child = node.child(i)!!
        if (child.type == "=" && i + 1 < node.childCount) {
            val expr = node.child(i + 1)!!
            edits += child.startByte..expr.endByte
            break
        }
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew test
```

Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat(signatures): strip parameter default values (signature presence preserved)"
```

---

### Task 1.8: Handle single-expression functions (no body block)

**Files:**
- Modify: `scripts/kotlin-signatures/src/main/kotlin/com/androidtestagent/signatures/Main.kt`
- Modify: `scripts/kotlin-signatures/src/test/kotlin/com/androidtestagent/signatures/MainTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
    @Test
    fun `single-expression function body is stripped`() {
        val input = """
            fun double(x: Int) = x * 2
            fun <T> id(t: T): T = t
        """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("fun double(x: Int)"))
        assertTrue(output.contains("fun <T> id(t: T): T"))
        assertFalse(output.contains("x * 2"))
        assertFalse(output.contains("= t"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test
```

Expected: fails (single-expression bodies aren't `function_body` nodes in the grammar).

- [ ] **Step 3: Extend visitor for single-expression bodies**

In the `when`:

```kotlin
"function_declaration" -> {
    for (i in 0 until node.childCount) {
        val child = node.child(i)!!
        if (child.type == "=" && i + 1 < node.childCount) {
            val expr = node.child(i + 1)!!
            edits += child.startByte..expr.endByte
            break
        }
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew test
```

Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat(signatures): strip single-expression function bodies"
```

---

### Task 1.9: Error path — non-Kotlin and missing files

**Files:**
- Modify: `scripts/kotlin-signatures/src/main/kotlin/com/androidtestagent/signatures/Main.kt`
- Modify: `scripts/kotlin-signatures/src/test/kotlin/com/androidtestagent/signatures/MainTest.kt`

- [ ] **Step 1: Write failing tests**

Add a new test file `IntegrationTest.kt` that invokes `main()` via a helper:

```kotlin
package com.androidtestagent.signatures

import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class IntegrationTest {
    private fun runMain(args: Array<String>): Pair<Int, String> {
        val err = ByteArrayOutputStream()
        val prevErr = System.err
        System.setErr(PrintStream(err))
        var code = 0
        try {
            main(args)
        } catch (e: ExitException) {
            code = e.code
        } finally {
            System.setErr(prevErr)
        }
        return code to err.toString()
    }

    @Test
    fun `exits 2 when no args`() {
        val (code, err) = runMain(emptyArray())
        assertEquals(2, code)
        assert(err.contains("usage:"))
    }

    @Test
    fun `exits 1 when file missing`() {
        val (code, err) = runMain(arrayOf("/nonexistent/file.kt"))
        assertEquals(1, code)
        assert(err.contains("file not found"))
    }
}

class ExitException(val code: Int) : RuntimeException()
```

Replace `kotlin.system.exitProcess(…)` with `throw ExitException(…)` in `main()` for testability.

- [ ] **Step 2: Run tests**

```bash
./gradlew test
```

Expected: both new tests pass after the exit refactor.

- [ ] **Step 3: Commit**

```bash
git add .
git commit -m "test(signatures): cover missing-file and no-args error paths"
```

---

### Task 1.10: Build fat jar and place it under the plugin

**Files:**
- Modify: `scripts/kotlin-signatures/build.gradle.kts` (verify shadow task)
- Create: `scripts/kotlin-signatures/bin/kotlin-signatures` (wrapper)

- [ ] **Step 1: Build the fat jar**

```bash
cd scripts/kotlin-signatures
./gradlew clean shadowJar
```

Expected: `build/libs/kotlin-signatures.jar` exists.

- [ ] **Step 2: Create wrapper script `bin/kotlin-signatures`**

```bash
mkdir -p bin
cat > bin/kotlin-signatures <<'SH'
#!/usr/bin/env bash
set -euo pipefail
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
JAR="$DIR/../build/libs/kotlin-signatures.jar"
if [[ ! -f "$JAR" ]]; then
  echo "error: $JAR not found — run ./gradlew shadowJar first" >&2
  exit 1
fi
exec java -jar "$JAR" "$@"
SH
chmod +x bin/kotlin-signatures
```

- [ ] **Step 3: Smoke-test wrapper against a fixture file**

```bash
cat > /tmp/sample.kt <<'KT'
class Foo {
    fun bar(x: Int): Int {
        return x + 1
    }
}
KT
bin/kotlin-signatures /tmp/sample.kt
```

Expected output contains `class Foo` and `fun bar(x: Int): Int` but not `return x + 1`.

- [ ] **Step 4: Commit**

```bash
git add scripts/kotlin-signatures/bin
# NOTE: we do NOT commit the built jar; consumers build it via `./gradlew shadowJar`.
git commit -m "feat(signatures): CLI wrapper script; verified against fixture"
```

---

## Phase 2 — Catalog

Catalog entries are markdown documents describing stock edge-case tests per framework. The schema is fixed; linter enforces it.

### Task 2.1: Catalog entry schema document

**Files:**
- Create: `catalog/_SCHEMA.md`

- [ ] **Step 1: Write the schema doc**

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add catalog/_SCHEMA.md
git commit -m "docs(catalog): entry schema"
```

---

### Task 2.2: Catalog linter

**Files:**
- Create: `scripts/catalog-lint/catalog_lint.py`
- Create: `scripts/catalog-lint/README.md`

- [ ] **Step 1: Write the linter**

```python
#!/usr/bin/env python3
"""
Validates every catalog/*.md against _SCHEMA.md:
- has required sections in order
- no empty sections
- no broken intra-repo links
Exits 0 on success, 1 on any failure (with per-file diagnostics).
"""
import sys, re, pathlib

REQUIRED_SECTIONS = ["Purpose", "Detection rule", "Stock edge cases", "Assertion patterns", "Gotchas"]

def lint_file(path: pathlib.Path) -> list[str]:
    errors = []
    text = path.read_text()
    headings = re.findall(r"^## (.+)$", text, re.MULTILINE)
    if headings != REQUIRED_SECTIONS:
        errors.append(f"{path}: headings {headings} != required {REQUIRED_SECTIONS}")
    # Empty section check
    for name in REQUIRED_SECTIONS:
        pattern = rf"^## {re.escape(name)}\s*\n+(?=^## |\Z)"
        if re.search(pattern, text, re.MULTILINE):
            errors.append(f"{path}: section '{name}' is empty")
    return errors

def main() -> int:
    root = pathlib.Path(__file__).resolve().parents[2]
    catalog_dir = root / "catalog"
    errors = []
    for path in sorted(catalog_dir.glob("*.md")):
        if path.name.startswith("_"):  # skip _SCHEMA.md etc
            continue
        errors.extend(lint_file(path))
    if errors:
        for e in errors:
            print(e, file=sys.stderr)
        return 1
    print(f"catalog-lint: OK ({len(list(catalog_dir.glob('*.md')))} files)")
    return 0

if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: Create README**

```markdown
# catalog-lint

Validates `catalog/*.md` entries against `catalog/_SCHEMA.md`.

## Run

```bash
python3 scripts/catalog-lint/catalog_lint.py
```

Exit 0 on pass, 1 on fail (with per-file error output).
```

- [ ] **Step 3: Make it executable and run against current (empty) catalog**

```bash
chmod +x scripts/catalog-lint/catalog_lint.py
python3 scripts/catalog-lint/catalog_lint.py
```

Expected: passes (no `.md` files other than `_SCHEMA.md`).

- [ ] **Step 4: Commit**

```bash
git add scripts/catalog-lint
git commit -m "feat(catalog-lint): structural validator for catalog entries"
```

---

### Task 2.3 – 2.14: One catalog entry per framework

For each framework in the v1 set, author a `catalog/<framework>.md` following `_SCHEMA.md`. Commit each file separately so the linter runs incrementally.

Frameworks (in this order, one task each):

2.3 `retrofit.md`
2.4 `apollo.md`
2.5 `room.md`
2.6 `datastore.md`
2.7 `coroutines-flow.md`
2.8 `rxjava2.md`
2.9 `hilt.md`
2.10 `compose-ui.md`
2.11 `nav3.md`
2.12 `roborazzi.md`
2.13 `viewmodel-stateflow.md`
2.14 `workmanager.md`

**Per-entry task template:**

**Files:**
- Create: `catalog/<framework>.md`

- [ ] **Step 1: Draft the entry following `_SCHEMA.md`**

Each entry MUST include:
- **Purpose** — pattern description.
- **Detection rule** — TOML aliases from `libs.versions.toml`.
- **Stock edge cases** — deterministic scenarios applicable regardless of business logic.
- **Assertion patterns** — one or more Kotlin snippets using the project's assertion libraries (Google Truth + JUnit 5 as seen in `groceries-android`).
- **Gotchas** — framework-specific pitfalls.

**Minimum stock edge-case coverage** (starting point — add more as you know the framework):

| Framework | Stock edge cases |
|---|---|
| retrofit | success 2xx, 4xx client error, 5xx server error, network failure, malformed body, timeout |
| apollo | successful query, `errors` payload, network failure, partial data, cache miss/hit |
| room | insert, update, delete, query-empty, migration-survival (read-only), unique-constraint violation |
| datastore | initial read (missing key), write-then-read, concurrent writes, corrupted file |
| coroutines-flow | initial value, subsequent emissions, cancellation propagation, error in operator, completion |
| rxjava2 | onSuccess, onError, onComplete, disposal, thread confinement |
| hilt | binding resolution, module swap in tests, scoped-vs-unscoped lifecycle |
| compose-ui | initial render, state change re-render, user interaction fires intent, recomposition guard |
| nav3 | navigate-to, back, args passing, deep link, conditional navigation gate |
| roborazzi | light mode baseline, dark mode, RTL, large font, large screen — one per variant |
| viewmodel-stateflow | initial state, state transition on event, configuration change survival, scope cancellation |
| workmanager | enqueue, success, retry-on-failure, cancellation, constraints not met |

- [ ] **Step 2: Run the linter**

```bash
python3 scripts/catalog-lint/catalog_lint.py
```

Expected: passes.

- [ ] **Step 3: Commit**

```bash
git add catalog/<framework>.md
git commit -m "feat(catalog): <framework> entry"
```

---

## Phase 3 — Subagents

Each subagent is a markdown file with YAML frontmatter (`name`, `description`, `model: inherit`) and a prompt body specifying its role, inputs, outputs, and constraints.

### Task 3.1: `project-discoverer` subagent

**Files:**
- Create: `agents/project-discoverer.md`

- [ ] **Step 1: Write the agent definition**

```markdown
---
name: project-discoverer
description: |
  Discovers an Android project's profile: frameworks (from libs.versions.toml),
  per-module test-runner configs, fake infrastructure, mock usage, fixture
  conventions, and error-wrapper conventions. Writes results to
  .claude/android-test-agent/project-profile.json.

  Invoked by the /write-tests orchestrator when no profile exists or the
  version-catalog hash has changed.
model: inherit
---

You are the project-discoverer subagent for the android-test-agent plugin. Your role is to build a JSON profile of the target Android project so downstream test generation can adhere to the project's existing conventions.

## Inputs (provided by orchestrator)

- Absolute path to the target repo root.
- Path to the catalog directory (for detection-rule lookup).

## Responsibilities

1. Parse `gradle/libs.versions.toml` if present; otherwise scan each module's `build.gradle.kts`.
2. Detect frameworks by matching TOML aliases to catalog filenames (open each catalog file's `Detection rule` section).
3. For each module: scan `build.gradle.kts` for `testInstrumentationRunner`; scan `src/androidTest/AndroidManifest.xml` for `<instrumentation>` entries. Capture the known test tasks (`:test`, `:connectedDebugAndroidTest`, Roborazzi's `:verifyRoborazziDebug`, etc.).
4. Locate existing fake infrastructure: classes matching `Fake*`/`*Fake` under `*/src/test*/` and `*/src/androidTest/`. Also check modules whose name contains `test`/`testing`/`testutils`. "No existing fake infrastructure" is a valid state and must be recorded as `fakeLocation: null`.
5. Detect mock-library usage (MockK, Mockito) across test source sets. Record for the refactor-proposal step.
6. Detect error-wrapper conventions per module: `Result<…>` vs `Box<…>` vs other (scan for wrapper types in `src/main/kotlin/`).
7. Detect test-fixture conventions: directory paths, sub-directory organization, file naming, formats.
8. Detect Cucumber end-to-end setup if cucumber-android / cucumber-android-hilt present: MockWebServer fixture location, hook classes, response-fixture format, base test class.
9. Read `AGENTS.md` if present at repo root; capture notable conventions as a string list.
10. Hash `libs.versions.toml` (SHA-256 hex) and write the profile.

## Output

Write JSON to `.claude/android-test-agent/project-profile.json` with schema:

```json
{
  "versionCatalogHash": "sha256-hex",
  "frameworks": { "<name>": { "version": "<v>", "moduleAliases": ["…"] } },
  "modules": {
    "<module>": {
      "instrumentationRunner": "<class>",
      "testTasks": { "unit": ":…", "roborazzi": ":…", "instrumented": ":…" },
      "errorWrapper": "Result | Box | …",
      "fakeLocation": "<relative path | null>"
    }
  },
  "conventions": {
    "mockLibrary": "mockk | mockito | null",
    "dispatcher": "<how coroutines dispatchers are provided>",
    "fixtureRoots": { "<tier>": "<path>" }
  },
  "cucumber": {
    "present": true,
    "mockWebServerFixtures": "<path>",
    "hookClasses": ["…"],
    "baseTestClass": "<class>",
    "responseFormat": "json | … "
  },
  "notesFromAgentsMd": ["…"]
}
```

## Constraints

- Do NOT read production Kotlin class bodies — only build scripts, manifests, and existing *tests*.
- Do NOT guess when data is missing: record `null` and surface a warning in your summary.

## Return value to orchestrator

A short text summary: (a) path to written profile, (b) any warnings/gaps, (c) whether mocks were detected (so the orchestrator can queue the refactor-proposal for Gate 1).
```

- [ ] **Step 2: Commit**

```bash
git add agents/project-discoverer.md
git commit -m "feat(agents): project-discoverer subagent"
```

---

### Task 3.2: `test-generator` subagent

**Files:**
- Create: `agents/test-generator.md`

- [ ] **Step 1: Write the agent definition**

```markdown
---
name: test-generator
description: |
  Generates tests for a single (class × tier) work unit. Consumes signature
  extract + relevant catalog entries + plan excerpt + project profile +
  existing tests/fakes. Forbidden from reading the body of the class under test.
  Writes test files to the appropriate source set and returns a summary.
model: inherit
---

You are the test-generator subagent. You produce tests for exactly one (class, tier) unit of work per invocation.

## Inputs (provided by orchestrator)

- `signatureExtract`: path to a file containing the signatures-only view of the class under test.
- `tier`: one of `unit`, `integration`, `roborazzi`, `cucumber`.
- `catalogPaths`: a list of absolute paths to relevant `catalog/*.md` entries.
- `planExcerpt`: the portion of `TEST_PLAN.md` that describes what to test, including traceability markers.
- `projectProfilePath`: path to `.claude/android-test-agent/project-profile.json`.
- `existingTestPaths`: any existing test files for this class (read-allowed; to augment, not replace).
- `existingFakePaths`: any existing fakes in the module (read-allowed).

## Absolute constraints

- **You must NOT read the body of the class under test.** Only the `signatureExtract` may be consulted for information about the target class. If you believe you need the body, return a clarification request — do not work around this rule.
- **You must NOT generate mock-based tests.** Use hand-written fakes; if a dependency lacks a fake, create one alongside the test mirroring the production package.
- **Adhere to the project profile's conventions.** File locations, fixture placement, assertion library, dispatcher usage, error-wrapper (`Result` vs `Box`), and runner wiring must match what the profile says.
- **Augment existing tests; do not rewrite them.** Add new test methods to existing files or create sibling files.

## Output location by tier

- `unit`, `integration`, `roborazzi` → `src/test/kotlin/<mirror of production package>/`
- `cucumber` → `src/androidTest/kotlin/<mirror of production package>/` (plus feature files and MockWebServer fixtures per the profile's conventions)

## Per-tier specifics

- **unit**: JUnit 5 + Google Truth (or whatever the profile says). Use fakes, not mocks. Cover AC-derived behaviors, contract-derived behaviors (sealed branches, nullability), and catalog-stock cases.
- **integration**: wire the real class + its real collaborators where feasible; use fakes only at IO boundaries (network, DB, system clock). Still no mocks.
- **roborazzi**: one `@Test` per visual variant from the plan excerpt (light/dark/RTL/large-font/tablet) using `captureRoboImage`. Runs under Robolectric.
- **cucumber**: feature file in Gherkin (one scenario per AC-derived behavior), step definitions wired into the detected Hilt test infrastructure, MockWebServer stubs for every backend call the feature makes.

## Return value

A short text summary: files created, files augmented, any clarification requests, any plan-excerpt items you decided you couldn't produce (with reason).
```

- [ ] **Step 2: Commit**

```bash
git add agents/test-generator.md
git commit -m "feat(agents): test-generator subagent"
```

---

### Task 3.3: `failure-analyst` subagent

**Files:**
- Create: `agents/failure-analyst.md`

- [ ] **Step 1: Write the agent definition**

```markdown
---
name: failure-analyst
description: |
  Triages Gradle/test run failures into mechanical (fixable via patch) vs
  substantive (requires human). Returns a structured patch or an escalation
  payload.
model: inherit
---

You are the failure-analyst subagent. Given a Gradle run log and the generated test files, categorize each failure and return a triage report.

## Inputs

- `runLogPath`: path to captured `./gradlew` output.
- `generatedTestFiles`: list of paths to the test files just written.
- `projectProfilePath`: path to `.claude/android-test-agent/project-profile.json`.
- `priorAttempts`: list of (failingTestId → errorSignature) tuples, if this is a retry.

## Categorization rules

**Mechanical** — fixable by patching test or supporting files:
- Missing import (`unresolved reference …`).
- DI wiring gap (`Hilt module missing`, `cannot create instance of …` with a known fix).
- Timing flake (intermittent `CancellationException`, dispatcher mismatch).
- Assertion DSL misuse (e.g., `.isEqualTo(…)` called on wrong type).

**Substantive** — requires human judgment:
- Assertion mismatch against intent (the test ran and failed; the code is not doing what the AC says).
- Behavior divergence (runtime exception that indicates real bug or real-but-intended behavior).
- Anything not in the mechanical list.

## Loop guard

If a failing test ID appears in `priorAttempts` with the same error signature, skip to substantive — do not propose another mechanical patch.

## Output

Two shapes, one per failing test:

Mechanical:
```json
{
  "testId": "ModuleFoo.TestBar#testX",
  "category": "mechanical",
  "patch": {
    "file": "<path>",
    "edits": [ { "find": "<exact>", "replace": "<exact>" } ]
  },
  "reason": "<short>"
}
```

Substantive:
```json
{
  "testId": "ModuleFoo.TestBar#testX",
  "category": "substantive",
  "diagnosis": "<one paragraph>",
  "suggestedFixPath": "<instruction for the human>"
}
```

Return these as a list. Orchestrator applies mechanical patches and escalates substantive ones to Gate 2.
```

- [ ] **Step 2: Commit**

```bash
git add agents/failure-analyst.md
git commit -m "feat(agents): failure-analyst subagent"
```

---

### Task 3.4: Sample fixture Android project

**Files:**
- Create: `tests/fixtures/sample-project/` (minimal Android project)

- [ ] **Step 1: Scaffold a minimal multi-module Android project**

```bash
mkdir -p tests/fixtures/sample-project/{app,feature-auth}/src/main/kotlin/com/sample/{app,auth}
mkdir -p tests/fixtures/sample-project/{app,feature-auth}/src/test/kotlin/com/sample/{app,auth}
mkdir -p tests/fixtures/sample-project/gradle
cat > tests/fixtures/sample-project/settings.gradle.kts <<'G'
rootProject.name = "sample-project"
include(":app", ":feature-auth")
G
```

- [ ] **Step 2: Add a minimal `libs.versions.toml`**

```toml
[versions]
kotlin = "2.1.0"
retrofit = "3.0.0"
hilt = "2.57.2"
junit5 = "6.0.2"

[libraries]
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
google-hilt = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
tests-junit5 = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "junit5" }
```

(Save to `tests/fixtures/sample-project/gradle/libs.versions.toml`.)

- [ ] **Step 3: Add ViewModel + Repo + DataSource across the two modules**

`feature-auth/src/main/kotlin/com/sample/auth/AuthDataSource.kt`:

```kotlin
package com.sample.auth

import retrofit2.http.Body
import retrofit2.http.POST

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val token: String)

interface AuthDataSource {
    @POST("/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}
```

`feature-auth/src/main/kotlin/com/sample/auth/AuthRepository.kt`:

```kotlin
package com.sample.auth

import javax.inject.Inject

sealed class LoginError {
    data object InvalidCredentials : LoginError()
    data object Network : LoginError()
    data class Server(val code: Int) : LoginError()
}

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<String>
}

class DefaultAuthRepository @Inject constructor(
    private val dataSource: AuthDataSource,
) : AuthRepository {
    override suspend fun login(email: String, password: String): Result<String> {
        // Body intentionally present so the extractor has something to strip.
        return runCatching { dataSource.login(LoginRequest(email, password)).token }
    }
}
```

`app/src/main/kotlin/com/sample/app/LoginViewModel.kt`:

```kotlin
package com.sample.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sample.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repo: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state

    fun submit(email: String, password: String) {
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            _state.value = repo.login(email, password).fold(
                onSuccess = { LoginUiState.Success },
                onFailure = { LoginUiState.Error(it.message ?: "unknown") },
            )
        }
    }
}
```

`feature-auth/src/test/kotlin/com/sample/auth/FakeAuthRepository.kt` (exercises the "augment" path):

```kotlin
package com.sample.auth

class FakeAuthRepository(
    private var response: Result<String> = Result.success("token"),
) : AuthRepository {
    fun setResponse(result: Result<String>) { response = result }
    override suspend fun login(email: String, password: String): Result<String> = response
}
```

- [ ] **Step 4: Commit**

```bash
git add tests/fixtures/sample-project
git commit -m "feat(fixtures): minimal sample Android project (app + feature-auth + one fake)"
```

---

### Task 3.5: Synthetic stories + expected plan outlines

**Files:**
- Create: `tests/fixtures/stories/login-happy-path.md`
- Create: `tests/fixtures/stories/login-happy-path.expected-plan.md`

- [ ] **Step 1: Write the user story + ACs**

```markdown
# As a user I want to log in so that I can access my account

## Acceptance criteria

- **AC-1**: On submitting valid credentials, I see the home screen within 3 seconds.
- **AC-2**: On invalid credentials, I see an inline error "Wrong email or password" and the password field is cleared.
- **AC-3**: On network failure during login, I see a banner "Network issue — please retry" and the submit button remains enabled.
- **AC-4**: On a 5xx backend response, I see a generic error state; retrying is idempotent.
```

- [ ] **Step 2: Write the expected plan outline**

```markdown
# Expected TEST_PLAN outline — login-happy-path

## com.sample.app.LoginViewModel (tier: unit)

- [ ] submit_validCreds_emitsLoadingThenSuccess
      source: AC-1
- [ ] submit_invalidCreds_emitsError
      source: AC-2
- [ ] submit_networkFailure_emitsError_bannerSemantics
      source: AC-3 + catalog/retrofit.md#network-failure
- [ ] submit_5xx_emitsGenericError
      source: AC-4 + catalog/retrofit.md#5xx
- [ ] stateTransitions_startFromIdle_neverSkipLoading
      source: contract (LoginUiState sealed branches)

## com.sample.auth.DefaultAuthRepository (tier: integration)

- [ ] login_success_returnsTokenFromDataSource
      source: AC-1 + catalog/retrofit.md#success
- [ ] login_4xx_wrapsInFailure
      source: AC-2 + catalog/retrofit.md#4xx
- [ ] login_ioException_wrapsInFailure
      source: AC-3 + catalog/retrofit.md#network-failure
- [ ] login_5xx_wrapsInFailure
      source: AC-4 + catalog/retrofit.md#5xx

## Refactor proposals

(None — sample project has no mock-based tests.)

## Clarification questions

- AC-3 "banner" vs AC-4 "generic error state": same component or different?
```

- [ ] **Step 3: Commit**

```bash
git add tests/fixtures/stories
git commit -m "feat(fixtures): synthetic login story + expected plan"
```

---

### Task 3.6: Captured failure logs for analyst fixture

**Files:**
- Create: `tests/fixtures/run-logs/mechanical-missing-import.txt`
- Create: `tests/fixtures/run-logs/substantive-assertion-mismatch.txt`
- Create: `tests/fixtures/run-logs/README.md`

- [ ] **Step 1: Add representative run logs**

`mechanical-missing-import.txt`:

```
> Task :feature-auth:compileTestKotlin FAILED
e: file:///…/LoginViewModelTest.kt:12:28 Unresolved reference: Truth

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':feature-auth:compileTestKotlin'.
> Compilation error. See log for more details.
```

Expected classification: **mechanical** — add missing import `import com.google.common.truth.Truth.assertThat`.

`substantive-assertion-mismatch.txt`:

```
> Task :app:test

com.sample.app.LoginViewModelTest > submit_invalidCreds_emitsError FAILED
    com.google.common.truth.AssertionError:
    expected: LoginUiState.Error(message=Wrong email or password)
    but was:  LoginUiState.Error(message=unknown)
        at com.sample.app.LoginViewModelTest.submit_invalidCreds_emitsError(LoginViewModelTest.kt:47)

1 test completed, 1 failed

FAILURE: Build failed with an exception.
```

Expected classification: **substantive** — code surfaces generic error string instead of the AC-2-required message; analyst should flag as "code likely not matching AC-2; human review."

- [ ] **Step 2: Write `README.md` for the fixture set**

Describe what each log illustrates and the expected analyst classification.

- [ ] **Step 3: Commit**

```bash
git add tests/fixtures/run-logs
git commit -m "feat(fixtures): failure-log samples for analyst validation"
```

---

## Phase 4 — Orchestrator skill

The orchestrator is the main-session skill invoked as `/write-tests`. It is a single `SKILL.md` prompt that guides the main session through all gates and dispatches subagents at the right moments.

### Task 4.1: Skill file scaffold and frontmatter

**Files:**
- Create: `skills/write-tests/SKILL.md`

- [ ] **Step 1: Write the header**

```markdown
---
name: write-tests
description: "Write tests for the current Android branch driven by a user story + ACs, adhering to the project's existing conventions. Run inside an Android project's branch."
---

# Write Tests — Android Test Agent Orchestrator

You are the orchestrator for the android-test-agent plugin. When the user invokes `/write-tests`, follow this workflow *in order*. Do not skip gates. Do not deviate from the project's existing conventions.

**Reference spec:** `<plugin-dir>/docs/superpowers/specs/2026-04-21-android-test-agent-design.md`.

---
```

- [ ] **Step 2: Commit**

```bash
git add skills/write-tests/SKILL.md
git commit -m "feat(skill): scaffold /write-tests SKILL.md"
```

---

### Task 4.2: Inputs and parent-branch detection

**Files:**
- Modify: `skills/write-tests/SKILL.md`

- [ ] **Step 1: Add the "Inputs" section**

```markdown
## 1. Collect inputs

- Ask the user to paste the user story + acceptance criteria. If not provided, prompt and wait.
- Determine the parent branch:
  1. Read `.claude/android-test-agent/config.json`. If `parentBranch` is set, use it.
  2. Otherwise, check for local branches `develop`, `main`, `master` (in that order). Use the first one that exists.
  3. If none found, ask the user and persist to `config.json`.
- Compute the diff: `git diff <parent>...HEAD` (three-dot syntax — commits unique to this branch).
```

- [ ] **Step 2: Commit**

```bash
git add skills/write-tests/SKILL.md
git commit -m "feat(skill): step 1 — inputs and parent-branch detection"
```

---

### Task 4.3: Discovery invocation with caching

**Files:**
- Modify: `skills/write-tests/SKILL.md`

- [ ] **Step 1: Add the discovery section**

```markdown
## 2. Discovery (cached)

- Hash `gradle/libs.versions.toml` (SHA-256). If `.claude/android-test-agent/project-profile.json` exists AND its `versionCatalogHash` matches, reuse it.
- Otherwise dispatch the `project-discoverer` subagent with the repo root as input. It writes a fresh profile.
- If no `libs.versions.toml` exists, the discoverer falls back to scanning `build.gradle.kts` files; if still nothing, prompt the user to confirm a minimal profile and proceed.
- Read the profile once done.
```

- [ ] **Step 2: Commit**

```bash
git add skills/write-tests/SKILL.md
git commit -m "feat(skill): step 2 — cached discovery"
```

---

### Task 4.4: Scope computation (diff + AC-inference fallback)

**Files:**
- Modify: `skills/write-tests/SKILL.md`

- [ ] **Step 1: Add the scope section**

```markdown
## 3. Scope

- If the diff is non-empty: `scope = (classes changed in diff) ∩ (classes implicated by ACs)`. "Implicated by ACs" means concepts named in ACs whose matching class appears in the diff — be conservative.
- If the diff is empty: run AC-based inference. Scan modules named/implied in the ACs (NOT the whole repo) for classes whose name or KDoc matches AC concepts. Present the candidate list to the user; they confirm, edit, or replace. If the final list is empty, stop with "no scope — nothing to test."
```

- [ ] **Step 2: Commit**

```bash
git add skills/write-tests/SKILL.md
git commit -m "feat(skill): step 3 — scope (diff + AC inference)"
```

---

### Task 4.5: Signature extraction

**Files:**
- Modify: `skills/write-tests/SKILL.md`

- [ ] **Step 1: Add the extraction section**

```markdown
## 4. Signature extraction

- For each class in scope, invoke the bundled extractor:
  ```bash
  <plugin-dir>/scripts/kotlin-signatures/bin/kotlin-signatures <path/to/Class.kt>
  ```
- Save each extract to `.claude/android-test-agent/runs/<timestamp>/extracts/<mirror-path>/<Class>.kt`.
- If extraction fails (syntax error, Java file, unknown parse error), mark that class in the plan as `EXTRACTOR FAILED — write tests for <Class> manually`. Do NOT fall back to reading the body.
```

- [ ] **Step 2: Commit**

```bash
git add skills/write-tests/SKILL.md
git commit -m "feat(skill): step 4 — signature extraction"
```

---

### Task 4.6: Gate 0 — feature understanding

**Files:**
- Modify: `skills/write-tests/SKILL.md`

- [ ] **Step 1: Add Gate 0**

```markdown
## 5. GATE 0 — Feature understanding

Write a short inline summary (3–5 bullets) covering:

- What the feature does (one-sentence goal).
- Who uses it / when it fires.
- What the ACs collectively imply beyond their literal text.
- Which classes / subsystems are touched.
- Any ambiguities you cannot resolve from inputs alone.

Ask: "Does this match what you intended? Anything I missed or misinterpreted?"

**Loop until the user confirms.** Only then proceed. Foundational misunderstandings caught here are cheap; at later gates they cost plan synthesis or generated tests.
```

- [ ] **Step 2: Commit**

```bash
git add skills/write-tests/SKILL.md
git commit -m "feat(skill): step 5 — Gate 0 feature understanding"
```

---

### Task 4.7: Plan synthesis + Gate 1

**Files:**
- Modify: `skills/write-tests/SKILL.md`

- [ ] **Step 1: Add plan synthesis and Gate 1**

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add skills/write-tests/SKILL.md
git commit -m "feat(skill): steps 6–7 — plan synthesis + Gate 1"
```

---

### Task 4.8: Generator dispatch

**Files:**
- Modify: `skills/write-tests/SKILL.md`

- [ ] **Step 1: Add generator dispatch**

```markdown
## 8. Generate

For each approved `(class × tier)`, dispatch a `test-generator` subagent in parallel where independent. Inputs to each:

- `signatureExtract`: path to the extract for this class.
- `tier`.
- `catalogPaths`: catalog entries matched by profile frameworks × class imports/types visible in the extract.
- `planExcerpt`: the relevant slice of `TEST_PLAN.md`.
- `projectProfilePath`.
- `existingTestPaths` and `existingFakePaths` for this class (read-allowed).

Aggregate the generator results. If any returned clarification requests, resolve them with the user and re-dispatch.
```

- [ ] **Step 2: Commit**

```bash
git add skills/write-tests/SKILL.md
git commit -m "feat(skill): step 8 — generator dispatch"
```

---

### Task 4.9: Run tests and triage

**Files:**
- Modify: `skills/write-tests/SKILL.md`

- [ ] **Step 1: Add run and triage**

```markdown
## 9. Run and triage

For each module where tests were written, run the module-scoped task(s) from the profile:

```bash
./gradlew <module>:<task>
```

(Use `profile.modules[module].testTasks` mappings — e.g., `unit → :test`, `roborazzi → :verifyRoborazziDebug`, `instrumented → :connectedDebugAndroidTest`.)

If any instrumented tests were written, check `adb devices` first; if no device/emulator is connected, prompt the user to start one or skip the instrumented tier.

Capture output to `.claude/android-test-agent/runs/<timestamp>/run.log`.

If there are failures, dispatch `failure-analyst`:

- For **mechanical** results, apply the returned patches and re-run. Retry limit: 2.
- **Loop guard**: if the same test fails with the same error after a patch, escalate directly without retrying.
- **Substantive** results go straight to Gate 2.
```

- [ ] **Step 2: Commit**

```bash
git add skills/write-tests/SKILL.md
git commit -m "feat(skill): step 9 — run, triage, retry with loop guard"
```

---

### Task 4.10: Gate 2 escalation and completion

**Files:**
- Modify: `skills/write-tests/SKILL.md`

- [ ] **Step 1: Add Gate 2 and completion**

```markdown
## 10. GATE 2 — Escalation (on persistent failure)

Write `diagnosis.md` under `.claude/android-test-agent/runs/<timestamp>/` containing:

- Summary: `N passed / M failed / K skipped`.
- Per failed test: file path, failure type, most-likely cause, suggested fix path.

Tell the user the run is stopped and point them at the diagnosis. The run directory retains `TEST_PLAN.md`, generated files, `run.log`, and the diagnosis — they can resume manually.

## 11. Completion

On clean run, print the summary, commit the generated tests on behalf of the user *only if they explicitly ask*, and stop.
```

- [ ] **Step 2: Commit**

```bash
git add skills/write-tests/SKILL.md
git commit -m "feat(skill): steps 10–11 — Gate 2 escalation and completion"
```

---

## Phase 5 — Optional hook and dogfooding

### Task 5.1: SessionStart hook (optional)

**Files:**
- Create: `hooks/session-start.sh`
- Modify: `package.json` (register the hook)

- [ ] **Step 1: Write the hook**

```bash
#!/usr/bin/env bash
# Detect an Android project and surface /write-tests in the session.
if [[ -f "gradle/libs.versions.toml" ]] || ls build.gradle.kts >/dev/null 2>&1; then
  echo "Android project detected — /write-tests is available."
fi
```

- [ ] **Step 2: Make executable**

```bash
chmod +x hooks/session-start.sh
```

- [ ] **Step 3: Verify hook discovery**

Claude Code auto-discovers plugin hooks from the `hooks/` directory — no explicit `package.json` registration is needed for scripts following the `<event-name>.sh` convention (`session-start.sh`, `user-prompt-submit.sh`, etc.). If a future Claude Code plugin-schema change requires explicit registration, the hook entry goes under a `hooks` field in `package.json` as `{ "sessionStart": "./hooks/session-start.sh" }`.

Smoke-test by linking the plugin to `~/.claude/plugins/` and starting a fresh session inside an Android project; the "Android project detected" line should appear in the session context.

- [ ] **Step 4: Commit**

```bash
git add hooks
git commit -m "feat(hooks): optional SessionStart to surface /write-tests in Android projects"
```

---

### Task 5.2: Dogfooding checklist

**Files:**
- Create: `docs/DOGFOODING.md`

- [ ] **Step 1: Write the checklist**

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add docs/DOGFOODING.md
git commit -m "docs: v1 dogfooding checklist"
```

---

### Task 5.3: Install instructions and top-level README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Expand the README**

```markdown
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

## Design

See `docs/superpowers/specs/2026-04-21-android-test-agent-design.md`.

## Uninstall

```bash
rm ~/.claude/plugins/android-test-agent
```
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: expand README with install + usage"
```

---

## Appendix A — Phase dependencies

```
Phase 0 — skeleton         (no deps)
Phase 1 — JVM extractor    (needs Phase 0)
Phase 2 — catalog          (needs Phase 0)
Phase 3 — subagents        (needs Phase 0, 2 for catalog references)
Phase 4 — orchestrator     (needs Phase 1, 2, 3)
Phase 5 — hook + docs      (needs Phase 4 for end-to-end)
```

Phases 1 and 2 can proceed in parallel once Phase 0 is done.

## Appendix B — Manual validation at end of each phase

- **Phase 1 complete**: `./gradlew test` in `scripts/kotlin-signatures` is green; `bin/kotlin-signatures` correctly strips bodies on a hand-picked sample file.
- **Phase 2 complete**: `python3 scripts/catalog-lint/catalog_lint.py` exits 0 on all 12 entries.
- **Phase 3 complete**: each subagent file passes a structural review (frontmatter present, all required input/output fields documented, constraints stated).
- **Phase 4 complete**: `/write-tests` can be invoked against the sample fixture project and produces a plan that lines up with the expected plan outline (±).
- **Phase 5 complete**: dogfooding checklist executed against a real Android project with results captured as follow-up items.
