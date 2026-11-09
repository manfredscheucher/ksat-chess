# SAT encoding of chess game paths

How `core/.../sat/` turns a puzzle (doc/spec.md section 4) into CNF, solves it and decodes the
answers. The plan Manfred agreed on is doc/spec.md section 5; this file documents what was built,
the measured effect of the changes against the Python encoder (`../sat-chess/sat_chess/encoder.py`),
and the known limitations.

| File | Content |
|---|---|
| `Cnf.kt` | growable clause store (flat `IntArray`), DIMACS writer, small encodings (AND/OR definitions, pairwise AMO, exactly-one ladder, unary counter) |
| `Geometry.kt` | the 1,792 geometrically possible (from, to) pairs, rays, castling squares |
| `AttackLayer.kt` | lazily built "square attacked by colour" variables for one board |
| `Encoding.kt` | the variables and clause families below |
| `MovePatterns.kt` | SAN / object move patterns as constraints |
| `Projection.kt` | projected variables, blocking clauses, the human-readable projected key |
| `Decoder.kt` | model to `Solution`, verification with the reference move generator (`chess/Rules`) |
| `Solve.kt` | `PuzzleSolver`: passes per side to move, enumeration, refinement, shortest check |

## 1. Variables

Plies `t = 0..N`; the move of ply `t` leads from board `t` to board `t+1` and is made by
`side(t)` (the side to move at ply 0 alternating). The side to move at ply 0 is a **parameter**
of the encoding: `sideToMove: "?"` builds two encodings and solves them one after the other.

| Variable | Count | Meaning |
|---|---|---|
| `at(t, sq, p)` | 64 × 13 per board | square holds piece `p` (0 = empty, 1..12 = `Piece` ordinal + 1) |
| `castling(t, r)` | 4 per board | castling right `r` still exists (state at every ply incl. 0 and N) |
| `enPassant(t, f)` | 8 per board | the side to move at `t` may capture e.p. on file `f` (state at every ply) |
| `move(t, pair)` | 1,792 per ply | ply `t` moves along this (from, to) pair |
| `source(t, sq)`, `target(t, sq)` | 64 + 64 per ply | exact ORs of the move variables leaving / entering `sq` |
| `promotion(t, i)` | 4 per ply | promotion to Q, R, B, N |
| `castleMove(t, r)` | 2 per ply | ply `t` castles with right `r` (only the mover's two rights) |
| `epCapture(t)` | 1 per ply | ply `t` is an e.p. capture (plus one AND variable per possible e.p. capture) |
| `attacked(t, sq, c)`, rays | lazy | see section 3 |

## 2. Clause families

Per board `t = 0..N`:

- **Square content**: exactly one of 13 per square (one at-least-one clause + 78 pairwise AMO).
- **No pawns on rank 1 / 8** (unit clauses). *Python allowed them on `?` squares.*
- **One king per colour**: exactly-one ladder over the 64 king variables,
  `s_i <=> s_(i-1) OR x_i`, `x_i => NOT s_(i-1)`, `s_64`. 63 auxiliaries and about 250 clauses per
  king instead of Python's 2,016 pairwise clauses. (Implied by the history at ply 0 and by king
  safety afterwards; stated per ply for propagation.)
- **Castling invariant**: `castling(t, r) => king and rook of r on their home squares`.
- **King safety**: the king of the side *not* to move is not attacked by
  the side to move. At `t = 0` this is the "the side that just moved is not in check" rule, at
  `t > 0` it is "a move may not leave the own king in check".
- Puzzle constraints: board patterns (`start.board` at 0, `positions`), square domains (one
  clause per square; the `"?"` key adds that clause for every `?` square of the ply's board
  that has no entry of its own).

Per ply `t < N`, mover `c = side(t)`:

- **Move choice**: `source(sq) <=> OR move(sq, *)`, `target(sq) <=> OR move(*, sq)`, and exactly
  one move (see section 4 for the two variants).
- **Validity**, per pair: `move => some piece of c that can make this shape stands on from`
  (queen + rook or bishop on lines, king on adjacent pairs, knight on jumps, pawn pushes / captures
  in c's direction, the king on castling pairs); `move => every square between is empty`.
  Per capable piece: pawn push needs an empty target, pawn capture needs an occupied target or
  (from the e.p. row) `enPassant(t, file)`; the castling pair with the king on e1/e8 forces
  `castleMove`. `target(sq) => no own piece on sq`.
  *Python emitted validity and sync clauses for every (pair, piece) of all 4,096 pairs.*
- **Board sync**: `move => from is empty at t+1`; per capable piece `move AND piece on from =>
  piece on to at t+1`; for pawns reaching the last rank `promoting <=> move AND pawn on from`,
  `promoting AND promotion(i) => promoted piece on to`; `promotion(i)` exactly one iff some
  `promoting` holds, all false otherwise (so they are functionally determined).
- **Frame axiom**: per square and piece value, `source OR target OR special OR NOT at(t) OR
  at(t+1)`, where `special` lists the castling moves whose rook uses this square and the e.p.
  captures whose victim stands here (inline literals, no extra variables).
- **En passant**: `enPassant(t+1, f) <=> double push on file f at t` (move variable AND pawn on
  the start square); `epc <=> move AND pawn AND enPassant(t, f)`, `epc => victim square empty at
  t+1`; `epCapture(t) <=> OR epc`. At `t = 0` from `start.enPassant`: `-` sets all false; a
  target square sets that file true and also forces the pushed pawn, the two empty squares
  behind it, and the opponent of the pusher to move (otherwise the pass is UNSAT).
- **Castling rights**: `castling(t+1, r) <=> castling(t, r) AND NOT source(king home) AND NOT
  source(rook home) AND NOT target(rook home)`. At `t = 0`: `auto` = right iff king and rook at
  home; `-` none; a subset of `KQkq` = exactly those; `?` = free (still needs king and rook at home).
- **Castling move** (both ways): `castleMove(t, r) <=> move(king home, king target) AND
  castling(t, r)`; it forces the squares between king and rook empty, king start / transit /
  target not attacked, the rook on its target at `t+1` and its home empty.

At ply 0 only: the **history** (section 11), which replaced the earlier material-count check
on 2026-11-09. Later plies inherit it, because moves only remove men or turn a pawn into a piece.

At ply N: the goal, see section 5.

## 3. Attacks

`attacked(t, sq, c)` is the exact OR of: a knight / king / pawn of `c` on the matching
neighbour squares, and eight **ray variables** `ray(t, sq, d, c) <=> slider(next) OR
(empty(next) AND ray(t, next, d, c))` (rook/queen orthogonally, bishop/queen diagonally).
That is 4-5 clauses per ray variable and linear in the board size; Python built one auxiliary
per (target, attacker square, piece) with clauses as long as the blocking path.
Attack variables are created **lazily**, only for the squares something asks about: king
safety needs them for one colour on all squares, castling for six squares of the other colour,
goals and check patterns at single plies.

## 4. Exactly one move per ply: benchmark

Two variants are implemented (`EncoderOptions.moveAmo`, CLI `--move-amo`):

- `ROW_COL` (Python's factoring, restricted to the possible pairs): one-hot from-row, from-col,
  to-row, to-col (8 each, pairwise AMO); `move <=> AND of its four factors`; one at-least-one
  clause over all move variables (needed because impossible factor combinations have no move).
- `SRC_TGT` (default): `source` and `target` one-hot, each via a row / column product
  (`source(sq) => row(sq)`, `source(sq) => col(sq)`, pairwise AMO over 8 rows and 8 columns),
  at least one source, and `source(a) AND target(b) => move(a, b)` for every possible pair.

Measured on JVM with `scripts/probe/bench_encoding.sh` (CLI, `/usr/bin/time`), 2026-10-21.
The machine was heavily loaded by parallel builds (load average 15-60), so wall times are
unreliable; the table shows **CPU (user) seconds** of one run each, incl. JVM start-up.

| variant | clauses (perft 3 / puzzle 4) | perft 3 (8,902) CADICAL | MINISAT | KISSAT | puzzle 4 (8 plies, all) CADICAL | MINISAT | KISSAT |
|---|---|---|---|---|---|---|---|
| `SRC_TGT` | 89,683 / 224,222 | **21.7** | 13.1 | 9.0 | **28.0** | 15.7 | **14.4** |
| `ROW_COL` | 110,428 / 279,542 | 42.3 | **8.3** | **7.9** | 152.9 | **11.9** | 17.3 |

A first run (wall clock, also under load) gave the same picture: CaDiCaL `SRC_TGT` 27.5 s /
37.7 s, `ROW_COL` 136.8 s / >280 s (timeout). **Decision: `SRC_TGT` is the default**: it is 2-5x
faster with CaDiCaL (the default solver), while `ROW_COL` wins by 10-35 % with MiniSat and is
mixed with Kissat. `ROW_COL` stays selectable (`--move-amo row-col`) for further measurements
on a quiet machine.

## 5. Goals, refinement loop

- `check`: `inCheck(N)` where `inCheck(t) <=> OR_sq (own king on sq AND sq attacked)`.
- `checkmate` / `stalemate`: only **necessary conditions** are encoded: in check / not in check,
  and for the king on `sq` every neighbour square is own-occupied or attacked in an extra attack
  layer that treats the king's square as empty (so the king cannot step back along the checking
  line). Each model is then checked with `Rules.isCheckmate` / `Rules.isStalemate` on the final
  position. A candidate that fails is excluded by a **refinement clause** over the full state at
  ply N (all `at(N, *, *)`, castling and e.p. variables; the side is fixed per pass) and the same
  incremental solver continues. Refinement clauses and enumeration clauses are separate (a
  refinement clause never removes a real mate). The driver reports the number of refinements as
  an `Info` event. Exact, and the CNF stays small; the price is extra solver calls when many
  checks are not mates.
- `SolveOptions.refinement` (CLI `--refinement`) selects the refinement clause:
  - `FULL_STATE` (as first written in the spec): the full state at ply N, about 850 literals.
  - `ESCAPE_MOVE` (default since 2026-10-21; exact, 5-13x fewer refinement calls): take one
    legal move of the side to move (not castling or e.p., which depend on rights / e.p. state; if only such moves exist, or the
    side to move does not have exactly one king, fall back to the full state) and block only the squares that make it legal: from, to, the squares between,
    the king square after the move, its knight and king neighbours, and each of its 8 rays up to
    and including the first piece (on the board after the move; every such square has the same
    content before the move except from / to, which are in the clause). Every position that
    agrees on these squares has the same legal move, so the clause is still exact, but it excludes
    whole families of non-mates at once.

  Fool's mate (startpos, 4 plies, checkmate, project moves, 8 solutions):

  | refinement | solver | refinements | CPU (user) |
  |---|---|---|---|
  | FULL_STATE | KISSAT | 185 | 5.6 s |
  | FULL_STATE | CADICAL | 185 | 9.9 s |
  | ESCAPE_MOVE | KISSAT | 14 | 3.9 s |
  | ESCAPE_MOVE | CADICAL | 19 | 6.6 s |

  (JVM, measured with `/usr/bin/time` while other builds ran; CPU time includes JVM start-up.)
  The tests use `ESCAPE_MOVE`; `GoalTest` checks both modes against a brute-force oracle.

## 6. Enumeration, projection, passes

- One incremental `Ksat` per pass (CADICAL, KISSAT support clauses added after a solve; MICROSAT
  does not, and MINISAT runs with its default option pre=1, whose first solve eliminates variables,
  so these two are rebuilt from the stored clauses for every call).
- After a solution, a **blocking clause over the projected variables only** is added. Projected
  variables come in one-hot groups (the move of a ply, the content of a square) blocked by
  negating the true member, and free variables (promotion choices, `piece@t:K` squares) blocked
  with full polarity. Auxiliary variables are never part of a block, so they cannot multiply
  solutions.
- `moves` projects the move and promotion variables of every ply, `board@t` the 64 × 13 square
  variables of ply `t`, `square@t:h4` one square, `piece@t:K` the 64 variables of that piece,
  `sideToMove@0` only the pass.
- `sideToMove: "?"`: two passes (white, then black). Unless `sideToMove@0` is projected, every
  solution of the first pass is also blocked in the second pass (translated by variable names),
  so a projected value found with either side counts once. Move and promotion names include the
  mover's colour (`mv0w:…` vs `mv0b:…`): White's Ra2-a1 and Black's ...Ra2-a1 are different
  moves, so with `moves` projected nothing is blocked across passes (a pass-1 solution that has
  a name the second encoding lacks cannot agree with any of its models and is skipped). Board,
  square and piece names are colour-free, so e.g. `board@0` counts a board found with either
  side once. (Fixed 2026-10-21: before, a black move with the same from / to as a white move of
  the first pass was dropped.)
- The projected key shown to the user is the SAN line for `moves` (two different moves can have
  the same SAN when the board is not projected), the placement for `board@t`, `h4 = R` /
  `h4 = empty` for squares, `K on e1` for pieces.
- `shortest`: after enumeration (only if something was found) the puzzle is solved again with
  `plies - 2`: constraints and projection items at ply 0 stay, those at the last ply move to the
  new last ply (with `plies = 2` they are merged into ply 0: boards cell by cell, domains
  intersected; a contradiction means no 0-ply solution), those in between and move patterns
  are dropped. Dropping makes the check a **relaxation**: UNSAT still proves "shortest", but a
  SAT answer only refutes it when nothing was dropped. `Done.shortestCheck`:
  `PROVEN` (UNSAT), `NOT_SHORTEST` (SAT, nothing dropped), `NOT_PROVEN_RELAXED` (SAT of the
  relaxed puzzle: neither proven nor refuted), `CANCELLED`. `Done.shortest` stays as a flag:
  true = `PROVEN`, false = anything else, null = not checked.
- Every path of `PuzzleSolver.solve` ends with `Done` or `Failed` (the UI waits for one); input
  problems are reported up front by `puzzleProblems(puzzle)` (also used by the UI), any other
  exception becomes `Failed("internal error: …")`.
- Cancellation is polled between solver calls (a single solver call is not interruptible).
- Exported DIMACS (`cli solve --dimacs`) is the plain CNF of a pass plus `c ind ... 0` lines
  listing the projected variables. It is meant for SAT solving; it is not a #SAT instance for
  counting unless a projected model counter uses the `c ind` lines (auxiliary variables are
  functionally determined except the free board / castling variables that the puzzle leaves open).

## 7. Move patterns

`moves` entries constrain single plies. SAN cannot be resolved without the board, so it becomes
constraints: piece letter (mover's colour) => `source(sq) => that piece on sq`; target square;
`x` => target occupied or e.p., no `x` => quiet move (strict); promotion piece; `O-O` / `O-O-O`
=> the castling move (and any other SAN forbids castling); disambiguation file / rank =>
allowed source squares; `+` or `#` => `inCheck(t+1)`. A missing `+` leaves the check free
(tolerant), and `#` only requires check: mate is not encoded per ply. Object fields: `piece`
(FEN letter, colour-specific), `from`, `to`, `capture`, `promo`, `castle` (`K`, `Q`, `k`, `q`
or `any`), `check`.

## 8. Verification

Every solution is replayed with the reference move generator: each decoded move must be in
`Rules.legalMoves` of the decoded position and `Rules.apply` must give exactly the next decoded
position (board, side, castling, e.p.), the start must not have the side not to move in check,
and the goal must hold. `Solution.verified` reports the result; the tests
require it for every solution.

## 9. Measurements

Encoding size, chess.com puzzle 4 (startpos to `rnbqkbnr/pp3ppp/2p1p3/8/4P3/8/PPPP1PPP/RNBQK1NR`,
8 plies): **29,742 variables, 224,222 clauses** (encoded in about 0.1 s) versus 1.58 M clauses in
the Python encoder (7x smaller). Python with native CaDiCaL took about 14-45 s for that puzzle;
the Kotlin ports need 12-28 s CPU (table in section 4), i.e. within the "small factor of native"
assumed in the spec.

`cli run` over the bundled puzzles (Kissat, 2026-10): chess-com-5/1 (4), /4 (1) match `expected`;
/3 finds 2 lines; /2 (`piece@0:K` over an open board, before it got its 3-ply definition)
enumerated tens of thousands of king sets. /5 reported 13 "answers" then; with the history
(section 11) it is unique, h4 = B, in about 2 s on the JVM. (The janko.at shorties measured here
were removed from the repo on 2026-11-09.)

Two performance problems of the Kotlin **CaDiCaL port** (`~/github/sat-solvers-kotlin`, not
changed here) were found with JFR:

- `CaDiCaL.ensureWatchTables(size)` grows the watch tables to exactly `size` on every
  `newVar()`, i.e. quadratic in the number of variables (the variable arrays grow by doubling).
  A 0-ply puzzle with 18,621 variables spends about 3 s there (Kissat: 0.19 s total).
- `CaDiCaL.reducing()` scans the clause list for a redundant clause *before* checking
  `conflicts >= limReduce`; with few learned clauses that is a linear scan per call. It was
  about half of all samples in SAT perft 3.

Both look like one-line fixes (grow geometrically; test the conflict limit first).

## 10. Known limitations

- All answers are existential (cooperative play); directmates are out of scope (spec section 4).
- The history (section 11) is sound but not complete: it does not track piece moves (a rook
  behind unmoved pawns, castling rights versus king moves), squares passed by double steps, or the
  order of events beyond the two pawn rules. A position it accepts may still be unreachable.
- A position with both `enPassant` and castling rights is accepted as given (no retro check).
- `#` in a move pattern only demands check.
- Solver ports: see section 9 for two performance problems found in the Kotlin CaDiCaL port.

## 11. History (retro accounting at ply 0)

`History.kt`, since 2026-11-09, always on. The position at ply 0 must be one that can arise from
the initial array, as far as these rules can tell; the puzzle format has no switch for it (the old
`rules` flags `oneKingEach`, `checkRules`, `materialSane` are gone). Variables per side:

- for each of the 8 pawns: `vis(sq)` / `end(sq)` over its reachable squares (file shift at most
  the ranks advanced), one step variable per push, double step and diagonal capture; `died`;
  promotion type (Q/R/B/N); the promoted piece's `alivePromo(sq)` / `deadPromoAt(sq)`;
- for each of the 8 original pieces: `alive(sq)` / `deadAt(sq)` (bishops only on their colour, the
  king has no death squares).

Rules, each sound for every legal game:

1. **Paths**: `vis(home)`; a visited square is left by exactly one step or is the end; every other
   visited square has exactly one incoming step. Rows are monotone, so this is one simple path.
   A pawn ending on the last rank promoted; elsewhere it is alive (`alivePawn`) or died there.
2. **Identity**: a board square with a pawn of colour c holds exactly one alive pawn of c; a square
   with a piece holds exactly one alive original of that type or one promoted pawn of that type;
   every unit is on at most one square; each original is alive or died exactly once; a promoted
   bishop keeps the colour of its promotion square. Material counts, promotions versus missing
   pawns and bishop colours all follow.
3. **Captures**: each diagonal step of a pawn onto `sq` needs a victim: an enemy unit whose
   death square is `sq`, or (on the e.p. target row) the enemy pawn of that file that died on the
   square behind after its double step. A death is matched to at most one pawn capture, so the
   victims are distinct; captures by pieces are free.
4. **Home squares**: a pawn alive on its home square never moved, so nothing else was ever on that
   square: no enemy pawn visited it, no unit of either side died there.
5. **Boxed bishops**: if both neighbouring pawns of a bishop's home stand at home, the bishop is at
   home or died at home.
6. **Crossing**: two pawns on one file cannot pass each other. For a pawn `w` that never made a
   diagonal step and is alive on its file at rank `R`: an enemy pawn `b` whose straight run down
   that file from square `s` reaches the first two ranks of `w`'s side was below `w` throughout
   that run (`w` was on the file the whole game, `b` on those ranks is below it, and the order
   cannot change), so the run started below `R`: `reach(b, s)` is false for every `s` at `R` or
   beyond. `reach` is defined square by square from the deep end (`vis(s)` and a vertical step
   whose target reaches).

What this solves: Smullyan's "which piece on h4?" (chess.com #5), see `HistoryTest`. Two plies of
window are needed there (White's cxd8=R+ and Black's move before it); the pawn rules then give:
Black promoted (3 knights or a second dark bishop would be needed otherwise), its h-pawn had to
leave the h-file (rule 4 / 6 with h2 unmoved) and enter the g-file at g2 (rule 6 with g3 unmoved),
all five Black pawn captures were on light squares, so White's dark-squared bishop never died.

Cost: about 18,000 variables and 60,000 clauses for both sides. With a fully given start board
the history shares no variable with the search, so it is built as a CNF of its own
(`Encoding.historyCheck`) and solved once by `Search` (milliseconds; UNSAT = the puzzle has no
solution, reported as an Info line). Carried along in the main CNF it had tripled the time of
Fool's mate in the browser (3.5 s to 11 s). With unknown squares at ply 0 it is part of the main
CNF. Unreachable test positions are now rejected, e.g. chessprogramming's
perft "position 5" (five white pawn captures, one missing black man).
