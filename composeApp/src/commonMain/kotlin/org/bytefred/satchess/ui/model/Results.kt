package org.bytefred.satchess.ui.model

import org.bytefred.satchess.chess.Color
import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Piece
import org.bytefred.satchess.chess.PieceType
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.sat.Solution

/** Solutions sharing one projected value, e.g. "h4 = R" with the lines that lead to it. */
data class AnswerGroup(val key: String, val solutions: List<Solution>)

/** Groups solutions by [Solution.projectedKey]; the most frequent answer first, ties in found order. */
fun groupByProjection(solutions: List<Solution>): List<AnswerGroup> =
    solutions.groupBy { it.projectedKey }
        .map { (key, list) -> AnswerGroup(key, list) }
        .sortedByDescending { it.solutions.size }

/** One-line answer summary for non-path projections, e.g. "h4 = R (3 lines), h4 = Q (1 line)". */
fun answerSummary(groups: List<AnswerGroup>): String = groups.joinToString(", ") { group ->
    val n = group.solutions.size
    "${group.key} ($n ${if (n == 1) "line" else "lines"})"
}

/**
 * The count line of the results: "unique", "3 solutions, expected 3", "at least 5 solutions (stopped at
 * the limit)", "no solution", "3 solutions (1 NOT verified)". [complete] = the search space was
 * exhausted, so [found] is exact. A line the move generator did not confirm is never called "unique".
 */
fun countSummary(found: Int, complete: Boolean, expected: Int?, unverified: Int = 0): String {
    val noun = if (found == 1) "solution" else "solutions"
    val main = when {
        found == 0 && complete -> "no solution"
        found == 1 && complete && unverified == 0 -> "unique"
        complete -> "$found $noun"
        else -> "at least $found $noun (stopped at the limit)"
    }
    val check = when {
        unverified == 0 -> ""
        found == 1 -> " (NOT verified)"
        else -> " ($unverified NOT verified)"
    }
    return if (expected == null) main + check else "$main$check, expected $expected"
}

/** Numbered SAN move list of a line, e.g. "1. e4 e5 2. Nf3" or "1... e5 2. Nf3" when Black starts. */
fun sanLine(sans: List<String>, whiteStarts: Boolean): String = buildString {
    var moveNo = 1
    var white = whiteStarts
    for ((i, san) in sans.withIndex()) {
        if (white) {
            if (i > 0) append(' ')
            append("$moveNo. ")
        } else if (i == 0) {
            append("$moveNo... ")
        } else {
            append(' ')
        }
        append(san)
        if (!white) moveNo++
        white = !white
    }
}

// ── "Group similar solutions" ────────────────────────────────────────────────

/**
 * Grouping key of a solution: the start position (full FEN), plus every move as the event "<moving piece> to
 * <target square>" (" x <captured piece>" if it captures) with full piece identity (colour + type).
 * White's and Black's events are collected separately and each list is sorted, so solutions that
 * differ only in move order share the key. White and Black events are never mixed, and solutions
 * from different start positions (an open A, as in "what was the last move?") never share a group.
 */
data class SimilarityKey(val start: String, val white: List<String>, val black: List<String>)

/** The solution indices of one group of similar solutions (in found order). */
data class SimilarGroup(val key: SimilarityKey?, val indices: List<Int>)

/** The key of [solution], or null if its positions cannot be read (it then forms its own group). */
fun similarityKey(solution: Solution): SimilarityKey? {
    val white = mutableListOf<String>()
    val black = mutableListOf<String>()
    for ((i, move) in solution.moves.withIndex()) {
        val cells = solution.fens.getOrNull(i)?.let { fen ->
            try {
                Fen.parsePattern(fen.substringBefore(' ')).cells
            } catch (e: IllegalArgumentException) {
                null
            }
        } ?: return null
        val piece = Piece.fromFen(cells[move.from]) ?: return null
        val captured = Piece.fromFen(cells[move.to]) ?: enPassantVictim(piece, move.from, move.to, cells)
        val event = "${pieceName(piece)} to ${Sq.name(move.to)}" + (captured?.let { " x ${pieceName(it)}" } ?: "")
        if (piece.color == Color.WHITE) white += event else black += event
    }
    val start = solution.fens.firstOrNull() ?: return null // full FEN: board, side to move, castling, e.p.
    return SimilarityKey(start, white.sorted(), black.sorted())
}

/** A pawn moving diagonally onto an empty square captures en passant: the victim is the enemy pawn. */
private fun enPassantVictim(piece: Piece, from: Int, to: Int, cells: List<Char>): Piece? =
    if (piece.type == PieceType.PAWN && Sq.col(from) != Sq.col(to) && cells[to] == EMPTY) {
        Piece.of(piece.color.opposite, PieceType.PAWN)
    } else {
        null
    }

private fun pieceName(piece: Piece): String =
    "${piece.color.name.lowercase()} ${piece.type.name.lowercase()}"

/**
 * Groups solutions with identical [similarityKey], incrementally: [add] keys one solution (indices in
 * the order added), [groups] lists the groups in order of their first solution.
 */
class SimilarGrouper {
    private val groups = LinkedHashMap<Any, Pair<SimilarityKey?, MutableList<Int>>>()

    /** Number of solutions added so far. */
    var size = 0
        private set

    fun add(solution: Solution) {
        val key = similarityKey(solution)
        groups.getOrPut(key ?: size) { key to mutableListOf() }.second += size
        size++
    }

    fun groups(): List<SimilarGroup> = groups.values.map { (key, indices) -> SimilarGroup(key, indices.toList()) }
}

/** "White: white knight to f3, white pawn to e4 · Black: black pawn to e5". */
fun similaritySummary(key: SimilarityKey?): String {
    if (key == null) return "(unreadable line)"
    fun side(name: String, events: List<String>) = "$name: " + events.joinToString(", ").ifEmpty { "-" }
    return side("White", key.white) + " · " + side("Black", key.black)
}
