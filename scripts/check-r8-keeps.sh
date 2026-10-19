#!/usr/bin/env bash
# Fails if R8 removed something that WorkManager creates by reflection.
#
# R8 writes everything it removed to usage.txt. A class name alone on a line means the whole
# class was removed; indented members under "<class>:" were removed from that class. If either
# happens to the classes below, the app only breaks in shrunk builds (the Play build and the dev
# app), and a plain compile never shows it. That is how the 1.0.25 widget got stuck loading.
#
# Usage: scripts/check-r8-keeps.sh [path/to/usage.txt]   (default: the debug build's report)
set -euo pipefail

cd "$(dirname "$0")/.."
usage="${1:-app/build/outputs/mapping/debug/usage.txt}"
if [ ! -f "$usage" ]; then
    echo "No R8 usage report at $usage. Build first: ./gradlew assembleDebug" >&2
    exit 2
fi

# WorkManager creates the input merger of every one-off job by reflection.
required=(
    "androidx.work.OverwritingInputMerger"
    "androidx.work.ArrayCreatingInputMerger"
)
# ...and every worker. Find the app's workers: "class Name(" followed by ") : (Coroutine)Worker(".
while IFS= read -r file; do
    pkg=$(sed -nE 's/^package ([a-zA-Z0-9_.]+).*/\1/p' "$file" | head -1)
    name=$(grep -oE "^class [A-Za-z0-9_]+" "$file" | head -1 | awk '{print $2}')
    [ -n "$pkg" ] && [ -n "$name" ] && required+=("$pkg.$name")
done < <(grep -rlE "^\) : (Coroutine)?Worker\(" app/src/main/java --include='*.kt')

failed=0
for cls in "${required[@]}"; do
    if grep -qxF "$cls" "$usage"; then
        echo "R8 removed $cls entirely" >&2
        failed=1
    elif awk -v c="$cls:" '$0 == c {inside=1; next} /^[^ ]/ {inside=0} inside && /<init>\(/ {found=1} END {exit !found}' "$usage"; then
        echo "R8 removed a constructor of $cls" >&2
        failed=1
    fi
done

if [ "$failed" -ne 0 ]; then
    echo "Add a -keep rule for the classes above to app/proguard-rules.pro." >&2
    exit 1
fi
echo "R8 kept every class WorkManager creates by reflection (${#required[@]} checked: ${required[*]})."
