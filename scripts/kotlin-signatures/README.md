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

## Parser

Uses `kotlin-compiler-embeddable` PSI to parse `.kt` files without JNI.
