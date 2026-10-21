package org.bytefred.satchess.ui.model

import org.bytefred.satchess.chess.Piece
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.UNKNOWN_SQUARES

/*
 * Square options (doc/spec.md section 7): a square of the editor board is either
 * fixed (a piece, or empty) or holds a set of options, e.g. "a king or nothing". Stored in the puzzle
 * as the board cell '?' plus a square domain in Puzzle.squares; the key [UNKNOWN_SQUARES] of a
 * ply is the default for every '?' square of that ply without its own entry. All options = plain '?'.
 */

/** Palette tool "Options...": a click on a square opens its options dialog instead of placing a piece. */
const val OPTIONS_TOOL = '*'

/** Everything a square can hold, in palette order: the 12 pieces (white first), then empty. */
val ALL_OPTIONS: List<Char> = Piece.entries.map { it.fen } + EMPTY

/** What the editor shows on one square. */
sealed interface SquareState {
    /** A fixed piece, or [EMPTY] for "nothing stands here". */
    data class Fixed(val cell: Char) : SquareState

    /** One of [allowed] stands here (a non-empty subset of [ALL_OPTIONS]; all = plain '?'). */
    data class Options(val allowed: Set<Char>) : SquareState
}

/** Domain string of [allowed] in palette order, e.g. "Kk." (the stored form). */
fun optionsText(allowed: Set<Char>): String = ALL_OPTIONS.filter { it in allowed }.joinToString("")

/** Readable form: "anything", "any piece", "empty", "K, k or empty". */
fun optionsLabel(allowed: Set<Char>): String {
    val pieces = ALL_OPTIONS.filter { it in allowed && it != EMPTY }
    return when {
        allowed.size == ALL_OPTIONS.size -> "anything"
        pieces.size == ALL_OPTIONS.size - 1 -> "any piece"
        pieces.isEmpty() -> if (EMPTY in allowed) "empty" else "nothing"
        else -> {
            val names = pieces.map { it.toString() } + listOfNotNull("empty".takeIf { EMPTY in allowed })
            if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " or " + names.last()
        }
    }
}

/** The options of every '?' square at ply [t] without its own entry (all, unless the "?" key is set). */
fun Puzzle.defaultOptionsAt(t: Int): Set<Char> =
    squares[t.toString()]?.get(UNKNOWN_SQUARES)?.toSet()?.intersect(ALL_OPTIONS.toSet())?.takeIf { it.isNotEmpty() }
        ?: ALL_OPTIONS.toSet()

/** The state of square [sq] at ply [t]: the board cell if fixed, else its own domain or the ply default. */
fun Puzzle.squareStateAt(t: Int, sq: Int): SquareState {
    val cell = boardAt(t)[sq]
    if (cell != UNKNOWN) return SquareState.Fixed(cell)
    val own = squares[t.toString()]?.get(Sq.name(sq))?.toSet()?.intersect(ALL_OPTIONS.toSet())?.takeIf { it.isNotEmpty() }
    return SquareState.Options(own ?: defaultOptionsAt(t))
}

/** The allowed contents of every '?' square at ply [t] (for drawing), keyed by square index. */
fun Puzzle.optionsAt(t: Int): Map<Int, Set<Char>> = boardAt(t).withIndex()
    .filter { it.value == UNKNOWN }
    .associate { (sq, _) -> sq to (squareStateAt(t, sq) as SquareState.Options).allowed }

/**
 * Sets the board at ply [t] like [withBoardAt] and drops the square domains of squares that are now
 * fixed (a fixed piece replaces the options it had).
 */
fun Puzzle.withCellsAt(t: Int, cells: List<Char>): Puzzle {
    val old = boardAt(t)
    val fixedNow = (0 until 64).filter { cells[it] != UNKNOWN && cells[it] != old[it] }.map(Sq::name).toSet()
    val atPly = squares[t.toString()] ?: emptyMap()
    return withBoardAt(t, cells).withSquaresAt(t, atPly - fixedNow)
}

/**
 * Gives square [sq] at ply [t] the options [allowed] (non-empty subset of [ALL_OPTIONS]). A single
 * option is a fixed square. Options equal to the ply default need no own entry.
 */
fun Puzzle.withSquareOptions(t: Int, sq: Int, allowed: Set<Char>): Puzzle {
    require(allowed.isNotEmpty() && ALL_OPTIONS.containsAll(allowed)) { "options must be a non-empty subset of $ALL_OPTIONS" }
    val cells = boardAt(t)
    if (allowed.size == 1) return withCellsAt(t, cells.toMutableList().also { it[sq] = allowed.single() })
    val opened = withBoardAt(t, cells.toMutableList().also { it[sq] = UNKNOWN })
    val atPly = opened.squares[t.toString()] ?: emptyMap()
    val name = Sq.name(sq)
    return opened.withSquaresAt(t, if (allowed == defaultOptionsAt(t)) atPly - name else atPly + (name to optionsText(allowed)))
}

/**
 * Sets the default options for the '?' squares of ply [t] (the "?" key; all options = no key).
 * Own entries equal to the new default become redundant and are dropped.
 */
fun Puzzle.withDefaultOptions(t: Int, allowed: Set<Char>): Puzzle {
    require(allowed.isNotEmpty() && ALL_OPTIONS.containsAll(allowed)) { "options must be a non-empty subset of $ALL_OPTIONS" }
    val text = optionsText(allowed)
    val own = (squares[t.toString()] ?: emptyMap()) - UNKNOWN_SQUARES
    val kept = own.filterValues { it.toSet() != allowed }
    return withSquaresAt(t, if (allowed.size == ALL_OPTIONS.size) kept else kept + (UNKNOWN_SQUARES to text))
}

/** Replaces the square domains of ply [t]; an empty map removes the ply's entry. */
private fun Puzzle.withSquaresAt(t: Int, atPly: Map<String, String>): Puzzle {
    val key = t.toString()
    return copy(squares = if (atPly.isEmpty()) squares - key else squares + (key to atPly))
}
