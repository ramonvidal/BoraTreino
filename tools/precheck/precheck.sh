#!/bin/bash
# Run before pushing. Cheap local checks that catch what CI kept catching.
cd "$(dirname "$0")/../.." || exit 1
fail=0
step() { echo; echo "== $1"; shift; "$@" || { echo "-- FAILED: $*"; fail=1; }; }

step "JVM tests (:core:model)" bash tools/precheck/run-jvm-tests.sh
step "Static test checks" python3 -I tools/precheck/check_tests.py
step "Translations" python3 translations/i18n.py check
if [ -x ./gradlew ] && [ -d ~/.gradle/caches ]; then
  step "Module rules (build-logic)" ./gradlew -p build-logic :rules:test --offline -q
else
  echo; echo "== Module rules: skipped (no gradlew or no Gradle cache)"
fi

echo
[ $fail -eq 0 ] && echo "precheck: OK" || echo "precheck: FAILED"
exit $fail
