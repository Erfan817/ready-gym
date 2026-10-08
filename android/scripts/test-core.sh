#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TOOLS="${READY_GYM_TOOLS:-$HOME/.local/share/ready-gym-tools}"
JAVA="${JAVA_HOME:-$TOOLS/jdk/usr/lib/jvm/java-17-openjdk-amd64}/bin/java"
LIB="$TOOLS/gradle-8.9/lib"
OUT=core/build/standalone
mkdir -p "$OUT"
CP="$LIB/kotlin-stdlib-1.9.23.jar:$TOOLS/downloads/junit.jar:$TOOLS/downloads/hamcrest.jar"
mapfile -t SOURCES < <(find core/src -name '*.kt' -type f)
"$JAVA" -cp "$LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$CP" -d "$OUT" "${SOURCES[@]}"
"$JAVA" -cp "$OUT:$CP" org.junit.runner.JUnitCore com.readygym.core.OnboardingTest
