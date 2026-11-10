# ksat-chess: specification log

Source of truth for what Manfred asked for, what was decided, and how ambiguous
requests were interpreted. A fresh reviewer should be able to check the
implementation against this file.

## 1. Original request (2026-10-20, paraphrased from German)

- Port `../sat-chess` (Python, SAT encoding of chess game paths, retrograde puzzles)
  to **Kotlin Multiplatform**. JVM is fine for testing; **web is the long-term target**.
- Use the **ksat interface** (`org.bytefred.ksat`, as in luckySweep) for solving in Kotlin.
- Build a **nice web page** (similar to `../ktoy`) where one can build chess boards / puzzles,
  with **bundled examples** ("load examples", configuration visible, boards editable).
- Think about the **best puzzle format**: puzzles are not only "start at A, k moves to B".
  A is often the initial board, but may be a custom position or **open** (asked for);
  B may be specified or open; k may be given in **moves or half-moves (plies)**;
  some puzzles ask "k moves until checkmate".
- Board editing: drag and drop, or click a square and get a dialog, or a **piece palette
  next to the board** (select a piece, click squares to place it; clicking the same piece
  again empties the square, otherwise overwrite). Settings can switch between input modes.
- Research licensing: Manfred wants to publish the repo under MIT. Can the chess.com
  (5 easy retrograde puzzles) and janko.at puzzles be bundled?
- Keep a project doc (this file) "as always".
- Use parallel agents / fresh reviewers to discuss the best approach.

## 2. Decisions (answers to Claude's questions, 2026-10-20)

| Topic | Decision |
|---|---|
| Encoding | Optimizing the encoding is allowed right away ("gern optimieren"). Kotlin performance is assumed to be within a small factor of native. |
| Python repo | Manfred first said "pull sat-chess along", then "you can leave sat-chess completely alone, the language doesn't matter for building the instance". **Interpretation: `../sat-chess` stays untouched.** The Kotlin port gets a JVM CLI that can export DIMACS, so hard instances can still go to native CaDiCaL/kissat. *(Flagged to Manfred; reversible.)* |
| Web target | Kotlin/JS + Compose (like ktoy) and the **solver runs in a Web Worker**, so the UI stays responsive, with cancel and progress. |
| Puzzle data | **Small hand-picked selection + credits**: the 5 chess.com puzzles (about 20 janko.at shorties were added at first and removed on 2026-11-09, see section 7). Only FEN, plies, composer/source/link. Descriptions are written by us, no copied prose. A separate `puzzles/NOTICE.md` says puzzle data is not covered by MIT and gives a removal contact. No bulk import (no yacpdb dump). |
| Model v1 | All of: free **side to move**, goal **checkmate**, goal **stalemate**, **castling rights / en passant** settable at A. Manfred: "you see what is needed, implement it all; design the web UI nicely, discuss with reviewers". |

## 3. Licensing research summary (not legal advice)

- chess.com ToS: site content is chess.com's, forum posts belong to their posters; no
  reuse rights for third parties. → Never copy article or forum text, diagrams or images.
- janko.at Impressum: "Copyright (c) 1999-2020 by Angela and Otto Janko. Alle Rechte
  vorbehalten." The WFCC Codex (hosted there) asks for composer + source + date on reprints.
- yacpdb: no license found → treat as all rights reserved; not bundled.
- Copyright of chess positions/moves is untested for composed problems; games/moves are
  generally treated as facts. EU database right only bites on substantial extraction.
- Practice: bundle FEN + stipulation + attribution, own descriptions, separate NOTICE,
  takedown contact.

## 4. Puzzle model and file format

A puzzle is a **window of N plies** (`t = 0..N`) over one game, with constraints.
All SAT answers are **existential**: "there is a line where ...". So a "mate in k" goal
means *both sides cooperate* (helpmate / proof game style). A real **directmate**
("White mates against every defence") needs a for-all quantifier and is **out of scope**.
*(Flagged to Manfred, who wrote "k moves until checkmate".)*

```json
{
  "id": "chess-com-5/4",
  "title": "Bishop sacrifice on e8",
  "composer": "Tibor Orban",
  "source": { "name": "chess.com article (M. Klein, 2017)",
              "url": "https://www.chess.com/article/view/can-you-solve-these-5-easy-retrograde-puzzles",
              "year": 2017 },
  "description": "Own short text.",
  "plies": 8,
  "start": { "board": "startpos", "sideToMove": "w", "castling": "auto", "enPassant": "-" },
  "positions": { "8": "rnbqkbnr/pp3ppp/2p1p3/8/4P3/8/PPPP1PPP/RNBQK1NR" },
  "squares": { "0": { "h4": "QRBNqrbn" } },
  "moves": { "0": "f3", "2": "Kf2", "5": { "piece": "p", "capture": true } },
  "goal": "none",
  "shortest": false,
  "project": ["moves", "board@0"],
  "expected": 1
}
```

- **Board strings**: FEN piece placement, `?` = unknown square (anything, including empty).
  All `?` / omitted = open. `startpos` = the initial position.
- `start.board` constrains t=0 (A). `positions` constrains any other ply (usually N = B).
- `squares`: optional per-ply **square domains**, e.g. h4 must hold one of `QRBNqrbn`
  (`.` = empty allowed). Keeps FEN strings pure. The key `"?"` is the domain of every `?`
  square of that ply's board (explicit square entries override it), e.g. "where is the white
  king?": board `8/8/8/1r1b4/B7/8/8/3k4` with all other squares `?` and
  `"squares": {"0": {"?": "K."}}` (each unknown square is a white king or empty; exactly one
  of them is the king). *(Requested by Manfred, 2026-10-21.)*
- `moves`: optional per-ply **move patterns**, either SAN (`"Kf2"`) or an object with any of
  `piece, from, to, capture, promo, castle, check`. Covers "White played f3, Kf2, Kg3, Kh4",
  "Black's last move was a capture", "White castled".
- `sideToMove` at A: `w`, `b`, or `?` (the driver solves twice, once per side). The side
  to move at B is derived and shown in the UI.
- `castling`: `auto` (a right exists iff king and rook are on their home squares; the
  Codex convention "castling is allowed unless disproved"), a FEN field (`KQkq`, `Kq`, `-`),
  or `?` (each right free, still requiring king and rook at home).
- `enPassant`: `-` or a target square (`e3`). No `?`: by Codex convention e.p. is only
  allowed when proved; to ask "was the last move a double push?" start the window one ply
  earlier. A given square also enforces the pawn on e4, e2/e3 empty, and Black to move.
- `goal` at ply N: `none`, `check`, `checkmate`, `stalemate` (all cooperative, see above).
- `shortest`: also prove that N−2 plies are not enough (UNSAT).
- `project` = what makes two solutions distinct; a list of ply-scoped items:
  `moves`, `board@t`, `square@t:h4`, `piece@t:K`, `sideToMove@0`.
  Default `["moves", "board@0"]` (= the whole path, since every board follows from A + moves).
  Puzzles like "where is the white king?" use `["piece@0:K"]`; "which piece on h4?" uses
  `["square@0:h4"]`.
- No rule switches (the `rules` flags were removed on 2026-11-09, old files with a `rules`
  key still load). Legal moves, exactly one king per side and the retro **history** of the
  position at ply 0 (pawn paths, captures, promotions, bishop colours; `doc/encoding.md`
  section 11) always hold.
- No `question` key (removed 2026-11-09, old files with it still load): the question is given
  by the boards, `plies`, `start` and `goal` alone.
- `expected`: known solution count under `project`, or null. Used by tests and shown in
  the results ("unique", "3 solutions, expected 3").
- UI shows plies and moves together ("13 plies = 6.5 moves"); the file always stores plies.
- Python `.puzzle` files convert 1:1 (`0` → `start.board`, other int keys → `positions`,
  `no_king`/`no_check`/`black_first`/`project_piece` → fields).

## 5. Encoding (changes vs. the Python encoder)

Ported semantics, with these optimizations / generalizations / fixes:

1. Move variables only for **geometrically possible (from, to) pairs** (queen lines +
   knight jumps, about 1,800 instead of 4,096 per ply). Exactly-one move per ply: keep
   strong propagation (benchmark against Python's row/col factoring before replacing it).
2. Validity and board-sync clauses only for pieces that can make that move shape.
3. Exactly-one king per colour with a sequential counter instead of 2,016 pairwise clauses.
4. Side to move is a parameter of the encoding (fixes Python's half-done `black_first`,
   which only changed the t=0 check test).
5. Castling rights and en passant are state variables at **every** ply incl. t=0 and t=N.
6. No pawns on rank 1/8 at any ply (Python allowed them from `?` squares).
7. Retro history at ply 0 (2026-11-09, `doc/encoding.md` section 11): pawn paths with
   capture squares, identity of every man, boxed bishops, pawns that cannot pass each other.
   Needed for Smullyan's h4 puzzle; replaces the material-count heuristic and the rule flags.
7. Auxiliary variables (AMO ladders etc.) are functionally determined where cheap;
   enumeration always blocks over the **projected** variables only. Exported DIMACS is
   for SAT, not for #SAT model counting, unless stated.
8. **Checkmate / stalemate at ply N**: encoded necessary conditions (check / no check;
   every king flight square own-occupied or attacked in an extra attack layer that treats
   the king's square as empty), then a **lazy refinement loop**: each candidate is checked
   by the reference move generator; non-mates are blocked over the full state at N
   (board, side, castling, e.p.) and solving continues. Refinement blocks and enumeration
   blocks are kept separate. Default refinement clause since 2026-10-21: `ESCAPE_MOVE`
   (block only the squares that make one legal reply legal; still exact, far fewer rounds). Exact; keeps the CNF small.
9. A reference **legal move generator** (perft-verified) is used for tests, the mate /
   stalemate check, SAN with disambiguation, and to **validate every solution**.
10. Castling clauses are explicit both ways (incl. king path not attacked).

## 6. Architecture

| Module | Targets | Content |
|---|---|---|
| `:core` | jvm, js | chess model, FEN, move generator, puzzle format (kotlinx.serialization), encoder, decoder, solver driver over `Ksat` |
| `:cli` | jvm | command line: solve a puzzle file, enumerate, write DIMACS, run the bundled set |
| `:worker` | js | Web Worker bundle: receives a puzzle as JSON, streams stats/solutions back |
| `:composeApp` | jvm, js | Compose UI. JVM solves on `Dispatchers.Default`, JS posts to the worker |

ksat modules are built from the submodule `sat-solvers-kotlin` (github.com/manfredscheucher/ksat,
projectDir remap). Web dev-server port **8608**.

## 7. Web UI

Reviewed by a fresh UX reviewer; decisions:

- **Input**: one hybrid mode by default. Pick a piece in the palette (12 pieces, empty,
  `?`), click squares to place it; clicking a square that already holds the selected piece
  empties it; otherwise it overwrites. Pieces on the board can always be dragged; dragging
  off the board removes them. Settings offer one alternative, "click opens a dialog",
  because Manfred asked for switchable input modes.
- **Question preset first** (Proof game / Last moves / Helpmate / Unknown piece / Custom; until
  2026-11-09 "Last move(s)?", "Helpmate in k", "Find unknown piece"). The preset fills A, B, N, goal and projection. (The "Advanced" section with
  solver choice, projection, rule switches, max solutions, Shortest and the square-domain editor
  was removed step by step on 2026-11-09.)
- **Plies vs moves**: one number with a unit toggle plus a live sentence, e.g.
  "13 plies · White starts · White moves last · Black to move at the end (B)" (until 2026-11-09 also "= 6.5 moves").
- **Timeline**: `(A)─1─2─…─(B)` chips; the icon shows fixed / partial / open (derived from
  the number of `?`, not a mode switch). Clicking a ply opens its board; "+" adds an
  intermediate board constraint or a move pattern.

Desktop:
```
[Examples][New][Import/Export][Share]                       [Settings]
Question: [Proof game v]  N: [13] plies|moves  Goal: [none v]  "6.5 moves ..."
Timeline: (A)--1--2--...--(B)
+ board (selected ply) +  + palette 6x2 + empty + ?  +
|                      |  | [Initial][Clear][All ?]  |
+----------------------+  | FEN: [..............]    |
                          +--------------------------+
[ Solve ]  time - found 2   [Cancel]       [Advanced v]
Results: answer summary / solution list (SAN) -> replay board
```

Phone width: single column; timeline scrolls sideways; full-width board; palette as a
2x7 strip below; Question and Advanced as collapsible cards; sticky Solve/Cancel bar;
results as their own view.

- **Results**: for non-path projections show the answer first ("h4: Rook (3 lines),
  Queen (1)"), expandable to lines. Replay with slider, clickable SAN list, arrow keys,
  from/to highlight. "unique" / "N solutions, expected M"; every solution is replayed with
  the move generator, and the count line says "(k NOT verified)" in red if one fails.
- **Square options** (Manfred, 2026-10-21, on chess.com #2 "only the king is missing"):
  instead of a bare '?', a square can hold a list of options (pieces and/or "empty"),
  edited via checkboxes (dialog or next to the board); it must be VISIBLE whether a piece
  is placed fixed or only possible there. Stored as `squares` domains; key "?" = domain
  for all '?' squares of that ply.
- **Square options as built (2026-10-21)**: options dialog via palette tool "Options ...",
  long press / right click, or "Several options ..." in dialog mode; shortcut buttons Any /
  Kings or empty / White king or empty / Any piece / None; one checked option = fixed square,
  all 13 = plain '?'; a line under the board sets the "?" default. Option squares are
  amber, hatched, with faded mini icons; legend below the board.
- Default solution limit 100 (Claude's proposal, "all" selectable). Worker bundle is
  production by default, `-PworkerVariant=development` for `runWeb.sh`. About shows full
  license texts generated from the license files (`generateLicenses`). Arrow keys step the
  replay without focusing it first.
- **Group similar solutions** (Manfred, 2026-10-21): the plain list stays the default; a
  toggle groups solutions. Per move an event string "piece X goes to square Y (captures Z)"
  with full piece identity (colour + type), not bare SAN like "Nxf5". White events and
  black events are collected separately and each sorted; key = (sorted white, sorted
  black). Identical keys = same events in another move order = one group. White and black
  are never mixed. Since 2026-11-09 the start position (full FEN at ply 0) is part of the key (Manfred: with an open
  A, as in "what was the last move?", lines from different start boards must not be grouped). Implemented literally; open question to Manfred: should a promotion
  piece be part of the event? (not included unless he says so)
- **As built (UI agent, 2026-10-21)**: >=1200 dp three columns (timeline + board + palette
  below the board | question, start, advanced, solve | results); 900-1200 dp two columns;
  phone: Puzzle/Results tabs, sticky Solve bar, collapsed question summary. Palette 2x7.
  Presets never drop a board the user entered. N accepts plies or moves ("6.5").
  Share = `#puzzle=<url-encoded JSON>`; imports and share links are validated. Max
  solutions defaults to "all". Worker bundle `solver-worker.js` is copied into the web
  resources by `syncSolverWorker`.
- Square options design confirmed by Manfred (2026-10-22, "4 passt").
- **chess.com #2 history** (Manfred, 2026-10-22, suspected a port bug since Python found a
  unique e.p. history): not a port bug. On the same 2-ply probe Python and Kotlin both give
  405. 2 plies are too short (e.p. at A is "-"); with a 3-ply window and "K or empty"
  domains the answer is unique: white king c3 via 1. c4 bxc3 e.p.+ 2. Kxc3+.
  Puzzle file uses 3 plies, expected 1, regression test `whiteKingNeedsEnPassantHistory`.
- **Worker**: the solver call is synchronous, so progress is per solution, not mid-solve;
  Cancel = `worker.terminate()` and respawn.
- **Share**: the puzzle JSON goes into the URL hash; Import/Export as a JSON file.

- **GitHub page and ktoy usability** (Manfred, 2026-11-07: "similar usability to ../ktoy, mainly a
  GitHub page, other targets don't matter for now; prepare it with examples, docs, tests, branch";
  on the feature list "all of it, you see my prompts there, similar UI please"). Repo name
  `sat-chess-kotlin`, solvers as a submodule. Taken over from ktoy, and this replaces the top bar,
  Settings dialog, phone tabs and full-screen example browser above:
  - `scripts/buildWeb.sh`, `scripts/deployPages.sh`, branch `gh-pages` via worktree `.gh-pages/`,
    no push (doc/platforms.md).
  - Menu bar File / View / Help without action buttons; one click switches menus, a click on the
    open title or Escape closes; scrolls sideways on phones. Settings moved to View.
  - "Save changes?" before New / Load / Examples only after real edits; a solver run is no edit.
  - Visible draggable scrollbars in all panels and long dialogs.
  - Narrow screens: no tabs, sections stacked, Solve scrolls to the results. The sticky Solve bar
    stays (Claude's decision, ktoy also keeps its controls visible).
  - Wide screens: draggable dividers. View -> Layout auto / portrait / landscape.
  - Examples dialog: collapsed tree, a click selects, Load loads, reopens as it was left.
    Deviation from ktoy (Claude's decision): the selected example also shows its board, credits and
    source link, because a puzzle title alone says little and the credits should stay visible.
  - Replay controls in ktoy's order: Reset, Back, Step, Run. Run steps once per second.
  - Inputs as low as buttons (`CompactField`), neutral backgrounds (the bundled DejaVu Sans Mono
    was dropped again on 2026-11-09).
  - View choices and the welcome dialog are remembered (`Prefs`, localStorage on the web).
  - Welcome dialog with feedback box, About with links, Help texts.
  - Not taken over because ktoy's web version has none: tab-close guard, browser back handling.
  - Beyond ktoy (found by the reviewer): opening another puzzle removes `#puzzle=` from the URL, and a
    share link pasted into the running page opens it.
  - Known limit, same as ktoy: the web Save cannot see a cancel in a browser "save as" dialog, so
    the app then counts the puzzle as saved.
  - Trimmed the longest example descriptions (Fool's mate, chess.com #2, #5).
- **Examples and solver cleanup** (Manfred, 2026-11-09):
  - "nimm die janko sachen raus": the 20 janko.at shorties are removed, also from the git history
    (rewritten before the first push; local backup tag `backup/before-janko-removal`). chess.com
    puzzles and demos stay.
  - Demos no longer say "Own demo"; first relabelled "Well-known" / "Standard opening" / "Rule
    demo", then (Manfred: "des is immer nur arrogant … sei normal") the demos carry no source or
    composer line at all.
    Manfred asked where demo 02 (knight on f7) comes from: Claude made it up on 2026-10-20 as a
    minimal "last move" exercise, no source. Decision (Manfred, 2026-11-09): keep it.
  - The loaded puzzle shows a "Source" link next to its credits (before: only in the Examples dialog).
  - "lass die solver auswahl weg": no solver choice in the UI, it always uses kissat (as fast as or
    faster than CaDiCaL in every measurement in `encoding.md`, and incremental).
  - "Projection (what makes two solutions distinct)" field removed from Advanced ("weg damit"):
    nobody understood `board@0` / `square@0:h4`. The puzzle file keeps `project` (set by the
    question preset and the examples); "Group similar solutions" in the results stays.
  - **Smullyan's h4 puzzle really solved** (Manfred: "du sollst das 5er lösen, du hast ja sat …
    schummeln geht gar ned"). chess.com #5 had been shipped with 13 "answers" and the check rule
    switched off. Now: retro history in the encoder (section 5 item 7, `doc/encoding.md` section
    11), #5 as a 2-ply window with the diagram at ply 2 and h4 = any piece, answer h4 = B, unique.
    The rule switches "Check rules" / "Exactly one king" / "Material sanity" are gone from the UI
    and the format: no real puzzle needs illegal moves, and the history subsumes the other two.
  - **No spoilers** (Manfred, 2026-11-09: the descriptions gave away knight tours and the en
    passant capture; "richt dich nach chess.com"): titles and descriptions of the chess.com
    puzzles now restate the article's questions in our own words and nothing more.
  - **Plain language** (Manfred: "kein mensch redet so … ktoy kennst ja auch"): Welcome, Help,
    hints, demo descriptions and the README intro rewritten in short, direct sentences like
    ktoy's; no "ask how it came about", no "existential", no "PG" in the plies line. A language
    reviewer then removed the rest: "contents", "window", "domains", "constraint", "line" (now
    "solution"), "Helpmate in k" (now "Helpmate"), the chess.com questions closer to the article.
  - **Simpler start, simpler screen** (Manfred, 2026-11-09): no en passant field in the UI (a
    puzzle that needs it starts one ply earlier with both boards given, as demo 04 does now; the
    file field `start.enPassant` stays for the solver and the tests); castling is four checkboxes
    "Available castling options" (first "May still castle") (ticked = what the board allows; unticking stores explicit rights, the
    "? (free)" mode is gone from the UI, chess.com #2 uses auto); columns reordered: question,
    start and advanced on the left, timeline and board in the middle, Solve above the results on
    the right; editor and replay board share one width (420 dp); Edit menu with Undo / Redo
    (Ctrl/Cmd+Z, Shift for redo, 100 steps, a run of N keystrokes is one step); the bundled
    monospace font is gone ("brauchst ja nie nen andren font typ"), with it the resources plugin
    and the DejaVu notice.
  - **Demo "Castling not allowed"** (Manfred, 2026-11-09): the castling demo with the rights
    switched off, expected 0, so the effect of a moved king or rook is visible. Chess960 castling
    is noted as an idea in section 8.
  - **Static solution view** (Manfred, 2026-11-09): View -> Solutions, animated (the replay
    board with Reset / Back / Step / Run and the slider) or static (all k + 1 boards of the
    solution, ply 0 included, in a grid with ceil(sqrt(k + 1)) columns); a "Show static" /
    "Show animation" button above the boards toggles the same setting, which is remembered.
  - **No Advanced section** (Manfred: "das ganze advanced kannst wegschmeißen"): the solver
    always enumerates all solutions, the Solve button turns into Cancel while it runs; "Shortest"
    and the square-domain editor are gone from the UI (`shortest` and `squares` stay in the file
    format; the board's "one of several" options still write `squares`). The timeline moved to
    the bottom of the left column, the puzzle header sits in a card like the sections, and the
    demos carry no source line.
  - About like ktoy: one line per part with link and license (app, ksat, cburnett pieces,
    chess.com article); the full license texts only behind "License texts". Developer notes
    on the piece graphics moved from `composeApp/NOTICE-pieces.md` to `doc/pieces.md`; the app
    ships only the plain license text (`doc/licenses/LICENSE_CBURNETT.txt`). `puzzles/NOTICE.md`
    is no longer shown in the app.

### Big dialogs like ktoy (Manfred, 2026-11-09)

From the comparison of ktoy, ksat-chess and the ksat playground, Manfred chose "alles"; for
ksat-chess that is ktoy's big dialogs. Help (Puzzles, Board input, Results), About and Examples
now fill the window but a 16dp margin (`BigDialog.kt`, ported from ktoy): title on top, content
in between with its scrollbar, buttons at the bottom right. The help texts are unchanged. The
small dialogs (Save changes?, Welcome, square/options dialogs) stay AlertDialogs. Tested by
`BigDialogTest` at 1280x800 and 360x640.

### Help texts as flowing text (Manfred, 2026-11-09)
The Help pages are flowing paragraphs that wrap at the dialog width (no fixed line breaks, no
horizontal scroll). Wording unchanged. A subheading (first line of a paragraph with more lines,
e.g. "Palette + drag") is bold instead of indenting its body. Tests: `helpTextUsesTheFullWidth`,
`subheadingsAreBold` in `BigDialogTest`.

### Question dropdown removed (Manfred, 2026-11-09)

Manfred did not understand what the "Question" type (Proof game, Last moves, Helpmate, Unknown
piece, Custom) was. It was a preset that filled a few fields once and had no meaning afterwards;
the solver never read it. Decision: remove it entirely (dropdown, `applyPreset`, the `question` key
of the format; old files with the key still load). The question is given by the boards, N, the
start and the goal alone. `GuiBuildChessComTest` now sets the goal through the Goal dropdown
(testTag `goal`) instead of the helpmate preset.

### Goal "none" is "any" (Manfred, 2026-11-09)
"none" read as "neither check nor mate allowed"; the label is now "any". Manfred's further idea,
to put the goal next to the board so a goal can be set at intermediate plies too, is left for later
on his say-so (section 8).

### A board click without a pick does nothing (Manfred, 2026-11-09)
Manfred: clicking the board in the middle column edited it right away (a white pawn was
preselected). Now nothing is selected in the palette by default and a click does nothing until a
piece, empty, `?` or the options tool is picked; clicking the picked one again deselects it. Drag
and drop, long press / right click and the dialog input mode are unchanged.

### One Setup group, plies only, two timeline buttons (Manfred, 2026-11-09)

Manfred's layout for the left column, implemented literally:
- One group "Setup" with: side to start (White / Black / either), the four castling checkboxes,
  the number of plies (plies only; the plies/moves unit toggle is gone from the UI, the format
  never had it) and the goal at the end (dropdown "any / check / checkmate / stalemate"). The
  former "Question" and "Start (A)" sections are merged into it.
- The "Timeline" group keeps the chips and its two actions, but as two real buttons instead of
  the "+" menu: "Edit board at ply ..." and "Add move pattern ...". Manfred suggested "Fix board
  at ply .." / "Fix move pattern"; a fresh reviewer said "Fix" reads as "repair" to a native
  speaker, so the verbs are "Edit" and "Add".
- Default pick in the palette is "One of several ..." (a click on a square opens its options);
  the earlier "nothing picked" default from the same day is superseded.
- From the reviewer, also taken: field label "Plies (half-moves)", sentence "... to move at the
  end (B)", a clearer chip legend, move pattern chips "Move A → 1: f3", helpmate note in body
  size. Not taken (Manfred's words stand): "any" for the goal, "Available castling options",
  "Goal at the end".

### Board title uniform (Manfred, 2026-11-09)
"Board at A (start)", "Board at 2 (after ply 2)", "Board at B (after ply 4)": the same shape for
every ply; before, A and B had no bracket.

### No info lines under Setup (Manfred, 2026-11-09)
The sentence "4 plies · White starts · ..." only repeated the inputs and the helpmate note
("Both sides cooperate ...") is covered by Help; both are gone. Only the error text for an
invalid plies number remains.

### Timeline as a list of boards and plies, move editor in the middle column (Manfred, 2026-11-09)

Manfred's redesign, implemented as described: the timeline is a vertical list

    Board 0 (start)
      Ply 1
    Board 1
      Ply 2
    ...
      Ply k
    Board k (end)

A board row shows that board in the middle column (as before), a ply row shows a **move editor**
there instead of the board: what the move from board t to t+1 must be. Fields, all optional,
written to the puzzle as you type: the move in notation ("Kf2", "exd5"), piece (chips with the
pieces of the side to move, both colours if either side may start), from / to square, capture
(any / yes / no), promotion, castling (no / yes / O-O / O-O-O), and "After this move": for the
last ply this is the goal at the end (the same value as the Setup dropdown, kept in sync because
it is one field), for the other plies "any / check" (pattern `check`). A "Clear" button drops the
pattern. The two timeline buttons, the "+" menu, the ply-picker dialog, the move-pattern dialog
and the chip legend are gone. Rows that constrain the puzzle (a board with known squares, a ply
with a pattern or the goal) are bold on a tinted background with a short detail ("given",
"partly given", "Kf2", "checkmate"); the selected row is filled.

Names: "Board t" with "(start)" / "(end)", "Ply n" with n = t + 1 for the move from board t to
t+1 (the file keeps the 0-based pattern key t). Manfred offered "action" as an alternative to
"ply"; "ply" was kept because the Setup field already counts plies. The letters A and B are gone
from the UI (titles, options dialogs, static solution grid, Help).

Also fixed from the reviewer's list (section 7 above, items the reviewer called plain bugs or
stale): the View menu check mark is drawn (the "✓" glyph rendered as a box on the web), the hint
under the palette and Help > Board input no longer claim "nothing picked, nothing happens", the
Welcome sentence covers forward puzzles too, Help no longer talks about "N".

### Solve bar without vars/clauses, no verified badge (Manfred, 2026-11-09)

Manfred on the reviewer's list: "vars und clauses sind mir wurst; verified badge auch weg; du
sollst die GUI ja minimal halten ... die arrows sind schon cool, bitte drin lassen". The solve
bar shows only "encoding ..." / elapsed / found / state; the per-solution "verified" chip is
gone (the count line still turns red with "(k NOT verified)" if a replay ever fails, which is
a solver bug, not information for the user). The "(arrow keys to step)" hint stays.

### Less in the editor: no dialog input mode, '?' default only when needed (Manfred, 2026-11-09)

Manfred: "mach wie du glaubst" on the rest of the reviewer's list, then "dritter weg macht aber
keinen sinn denk 2 reichen". So: the View > Board input mode "Click opens a dialog" and its
per-square dialog are gone (the options dialog is reached by the palette tool or long press /
right click); the line "Every '?' square: ... [Change ...]" shows only while the board has a
'?' square; the collapsed Setup card on the phone reads "4 plies · ends in checkmate" (nothing
after the plies when the goal is "any"); the FEN label stays short.

### Columns input for the static solution view (Manfred, 2026-11-10)

Manfred: "für die static anzeige noch nen integer input machn, für anzahl der spalten, mit
default setzn (über wurzel berechnen), danach wert merken ... solang bis neues spiel bzw ply zahl
geändert wird". A "Columns" field above the static grid shows ceil(sqrt(k + 1)) until the user
types a number; that number is kept until another puzzle is loaded or the number of plies changes
(typing, undo or redo), then the default applies again. Not stored across reloads. Claude's
reading: a valid value is a whole number from 1 to the number of boards (k + 1); anything else
shows an error and the grid keeps the last valid value.

### Start with a new puzzle, "How to start" in the Welcome dialog (Manfred, 2026-11-10)

Manfred: "by default immer mit new starten, also nicht fools mate als start example ... beim ersten
start sagen sowas wie how to start: look at one of the examples, encode a board by yourself to
figure out how you got there ... auch darauf verweisen dass in help erklärung gibt ... darunter
erst die email ... direkt nach der erklärung was das tool ist". The app opens "New puzzle" (a
shared link still wins). The Welcome dialog: what the tool does; "How to start: look at one of
the examples (File > Examples), or encode a board yourself to figure out how you got there.";
"If something is unclear, Help explains the puzzle, the board input and the results."; the repo
link; then the feedback box with the e-mail.

## 8. Open points / follow-ups

- Idea (Manfred, 2026-11-09): the goal belongs to the board, not the Question row, so a goal
  (check, mate, stalemate) could be set at intermediate plies too. That is a format change
  (`goal` per ply) and an encoder/refinement change; deferred by Manfred ("erstmal so lassen").


- Repo renamed to `ksat-chess` on GitHub (Manfred, 2026-11-09); Pages URL
  https://manfredscheucher.github.io/ksat-chess/. The app is called ksat-chess too (Welcome, About,
  page title, feedback mail), the Welcome dialog links to the repo, `rootProject.name` is
  `ksat-chess`; only the local folder keeps its old name.

- Idea (Manfred, 2026-11-09): Chess960 castling, with a general rule "castling is possible when
  the king stands between its two rooks and neither has moved", plus a Chess960 example. Not
  built; the encoder's castling geometry is fixed to e1/e8 and the corner rooks.

- Public release: the sibling projectDir remap does not work for strangers cloning the repo. Decided
  (2026-11-07): `sat-solvers-kotlin` becomes a submodule; `git submodule add` is Manfred's job, then
  `settings.gradle.kts` and the README switch to it. Done 2026-11-09.
- Piece graphics: cburnett set (BSD/GPL/GFDL tri-licensed, used by lichess), see `doc/pieces.md`.
