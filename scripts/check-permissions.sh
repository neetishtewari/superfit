#!/usr/bin/env bash
# Fails if the built app asks for a permission that isn't listed in app/permissions.txt.
#
# Libraries can add permissions to the app without any change to our own manifest, and every
# permission is something users see and Play reviews (the Data safety form, Health Connect
# declaration). This keeps that list a deliberate choice: when a change needs a new permission,
# add it to app/permissions.txt in the same pull request.
#
# Usage: scripts/check-permissions.sh   (after ./gradlew assembleDebug)
set -euo pipefail

cd "$(dirname "$0")/.."
manifest=$(find app/build/intermediates -name AndroidManifest.xml -path '*merged_manifest*' -path '*debug*' \
    ! -path '*AndroidTest*' ! -path '*UnitTest*' 2>/dev/null | head -1)
if [ -z "$manifest" ]; then
    echo "No merged manifest found. Build first: ./gradlew assembleDebug" >&2
    exit 2
fi

actual=$(grep -oE '<uses-permission(-sdk-23)? [^>]*android:name="[^"]+"' "$manifest" \
    | sed -E 's/.*android:name="([^"]+)"/\1/' | sort -u)
expected=$(grep -vE '^\s*(#|$)' app/permissions.txt | sort -u)

added=$(comm -13 <(echo "$expected") <(echo "$actual"))
removed=$(comm -23 <(echo "$expected") <(echo "$actual"))

if [ -n "$added" ] || [ -n "$removed" ]; then
    [ -n "$added" ] && printf 'New permissions not in app/permissions.txt:\n%s\n\n' "$added" >&2
    [ -n "$removed" ] && printf 'Listed in app/permissions.txt but no longer requested:\n%s\n\n' "$removed" >&2
    printf 'The app now requests (from %s):\n%s\n' "$manifest" "$actual" >&2
    exit 1
fi
echo "Permissions match app/permissions.txt ($(echo "$actual" | wc -l | tr -d ' ') checked)."
