#!/usr/bin/env bash
# Benchmark matrix for doc/encoding.md: move-AMO variant x solver on SAT perft 3 and chess.com puzzle 4.
# Wall time is noisy when other processes run; the 'user' line is the CPU time.
# Uses the already installed CLI (run scripts/runCli.sh once before). Each run is capped at 280 s.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
B="$ROOT/cli/build/install/sat-chess/bin/sat-chess"
for amo in ${AMOS:-src-tgt row-col}; do
  for solver in ${SOLVERS:-cadical minisat kissat}; do
    echo "== perft 3 / $amo / $solver"
    /usr/bin/time -p timeout 280 "$B" perft 3 --move-amo "$amo" --solver "$solver" 2>&1 | grep -E "perft|user"
    echo "== puzzle 4 / $amo / $solver"
    /usr/bin/time -p timeout 280 "$B" solve "$ROOT/puzzles/chess-com-5/4.json" --all --verbose --move-amo "$amo" --solver "$solver" 2>&1 | grep -E "encoded|ms\)|found|user"
  done
done
