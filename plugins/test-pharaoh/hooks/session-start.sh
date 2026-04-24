#!/usr/bin/env bash
# Detect an Android project and surface /test-scribe in the session.
if [[ -f "gradle/libs.versions.toml" ]] || ls build.gradle.kts >/dev/null 2>&1; then
  echo "Android project detected — /test-scribe is available."
fi
