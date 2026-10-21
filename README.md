# sat-chess-kotlin

Retrograde chess puzzles solved with SAT, in Kotlin Multiplatform. A puzzle is a window of N plies
with constraints (start / target board, unknown squares, "a king or nothing stands here", move
patterns, mate / stalemate at the end). The encoder turns it into CNF, a SAT solver finds every line
that fits, and a reference move generator verifies each one. Web (Kotlin/JS, solver in a Web Worker)
and desktop (JVM) UI with a board editor, bundled example puzzles and a replay board, plus a JVM CLI.

Screenshots: `./gradlew :composeApp:jvmTest` writes them to `composeApp/build/screenshots/`.

## Requirements

The SAT solvers (ksat: Kotlin ports of MicroSAT, MiniSat, CaDiCaL, kissat) are built from a
**sibling checkout**: `sat-solvers-kotlin` must sit next to this repo (`../sat-solvers-kotlin`,
see `settings.gradle.kts`). Without it the build fails. JDK 21.

## Run

```bash
./scripts/runWeb.sh    # web dev-server on http://localhost:8608 (opens Chrome; NO_CHROME=1 to skip)
./scripts/runJvm.sh    # desktop app
./scripts/runCli.sh solve puzzles/chess-com-5/4.json --all --verbose   # command line
```

## Build

```bash
./gradlew :composeApp:jsBrowserDistribution   # static web app in composeApp/build/dist/js/productionExecutable
./gradlew :composeApp:packageDistributionForCurrentOS   # desktop installer
./gradlew :cli:installDist                    # CLI in cli/build/install/sat-chess
```

## Docs

- [doc/spec.md](doc/spec.md): what was asked for, decisions, puzzle format, UI
- [doc/encoding.md](doc/encoding.md): the SAT encoding

## License

MIT (see [LICENSE](LICENSE)), except the puzzles in `puzzles/` (credited composers' works, see
[puzzles/NOTICE.md](puzzles/NOTICE.md)) and the piece graphics (cburnett set, BSD 3-clause, see
[composeApp/NOTICE-pieces.md](composeApp/NOTICE-pieces.md)). The app's About dialog shows all
bundled license texts.
