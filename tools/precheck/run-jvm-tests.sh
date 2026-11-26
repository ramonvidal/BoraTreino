#!/bin/bash
# Compiles and runs the pure-Kotlin JVM tests of :core:model without Gradle or Android,
# using the kotlin compiler and JUnit jars that ship with a Gradle install.
# Skips (exit 0) when the jars are missing; exits non-zero on compile errors or failing tests.
#
# :core:data is NOT covered: its tests need kotlinx-coroutines-test, Dagger/Hilt and Room
# (and android.content.Context in main), none of which are cached in cloud containers.
# Usage: run-jvm-tests.sh [TestClassFQN ...]   (no args = every test class in core/model)
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
M="$ROOT/core/model"

# Find a Gradle lib dir that has everything we need.
LIB=""
for d in /opt/gradle*/lib $(ls -d "$HOME"/.gradle/wrapper/dists/*/*/gradle-*/lib 2>/dev/null); do
  if ls "$d"/kotlin-compiler-embeddable-*.jar "$d"/junit-4*.jar >/dev/null 2>&1; then LIB="$d"; break; fi
done
if [ -z "$LIB" ]; then
  echo "run-jvm-tests: skipped (no Gradle lib dir with kotlin-compiler-embeddable and junit found)"
  exit 0
fi
one() { ls "$LIB"/$1 2>/dev/null | head -1; }
KVER_JAR=$(one 'kotlin-compiler-embeddable-*.jar'); KV=${KVER_JAR##*embeddable-}; KV=${KV%.jar}
need=("$KVER_JAR" "$LIB/kotlin-stdlib-$KV.jar" "$LIB/kotlin-reflect-$KV.jar" "$LIB/kotlin-script-runtime-$KV.jar"
      "$LIB/kotlin-daemon-embeddable-$KV.jar" "$(one 'trove4j-*.jar')" "$(one 'kotlinx-coroutines-core-jvm-*.jar')"
      "$(one 'annotations-*.jar')" "$(one 'junit-4*.jar')" "$(one 'hamcrest-core-*.jar')")
for j in "${need[@]}"; do
  if [ -z "$j" ] || [ ! -f "$j" ]; then echo "run-jvm-tests: skipped (missing jar: ${j:-unknown})"; exit 0; fi
done
command -v java >/dev/null || { echo "run-jvm-tests: skipped (no java)"; exit 0; }

KC=$(IFS=:; echo "${need[*]:0:8}")
CP="$LIB/kotlin-stdlib-$KV.jar:${need[8]}:${need[9]}"
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/main" "$OUT/test"

kc() { java -cp "$KC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 "$@" 2>&1 | grep -v JAVA_TOOL_OPTIONS; return "${PIPESTATUS[0]}"; }

echo "run-jvm-tests: compiling :core:model main (Kotlin $KV)"
kc -cp "$CP" -d "$OUT/main" $(find "$M/src/main/kotlin" -name '*.kt') || { echo "run-jvm-tests: FAILED (main compile)"; exit 1; }
echo "run-jvm-tests: compiling :core:model tests"
kc -cp "$CP:$OUT/main" -d "$OUT/test" $(find "$M/src/test/kotlin" -name '*.kt') || { echo "run-jvm-tests: FAILED (test compile)"; exit 1; }

if [ "$#" -eq 0 ]; then
  CLASSES=$(cd "$M/src/test/kotlin" && grep -rlE "@Test" --include=*.kt . | sed -E 's#^\./##; s#\.kt$##; s#/#.#g')
  CLASSES=$(for c in $CLASSES; do [ -f "$OUT/test/$(echo "$c" | tr . /).class" ] && echo "$c"; done)
else
  CLASSES="$*"
fi
java -cp "$CP:$OUT/main:$OUT/test" org.junit.runner.JUnitCore $CLASSES 2>&1 | grep -v JAVA_TOOL_OPTIONS | tee "$OUT/result.txt" | tail -40
if grep -qE "^OK \(" "$OUT/result.txt"; then echo "run-jvm-tests: PASSED"; exit 0; fi
echo "run-jvm-tests: FAILED"; exit 1
