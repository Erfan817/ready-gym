#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVA_HOME=${JAVA_HOME:-/home/ubuntu/.local/share/ready-gym-tools/jdk/usr/lib/jvm/java-17-openjdk-amd64}
cd "$ROOT"
mkdir -p build/classes
CP=$(printf '%s:' lib/jackson-*.jar lib/postgresql-*.jar)
"$JAVA_HOME/bin/javac" -J-Xmx160m --release 17 -encoding UTF-8 -cp "$CP" -d build/classes $(python3 -c 'from pathlib import Path; print(" ".join(map(str,Path("src/main/java").rglob("*.java"))))')
exec "$JAVA_HOME/bin/java" -Xmx160m -cp "build/classes:$CP" com.readygym.business.Main
