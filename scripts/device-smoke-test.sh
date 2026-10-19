#!/usr/bin/env bash
# Installs the dev app on a USB-connected phone and checks it actually runs: it launches the app,
# forces every background job it has scheduled to run now, and fails on crashes or WorkManager
# errors. The dev app is shrunk with R8 like the Play build, so this catches release-only bugs
# (like the 1.0.25 widget that never loaded) before an upload.
#
# Only touches the dev app (com.superfit.aifitness.debug); never the Play Store app.
#
# Usage: scripts/device-smoke-test.sh [--no-build]
set -uo pipefail

cd "$(dirname "$0")/.."
PKG=com.superfit.aifitness.debug
APK=app/build/outputs/apk/debug/app-debug.apk

if [ "$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')" -ne 1 ]; then
    echo "Connect exactly one phone with USB debugging on (adb devices)." >&2
    exit 2
fi

if [ "${1:-}" != "--no-build" ]; then
    ./gradlew -q assembleDebug || exit 1
fi
scripts/check-r8-keeps.sh || exit 1

adb install -r "$APK" >/dev/null || { echo "Install failed" >&2; exit 1; }
adb logcat -c

# Launch, give it time to start and schedule its work.
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 10
pid=$(adb shell pidof "$PKG" | tr -d '\r')
if [ -z "$pid" ]; then
    echo "FAIL: the app is not running after launch" >&2
    adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime" -A15 | head -40 >&2
    exit 1
fi

# Force every job the app has with Android's job scheduler (WorkManager runs through it). This
# includes the daily reminder, so the phone shows a reminder notification during the test.
# Skip jobs set 100+ days out: Glance books a ten-year placeholder job only to keep WorkManager's
# job service enabled. It is never meant to run and fails ("must be started with a key") if forced.
jobs=$(adb shell dumpsys jobscheduler | awk -v pkg="$PKG/" '
    /^  JOB #u0a[0-9]+\/[0-9]+: / { id = ""; if (index($0, pkg)) { split($2, a, "/"); id = a[2]; sub(":", "", id); ids[id] = 1 } }
    id != "" && /Minimum latency: \+[0-9][0-9][0-9]+d/ { delete ids[id] }
    END { for (i in ids) print i }' | sort -u)
if [ -z "$jobs" ]; then
    echo "FAIL: the app scheduled no background jobs (expected the reminder and the sync)" >&2
    exit 1
fi
for job in $jobs; do
    adb shell cmd jobscheduler run -f "$PKG" "$job" >/dev/null
done
sleep 20

# Problems that only appear when the code runs: crashes, WorkManager failing to create a worker or
# its input merger, and classes or constructors R8 removed.
log=$(adb logcat -d -v brief)
problems=$(echo "$log" | grep -E "\( *$pid\)" | grep -E \
    "FATAL EXCEPTION|E/WM-|Could not create|NoSuchMethodException|ClassNotFoundException|NoClassDefFoundError|Worker result FAILURE")

echo "Jobs run: $(echo $jobs | wc -w | tr -d ' ')"
echo "$log" | grep -E "\( *$pid\)" | grep -E "Worker result" | sed -E 's/.*Worker result/  Worker result/' | sort -u
if [ -n "$problems" ]; then
    echo "FAIL: errors while the app ran:" >&2
    echo "$problems" | head -40 >&2
    exit 1
fi
echo "PASS: the dev app launched and its background jobs ran without errors."
