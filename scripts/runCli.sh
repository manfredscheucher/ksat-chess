#!/usr/bin/env bash
# Build the JVM command line (installDist) and run the freshly installed binary.
# Always rebuilds first, so it never runs a stale binary.
#
#   ./scripts/runCli.sh solve puzzles/chess-com-5/4.json --all --verbose
#   ./scripts/runCli.sh perft 3
#   ./scripts/runCli.sh run puzzles --timeout 60
#   ./scripts/runCli.sh solve puzzles/demos/01-fools-mate.json --dimacs /tmp/fools.cnf
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

"$ROOT/gradlew" -p "$ROOT" :cli:installDist --console=plain -q
exec "$ROOT/cli/build/install/sat-chess/bin/sat-chess" "$@"
