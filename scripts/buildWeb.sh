#!/usr/bin/env bash
# Build the static production website (Kotlin/JS bundle, solver worker, index.html).
# Output: composeApp/build/dist/js/productionExecutable/
#
#   ./scripts/buildWeb.sh
set -euo pipefail
cd "$(dirname "$0")/.."

# Linking the Kotlin/JS bundles needs more than the 1.5 GB kotlin daemon set in gradle.properties.
./gradlew -Pkotlin.daemon.jvmargs=-Xmx3072M :composeApp:jsBrowserDistribution
echo "Website built: composeApp/build/dist/js/productionExecutable/"
