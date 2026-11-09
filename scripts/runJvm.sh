#!/usr/bin/env bash
# Run the JVM desktop build via Compose's run task (window size is set in
# composeApp/src/jvmMain/.../main.kt).
#
#   ./scripts/runJvm.sh
set -euo pipefail
cd "$(dirname "$0")/.."

[ -x ./gradlew ] || { echo "Error: ./gradlew not found — run from the ksat-chess checkout." >&2; exit 1; }

echo "Starting the desktop app (:composeApp:run) …  (Ctrl-C to stop)"
exec ./gradlew :composeApp:run
