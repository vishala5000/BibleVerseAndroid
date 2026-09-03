#!/bin/sh
set -e
GRADLE_VERSION=9.6.0
DIST="$HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION-bin.zip"
BASE="$HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION-bin"
if [ ! -x "$BASE/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  mkdir -p "$BASE"
  curl -L --fail "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$DIST"
  unzip -q -o "$DIST" -d "$BASE"
fi
exec "$BASE/gradle-$GRADLE_VERSION/bin/gradle" "$@"
