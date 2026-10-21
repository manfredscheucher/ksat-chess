#!/usr/bin/env bash
# Run the web (Kotlin/JS) build in a browser via the Gradle webpack dev-server (hot-reload).
# Opens Chrome in a phone-sized window.
#
#   ./scripts/runWeb.sh                          # serve on :8608, open Chrome
#   NO_CHROME=1 ./scripts/runWeb.sh              # serve only, open the URL yourself
#   WINDOW_SIZE=1280,800 ./scripts/runWeb.sh     # desktop-sized window
#
# Port: 8608 (set in composeApp/webpack.config.d/watch.js; see ~/github/.dev-ports.md).
# Change it in BOTH that file and the PORT= line below if ever needed.
set -euo pipefail
cd "$(dirname "$0")/.."

PORT=8608
URL="http://localhost:$PORT/"
CHROME="${CHROME_PATH:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
TASK=":composeApp:jsBrowserDevelopmentRun"
mkdir -p build/logs
LOG="build/logs/runweb.log"

if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Error: port $PORT is already in use." >&2
    exit 1
fi

echo "Starting Gradle dev-server ($TASK) …  logs: $LOG   (Ctrl-C to stop)"
# Linking the Kotlin/JS bundles (Compose + SAT solvers) needs more than the 1.5 GB kotlin daemon set in
# gradle.properties (OutOfMemoryError); 3 GB like luckySweep — only for this build.
WEB_HEAP=(-Pkotlin.daemon.jvmargs=-Xmx3072M)
# The dev-server serves the faster development bundle of the solver worker (builds default to production).
./gradlew "${WEB_HEAP[@]}" -PworkerVariant=development "$TASK" >"$LOG" 2>&1 &
GRADLE_PID=$!
trap 'kill "$GRADLE_PID" 2>/dev/null || true' EXIT

# Wait for the dev-server (a cold Gradle build can take a few minutes).
echo -n "waiting for $URL "
UP=""
for _ in $(seq 1 1200); do
    if curl -sf -o /dev/null "$URL"; then echo " up"; UP=1; break; fi
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
        echo; echo "Gradle exited early — see $LOG" >&2; tail -20 "$LOG" >&2; exit 1
    fi
    echo -n .; sleep 0.5
done
if [ -z "$UP" ]; then
    echo; echo "The dev-server did not come up within 10 minutes — see $LOG" >&2; tail -20 "$LOG" >&2; exit 1
fi

if [ -z "${NO_CHROME:-}" ]; then
    if [ -x "$CHROME" ]; then
        "$CHROME" --new-window --window-size="${WINDOW_SIZE:-450,900}" "$URL" >/dev/null 2>&1 &
    elif command -v google-chrome >/dev/null 2>&1; then
        google-chrome --new-window --window-size="${WINDOW_SIZE:-450,900}" "$URL" >/dev/null 2>&1 &
    else
        echo "Chrome not found — open manually: $URL" >&2
    fi
fi

wait "$GRADLE_PID"
