package org.bytefred.satchess.sat

import org.bytefred.satchess.chess.CastleRight
import org.bytefred.satchess.chess.Color
import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Piece
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.UNKNOWN_SQUARES

/**
 * Everything in [p] the solver would reject, as readable messages (empty = solvable input).
 * Pure and cheap (no encoding): plies, ply keys of positions / squares / moves, projection items,
 * the start fields, board FENs, square domains and move patterns. [PuzzleSolver.solve] fails with
 * these messages before encoding.
 */
fun puzzleProblems(p: Puzzle): List<String> {
    val problems = ArrayList<String>()
    fun check(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            problems += e.message ?: e.toString()
        }
    }
    val n = p.plies
    if (n < 0) return listOf("plies must be >= 0, got $n")

    /** The ply of [key], or null (and a problem) if it is no number in [range]. */
    fun ply(key: String, what: String, range: IntRange): Int? {
        val t = key.toIntOrNull()
        when {
            t == null -> problems += "$what: ply key '$key' is not a number"
            t !in range -> problems += "$what: ply $t is outside ${range.first}..${range.last}"
            else -> return t
        }
        return null
    }

    check { Fen.parsePattern(p.start.board) }
    for ((key, board) in p.positions) {
        if (ply(key, "positions", 0..n) != null) check { Fen.parsePattern(board) }
    }
    p.positions["0"]?.let { board ->
        val merged = runCatching { mergeBoards(listOf(p.start.board, board)) } // unreadable boards are reported above
        if (merged.isSuccess && merged.getOrNull() == null) problems += "positions: the board for ply 0 contradicts start.board"
    }
    for ((key, domains) in p.squares) {
        val t = ply(key, "squares", 0..n) ?: continue
        for ((name, allowed) in domains) {
            if (name != UNKNOWN_SQUARES && Sq.parse(name) == null) problems += "squares: invalid square '$name' at ply $t"
            if (allowed.isEmpty()) problems += "squares: empty domain for $name at ply $t"
            allowed.firstOrNull { it != '.' && Piece.fromFen(it) == null }?.let {
                problems += "squares $name at ply $t: invalid piece '$it'"
            }
        }
    }
    for ((key, pattern) in p.moves) {
        ply(key, "moves", 0 until n) ?: continue
        // The syntax does not depend on the mover; its colour only picks the pieces.
        check { pattern.san?.let { parseSanConstraint(it, Color.WHITE) } }
        check { fieldConstraint(pattern, Color.WHITE) }
    }
    for (item in p.project) check { parseProjectItem(item, n) }

    val start = p.start
    if (start.sideToMove !in listOf("w", "b", "?")) {
        problems += "start.sideToMove must be 'w', 'b' or '?', got '${start.sideToMove}'"
    }
    val castling = start.castling
    val castlingLetters = castling.all { c -> CastleRight.entries.any { it.fen == c } }
    if (castling != "auto" && castling != "?" && castling != "-" && !castlingLetters) {
        problems += "start.castling must be 'auto', '?', '-' or letters of KQkq, got '$castling'"
    }
    val ep = start.enPassant
    if (ep != "-") {
        val target = Sq.parse(ep)
        if (target == null) problems += "start.enPassant must be '-' or a square, got '$ep'"
        else if (Sq.row(target) != 2 && Sq.row(target) != 5) problems += "start.enPassant '$ep' must be on rank 3 or 6"
    }
    return problems
}
