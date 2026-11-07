# Platforms: build & run

The website is the main target. The desktop app and the CLI share the code and stay for testing.

| Target  | Gradle                        | Run                  |
|---------|-------------------------------|----------------------|
| Web     | `js { browser() }`            | `scripts/runWeb.sh`  |
| Desktop | `jvm()`                       | `scripts/runJvm.sh`  |
| CLI     | `:cli`                        | `scripts/runCli.sh`  |

The web dev-server port is **8608** (see `~/github/.dev-ports.md`), set in
`composeApp/webpack.config.d/watch.js` and mirrored in `scripts/runWeb.sh`.

## GitHub Pages

```bash
./scripts/deployPages.sh
git push origin gh-pages
```

`deployPages.sh` builds the production bundle (`scripts/buildWeb.sh`, output
`composeApp/build/dist/js/productionExecutable/`), copies it into the git worktree `.gh-pages/` of
the branch `gh-pages` and commits it locally. It does not push. Same procedure as ktoy.

One-time setup: create the repo on github.com and add it as `origin`, then Settings -> Pages -> Deploy from a branch -> `gh-pages` / (root).
URL: <https://manfredscheucher.github.io/sat-chess-kotlin/>

All paths in the bundle are relative, so it works under the `/sat-chess-kotlin/` subpath. The solver
worker `solver-worker.js` is part of the bundle (`syncSolverWorker`).

## Tests

```bash
./gradlew :composeApp:jvmTest :core:jvmTest
```

`UsabilityUiTest` covers the usability rules taken over from ktoy (menus, unsaved edits, Examples
dialog, stacked layout, replay). `ExampleCatalogTest` checks that every bundled example loads,
`PuzzleFilesTest` solves them (all of them only with `-PslowTests`).
`ScreenshotTest` writes screenshots to `composeApp/build/screenshots/`. The tests use their own
preferences node (`prefsNode`), not those of the desktop app.

`scripts/probe/web_e2e.py` drives the built website in headless Chrome (clicks, screenshots). It was
used to check the real worker in the browser and to make `doc/screenshot*.png`.
