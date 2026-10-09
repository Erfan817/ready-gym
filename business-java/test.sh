#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVA_HOME=${JAVA_HOME:-/home/ubuntu/.local/share/ready-gym-tools/jdk/usr/lib/jvm/java-17-openjdk-amd64}
cd "$ROOT"
mkdir -p build/classes build/test-classes build/evidence
CP=$(printf '%s:' lib/*.jar)
"$JAVA_HOME/bin/javac" -J-Xmx160m --release 17 -encoding UTF-8 -cp "$CP" -d build/classes $(python3 -c 'from pathlib import Path; print(" ".join(map(str,Path("src/main/java").rglob("*.java"))))')
"$JAVA_HOME/bin/javac" -J-Xmx160m --release 17 -encoding UTF-8 -cp "build/classes:$CP" -d build/test-classes $(python3 -c 'from pathlib import Path; print(" ".join(map(str,Path("src/test/java").rglob("*.java"))))')
exec "$JAVA_HOME/bin/java" -Xmx160m -jar lib/junit-platform-console-standalone-1.11.4.jar execute --class-path "build/classes:build/test-classes:$CP" --disable-ansi-colors --details=tree --reports-dir=build/test-results "$@"
