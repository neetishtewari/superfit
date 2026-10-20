#!/usr/bin/env bash
# Checks the Kotlin code formatting with ktlint; rules are in .editorconfig.
# CI runs the check. Fix formatting before committing with: scripts/ktlint.sh --format
#
# Usage: scripts/ktlint.sh [--format]
set -euo pipefail

cd "$(dirname "$0")/.."
VERSION=1.7.1
SHA256=b596a05f09364e1997b644152683af12fef58944f3acfabdf78071f5a490eb98
BIN=build/ktlint-$VERSION

if [ ! -x "$BIN" ]; then
    mkdir -p build
    curl -sSLf -o "$BIN.tmp" "https://github.com/pinterest/ktlint/releases/download/$VERSION/ktlint"
    if command -v sha256sum >/dev/null; then
        actual=$(sha256sum "$BIN.tmp" | cut -d' ' -f1)
    else
        actual=$(shasum -a 256 "$BIN.tmp" | cut -d' ' -f1)
    fi
    if [ "$actual" != "$SHA256" ]; then
        echo "ktlint download checksum mismatch" >&2
        rm -f "$BIN.tmp"
        exit 1
    fi
    chmod +x "$BIN.tmp"
    mv "$BIN.tmp" "$BIN"
fi

if [ "${1:-}" = "--format" ]; then
    "$BIN" --format --relative "app/src/**/*.kt"
else
    "$BIN" --relative "app/src/**/*.kt" || {
        echo "Formatting problems found. Run scripts/ktlint.sh --format and commit the result." >&2
        exit 1
    }
fi
