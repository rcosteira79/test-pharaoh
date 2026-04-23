#!/usr/bin/env bash
# Detect an Android project and surface /write-tests in the session.
if [[ -f "gradle/libs.versions.toml" ]] || ls build.gradle.kts >/dev/null 2>&1; then
  echo "Android project detected — /write-tests is available."
fi
