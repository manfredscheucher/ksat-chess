package org.bytefred.satchess.ui.model

import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.Goal
import org.bytefred.satchess.puzzle.MovePattern
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.Question
import org.bytefred.satchess.sat.puzzleProblems

/**
 * Pure functions mapping between a [Puzzle] (the file format, doc/spec.md section 4) and what the
 * editor shows: one board per ply, the plies/moves sentence, question presets, FEN text.
 * The editor keeps the Puzzle itself as its state, so import/export/share are lossless.
 */

/** The board constraint at ply [t]: A = `start.board`, other plies = `positions[t]`, missing = open. */
fun Puzzle.boardAt(t: Int): List<Char> {
    val text = if (t == 0) start.board else positions[t.toString()] ?: return OPEN_BOARD
    return Fen.parsePattern(text).cells
}

/** Sets the board at ply [t]. The initial position is written as "startpos", open boards are dropped. */
fun Puzzle.withBoardAt(t: Int, cells: List<Char>): Puzzle = if (t == 0) {
    copy(start = start.copy(board = if (cells == INITIAL_BOARD) "startpos" else placementText(cells)))
} else if (cells == OPEN_BOARD) {
    copy(positions = positions - t.toString())
} else {
    copy(positions = positions + (t.toString() to placementText(cells)))
}

/** Plies that carry a move pattern for the move from t to t+1. */
fun Puzzle.movePatternPlies(): Set<Int> = moves.keys.mapNotNull { it.toIntOrNull() }.toSet()

/**
 * Changes the window length. The target board B (its square domains and projection items like
 * `board@N`) moves along with N, as long as both the old and the new window have a B separate from A;
 * intermediate constraints, square domains, move patterns and projection items that no longer fit into
 * the window are dropped (an emptied projection falls back to `moves`).
 */
fun Puzzle.withPlies(newPlies: Int): Puzzle {
    require(newPlies >= 0)
    val old = plies.toString()
    val new = newPlies.toString()
    // Intermediate plies that exist in both windows; A (ply 0) always stays.
    val lastIntermediate = minOf(plies, newPlies) - 1
    fun intermediate(key: String) = key.toIntOrNull()?.let { it in 1..lastIntermediate } == true
    fun inWindow(key: String, last: Int) = key.toIntOrNull()?.let { it in 0..last } == true
    val bMoves = plies > 0 && newPlies > 0
    val b = positions[old]?.takeIf { bMoves }
    val bSquares = squares[old]?.takeIf { bMoves }
    return copy(
        plies = newPlies,
        positions = positions.filterKeys(::intermediate) + listOfNotNull(b?.let { new to it }),
        squares = squares.filterKeys { it == "0" || intermediate(it) } + listOfNotNull(bSquares?.let { new to it }),
        moves = moves.filterKeys { inWindow(it, newPlies - 1) },
        project = project.mapNotNull { item ->
            val ply = PLY_ITEM.find(item)?.groupValues?.get(1) ?: return@mapNotNull item
            when {
                ply == old && bMoves -> item.replaceFirst("@$old", "@$new")
                ply == "0" || intermediate(ply) -> item
                else -> null
            }
        }.ifEmpty { listOf("moves") },
    )
}

/** The ply of a projection item like `board@4` or `square@4:h4`. */
private val PLY_ITEM = Regex("@(\\d+)")

/**
 * Null if [puzzle] can be edited and solved, else its problems: everything the solver would reject
 * ([puzzleProblems]), plus what the editor cannot show (more than [MAX_PLIES] plies, a board in
 * `positions` at ply 0, an e.p. square that does not fit the side to move). Used for imports and
 * share links.
 */
fun puzzleProblem(puzzle: Puzzle): String? {
    val editor = listOfNotNull(
        "plies must be at most $MAX_PLIES".takeIf { puzzle.plies > MAX_PLIES },
        "positions: ply 0 belongs in start.board".takeIf { "0" in puzzle.positions },
        enPassantError(puzzle.start.enPassant, puzzle.start.sideToMove),
    )
    return (puzzleProblems(puzzle) + editor).distinct().takeIf { it.isNotEmpty() }?.joinToString("; ")
}

// ── plies vs moves ────────────────────────────────────────────────────────────

enum class PliesUnit { PLIES, MOVES }

/** Plies as (full) moves: 13 -> "6.5", 12 -> "6". */
fun formatMoves(plies: Int): String = if (plies % 2 == 0) "${plies / 2}" else "${plies / 2}.5"

/** Text of the N field in the given unit. */
fun formatPlies(plies: Int, unit: PliesUnit): String =
    if (unit == PliesUnit.PLIES) plies.toString() else formatMoves(plies)

const val MAX_PLIES = 200

private val PLIES_INPUT = Regex("\\d{1,4}")
private val MOVES_INPUT = Regex("(\\d{0,4})(?:[.,]([05]))?")

/** Parses the N field: plies are whole numbers, moves may end in ".5" (or ",5"). Null = invalid input. */
fun parsePliesInput(text: String, unit: PliesUnit): Int? {
    val t = text.trim()
    val plies = when (unit) {
        PliesUnit.PLIES -> PLIES_INPUT.matchEntire(t)?.let { t.toInt() }
        PliesUnit.MOVES -> MOVES_INPUT.matchEntire(t)
            ?.takeIf { it.groupValues[1].isNotEmpty() || it.groupValues[2].isNotEmpty() }
            ?.let { m -> (m.groupValues[1].ifEmpty { "0" }.toInt()) * 2 + if (m.groupValues[2] == "5") 1 else 0 }
    }
    return plies?.takeIf { it in 0..MAX_PLIES }
}

private fun colorName(side: Char): String = if (side == 'w') "White" else "Black"
private fun other(side: Char): Char = if (side == 'w') 'b' else 'w'

/** Side to move at ply [t] ('w'/'b'), or null when the start side is free ('?'). */
fun sideToMoveAt(startSide: String, t: Int): Char? {
    val start = startSide.singleOrNull()?.takeIf { it == 'w' || it == 'b' } ?: return null
    return if (t % 2 == 0) start else other(start)
}

/**
 * The live sentence next to the N field, e.g.
 * "13 plies = PG 6.5 · White starts · White moves last · Black to move at B".
 */
fun pliesSentence(plies: Int, startSide: String): String {
    val head = "$plies ${if (plies == 1) "ply" else "plies"} = PG ${formatMoves(plies)}"
    val start = sideToMoveAt(startSide, 0) ?: return "$head · either side starts"
    if (plies == 0) return "$head · position only · ${colorName(start)} to move"
    val lastMover = sideToMoveAt(startSide, plies - 1)!!
    val atB = sideToMoveAt(startSide, plies)!!
    return "$head · ${colorName(start)} starts · ${colorName(lastMover)} moves last · ${colorName(atB)} to move at B"
}

// ── question presets ──────────────────────────────────────────────────────────

/**
 * Applies a question preset (spec section 7: "the preset fills A, B, N, goal and projection").
 * Boards the user already entered are not thrown away: a preset that resets A or opens B first moves
 * a concrete board from there to the other end if that end is still open (and N > 0).
 */
fun applyPreset(puzzle: Puzzle, question: Question): Puzzle {
    val p = puzzle.copy(question = question)
    val n = p.plies.toString()
    val aOpen = p.boardAt(0) == OPEN_BOARD
    val bOpen = p.positions[n] == null
    return when (question) {
        Question.PROOF_GAME -> {
            // Proof game: A is the initial position; a custom A board becomes the target B if B is open.
            val a = p.boardAt(0)
            val withB = if (bOpen && !aOpen && a != INITIAL_BOARD && p.plies > 0) p.withBoardAt(p.plies, a) else p
            withB.copy(
                start = withB.start.copy(board = "startpos", sideToMove = "w", castling = "auto", enPassant = "-"),
                goal = Goal.NONE,
                project = listOf("moves"),
            )
        }
        Question.LAST_MOVES -> {
            // "What were the last moves?": the given position is B, the earlier one A is open.
            val withB = if (bOpen && !aOpen && p.plies > 0) p.withBoardAt(p.plies, p.boardAt(0)) else p
            withB.copy(
                start = withB.start.copy(board = placementText(OPEN_BOARD), sideToMove = "?", castling = "?", enPassant = "-"),
                goal = Goal.NONE,
                project = listOf("moves"),
            )
        }
        Question.HELPMATE -> {
            // Helpmate: the given position is A, Black moves first, B is open, mate at the end.
            val withA = if (aOpen && !bOpen && p.plies > 0) p.withBoardAt(0, p.boardAt(p.plies)) else p
            withA.copy(
                start = withA.start.copy(sideToMove = "b"),
                positions = withA.positions - n,
                goal = Goal.CHECKMATE,
                project = listOf("moves"),
            )
        }
        Question.UNKNOWN_PIECE -> {
            val unknown = p.boardAt(0).withIndex().filter { it.value == UNKNOWN }
            val project = if (unknown.size == 1) "square@0:${Sq.name(unknown.single().index)}" else "board@0"
            p.copy(goal = Goal.NONE, project = listOf(project))
        }
        Question.CUSTOM -> p
    }
}

val Question.label: String
    get() = when (this) {
        Question.PROOF_GAME -> "Proof game"
        Question.LAST_MOVES -> "Last move(s)?"
        Question.HELPMATE -> "Helpmate in k"
        Question.UNKNOWN_PIECE -> "Find unknown piece"
        Question.CUSTOM -> "Custom"
    }

val Goal.label: String
    get() = when (this) {
        Goal.NONE -> "none"
        Goal.CHECK -> "check"
        Goal.CHECKMATE -> "checkmate"
        Goal.STALEMATE -> "stalemate"
    }

// ── FEN text field (two-way) ──────────────────────────────────────────────────

/** Result of typing into the FEN field: the updated puzzle, or an inline error message. */
data class FenEdit(val puzzle: Puzzle?, val error: String?)

/**
 * Applies the FEN field of ply [t]. Accepts a placement (with '?'), "startpos", and at A also a full
 * FEN whose side / castling / e.p. fields then set the start parameters. Never throws.
 */
fun applyFenText(puzzle: Puzzle, t: Int, text: String): FenEdit {
    val fields = text.trim().split(Regex("\\s+"))
    if (fields.size > 1 && t != 0) return FenEdit(null, "Only a placement is allowed here (no side/castling fields)")
    if (fields.size !in listOf(1, 4, 5, 6)) return FenEdit(null, "Expected a placement, or placement + side + castling + e.p.")
    val cells = try {
        Fen.parsePattern(fields[0]).cells
    } catch (e: IllegalArgumentException) {
        return FenEdit(null, e.message ?: "Invalid FEN")
    }
    val withBoard = puzzle.withBoardAt(t, cells)
    if (fields.size == 1) return FenEdit(withBoard, null)
    val side = fields[1].takeIf { it in listOf("w", "b", "?") } ?: return FenEdit(null, "Side to move must be w, b or ?")
    val castling = fields[2].takeIf { isCastlingField(it) } ?: return FenEdit(null, "Castling must be -, ?, auto or letters of KQkq")
    val ep = fields[3]
    enPassantError(ep, side)?.let { return FenEdit(null, it) }
    return FenEdit(withBoard.copy(start = withBoard.start.copy(sideToMove = side, castling = castling, enPassant = ep)), null)
}

/** The FEN field text for ply [t] (placement only; start parameters have their own controls). */
fun Puzzle.fenTextAt(t: Int): String = placementText(boardAt(t))

// ── start parameters ──────────────────────────────────────────────────────────

/** Castling field values: "auto", "?", "-" or a duplicate-free subset of KQkq. */
fun isCastlingField(value: String): Boolean =
    value in listOf("auto", "?", "-") ||
        (value.isNotEmpty() && value.all { it in "KQkq" } && value.toSet().size == value.length)

/** Toggles one castling right; the result is written in canonical KQkq order, "-" when empty. */
fun toggleCastlingRight(castling: String, right: Char, on: Boolean): String {
    val current = if (castling.all { it in "KQkq" }) castling.toSet() else emptySet()
    val next = if (on) current + right else current - right
    return "KQkq".filter { it in next }.ifEmpty { "-" }
}

/** Null if [ep] is a valid e.p. field for [side] ("-" or a rank-3/6 square), else the error. */
fun enPassantError(ep: String, side: String): String? {
    if (ep == "-") return null
    if (Sq.parse(ep) == null) return "En passant must be - or a square like e3"
    val rank = ep[1]
    return when {
        rank != '3' && rank != '6' -> "En passant square must be on rank 3 or 6"
        side == "w" && rank != '6' -> "With White to move the e.p. square is on rank 6"
        side == "b" && rank != '3' -> "With Black to move the e.p. square is on rank 3"
        else -> null
    }
}

// ── move patterns, square domains, projection ─────────────────────────────────

fun Puzzle.withMovePattern(t: Int, pattern: MovePattern?): Puzzle =
    copy(moves = if (pattern == null) moves - t.toString() else moves + (t.toString() to pattern))

/** Short label of a move pattern, e.g. "Kf2" or "p x" for "a black pawn captures". */
fun MovePattern.label(): String = san ?: buildList {
    piece?.let { add(it) }
    if (from != null || to != null) add("${from ?: "?"}-${to ?: "?"}")
    if (capture == true) add("x")
    if (capture == false) add("no capture")
    promo?.let { add("=$it") }
    castle?.let { add(if (it == "any") "castles" else "O-O ($it)") }
    if (check == true) add("+")
}.joinToString(" ").ifEmpty { "any move" }

/** Sets the allowed contents of [square] at ply [t]; an empty string removes the domain. */
fun Puzzle.withSquareDomain(t: Int, square: String, allowed: String): Puzzle {
    val key = t.toString()
    val atPly = (squares[key] ?: emptyMap()).let { if (allowed.isEmpty()) it - square else it + (square to allowed) }
    return copy(squares = if (atPly.isEmpty()) squares - key else squares + (key to atPly))
}

/** Null if [allowed] is a valid square domain (FEN letters and '.'), else the error. */
fun squareDomainError(allowed: String): String? = when {
    allowed.isEmpty() -> "Give at least one piece letter or '.'"
    allowed.any { it != EMPTY && !isPiece(it) } -> "Only FEN letters (KQRBNPkqrbnp) and '.' are allowed"
    else -> null
}

private val PROJECTION_ITEM = Regex("moves|sideToMove@0|board@\\d+|square@\\d+:[a-h][1-8]|piece@\\d+:[KQRBNPkqrbnp]")

/** Parses the comma separated projection field; null if an item is not one of the spec's forms. */
fun parseProjection(text: String): List<String>? {
    val items = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    return items.takeIf { it.isNotEmpty() && it.all(PROJECTION_ITEM::matches) }
}

/** True if solutions are whole lines (the projection includes the moves). */
fun isPathProjection(project: List<String>): Boolean = "moves" in project
