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
- **One king per colour** (`rules.oneKingEach`): exactly-one ladder over the 64 king variables,
  `s_i <=> s_(i-1) OR x_i`, `x_i => NOT s_(i-1)`, `s_64`. 63 auxiliaries and about 250 clauses per
  king instead of Python's 2,016 pairwise clauses.
- **Castling invariant**: `castling(t, r) => king and rook of r on their home squares`.
- **King safety** (`rules.checkRules`): the king of the side *not* to move is not attacked by
  the side to move. At `t = 0` this is the "the side that just moved is not in check" rule, at
  `t > 0` it is "a move may not leave the own king in check".
- Puzzle constraints: board patterns (`start.board` at 0, `positions`), square domains.

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
  target not attacked (with `checkRules`), the rook on its target at `t+1` and its home empty.

At ply 0 only:

- **Material sanity** (`rules.materialSane`, default on iff some board has `?`): per side at
  most 16 men, and `pawns + promoted pieces <= 8`, where promoted pieces are the excess over the
  initial set: queens over 1, rooks over 2, knights over 2, and **bishops over 1 per square
  colour**. Built from exact unary counters (sequential counters whose outputs are equivalences).
  Later plies inherit sanity, because moves only remove men or turn a pawn into a piece. Still
  not full retro-legality (e.g. pawn structures that need captures are not checked).

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
  - `FULL_STATE` (default, as in the spec): the full state at ply N, about 850 literals.
  - `ESCAPE_MOVE` (optional, not in the spec): take one legal move of the side to move (not
    castling or e.p., which depend on rights / e.p. state; if only such moves exist, fall back to
    the full state) and block only the squares that make it legal: from, to, the squares between,
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

- One incremental `Ksat` per pass (MINISAT, CADICAL, KISSAT support clauses added after a
  solve; MICROSAT does not, so it is rebuilt from the stored clauses for every call).
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
  so a projected value found with either side counts once.
- The projected key shown to the user is the SAN line for `moves` (two different moves can have
  the same SAN when the board is not projected), the placement for `board@t`, `h4 = R` /
  `h4 = empty` for squares, `K on e1` for pieces.
- `shortest`: after enumeration (only if something was found) the puzzle is solved again with
  `plies - 2`: constraints at ply 0 stay, constraints at the last ply move to the new last ply,
  constraints in between and move patterns are dropped. `Done.shortest` = true if that is UNSAT.
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
position (board, side, castling, e.p.), the start must not have the side not to move in check
(with `checkRules`), and the goal must hold. `Solution.verified` reports the result; the tests
require it for every solution.

## 9. Measurements

Encoding size, chess.com puzzle 4 (startpos to `rnbqkbnr/pp3ppp/2p1p3/8/4P3/8/PPPP1PPP/RNBQK1NR`,
8 plies): **29,742 variables, 224,222 clauses** (encoded in about 0.1 s) versus 1.58 M clauses in
the Python encoder (7x smaller). Python with native CaDiCaL took about 14-45 s for that puzzle;
the Kotlin ports need 12-28 s CPU (table in section 4), i.e. within the "small factor of native"
assumed in the spec.

`cli run` over the bundled puzzles (Kissat): chess-com-5/1 (4), /4 (1), /5 (13) match
`expected`; /3 finds 2 lines; /2 (`oneKingEach: false`, `piece@0:K` over an open board)
enumerates tens of thousands of king sets and needs a better puzzle definition. Janko shorties:
every completed one matches (s1-05, s1-06, s1-10, s1-11); several need more than a minute to
prove there is no further solution.

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
- `materialSane` is not full retro-legality (no pawn-structure / capture-count reasoning).
- A position with both `enPassant` and castling rights is accepted as given (no retro check).
- `#` in a move pattern only demands check.
- `rules.checkRules: false` turns off king safety and the castling path test (Python's
  `no_check`); solutions of such puzzles are usually reported as not verified.
- Solver ports: see section 9 for two performance problems found in the Kotlin CaDiCaL port.
