#!/bin/sh
set -eu
reapp_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
reapp_test_out=$(mktemp -d)
trap 'rm -rf "$reapp_test_out"' EXIT HUP INT TERM
java com.sun.tools.javac.Main -encoding UTF-8 -d "$reapp_test_out" \
    "$reapp_root/app/src/main/java/com/gree1d/reappzuku/manager/ForegroundAppDetector.java" \
    "$reapp_root/tools/tests/com/gree1d/reappzuku/manager/ForegroundAppDetectorTest.java"
java -ea -cp "$reapp_test_out" com.gree1d.reappzuku.manager.ForegroundAppDetectorTest
