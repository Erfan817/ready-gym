#!/usr/bin/env python3
"""Bounded, SDK-free real Kotlin/JUnit run; build output is ignored by Git."""
from pathlib import Path
import os
import subprocess
import sys

CORE = Path(__file__).resolve().parents[1]
TOOLS = Path(os.environ.get("READY_GYM_TOOLS", str(Path.home() / ".local/share/ready-gym-tools")))
JAVA = Path(os.environ.get("JAVA_HOME", str(TOOLS / "jdk/usr/lib/jvm/java-17-openjdk-amd64"))) / "bin/java"
LIB = TOOLS / "gradle-8.9/lib"
OUT = CORE / "build/planning-standalone"
OUT.mkdir(parents=True, exist_ok=True)
CP = os.pathsep.join(str(p) for p in [LIB / "kotlin-stdlib-1.9.23.jar", TOOLS / "downloads/junit.jar", TOOLS / "downloads/hamcrest.jar"])
sources = sorted(str(p) for p in (CORE / "src").rglob("*.kt"))
label = sys.argv[1] if len(sys.argv) > 1 else "latest"
if not label.replace("-", "").replace("_", "").isalnum():
    raise SystemExit("Use an alphanumeric test-run label")
logs = CORE / "build/planning-tdd"
logs.mkdir(parents=True, exist_ok=True)
commands = [
    [str(JAVA), "-Xmx384m", "-cp", str(LIB / "*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", CP, "-d", str(OUT), *sources],
    [str(JAVA), "-Xmx128m", "-cp", str(OUT) + os.pathsep + CP + os.pathsep + str(CORE / "src/main/resources"), "org.junit.runner.JUnitCore", "com.readygym.core.OnboardingTest", "com.readygym.core.PlanningTest"],
]
with (logs / (label + ".log")).open("w") as log:
    for command in commands:
        result = subprocess.run(command, cwd=CORE, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=90)
        log.write(result.stdout)
        log.flush()
        print(result.stdout, end="", flush=True)
        if result.returncode:
            raise SystemExit(result.returncode)
print("Real Kotlin + JUnit log:", logs / (label + ".log"))
