package org.bytefred.satchess.ui.model

import org.bytefred.satchess.chess.BoardPattern
import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Piece

/**
 * Pure board-editing logic for the editor (doc/spec.md section 7). A board is the 64 cells of a
 * [BoardPattern]: a FEN piece letter, [EMPTY] or [UNKNOWN], indexed like [org.bytefred.satchess.chess.Sq].
 */
const val EMPTY = '.'
const val UNKNOWN = '?'

/** The palette: 12 pieces (white first, as in FEN), then empty and unknown. */
val PALETTE: List<Char> = Piece.entries.map { it.fen } + EMPTY + UNKNOWN

val OPEN_BOARD: List<Char> = List(64) { UNKNOWN }
val CLEAR_BOARD: List<Char> = List(64) { EMPTY }
val INITIAL_BOARD: List<Char> = Fen.parsePattern(Fen.START_PLACEMENT).cells

/**
 * Hybrid input, click with [selected] palette entry on [sq]: a square that already holds the selected
 * entry is emptied, anything else is overwritten with it.
 */
fun paletteClick(cells: List<Char>, sq: Int, selected: Char): List<Char> =
    cells.with(sq, if (cells[sq] == selected) EMPTY else selected)

/** Drag on the board: the piece moves to [to] (overwriting it), [from] becomes empty. */
fun movePiece(cells: List<Char>, from: Int, to: Int): List<Char> =
    if (from == to || !isPiece(cells[from])) cells else cells.with(to, cells[from]).with(from, EMPTY)

/** Drag off the board, or the dialog's "empty" choice. */
fun removePiece(cells: List<Char>, sq: Int): List<Char> = cells.with(sq, EMPTY)

/** Dialog input mode: the square gets exactly [value]. */
fun setSquare(cells: List<Char>, sq: Int, value: Char): List<Char> = cells.with(sq, value)

fun isPiece(cell: Char): Boolean = Piece.fromFen(cell) != null

/**
 * How strongly a board constrains its ply (timeline icon); derived from the number of '?'. An all-'?'
 * board with square options ([restricted]) is partial, not open.
 */
enum class ConstraintLevel { FIXED, PARTIAL, OPEN }

fun constraintLevel(cells: List<Char>, restricted: Boolean = false): ConstraintLevel = when (cells.count { it == UNKNOWN }) {
    0 -> ConstraintLevel.FIXED
    cells.size -> if (restricted) ConstraintLevel.PARTIAL else ConstraintLevel.OPEN
    else -> ConstraintLevel.PARTIAL
}

/** FEN placement of [cells], with '?' for unknown squares. */
fun placementText(cells: List<Char>): String = Fen.patternToString(BoardPattern(cells))

private fun List<Char>.with(sq: Int, value: Char): List<Char> =
    if (this[sq] == value) this else toMutableList().also { it[sq] = value }
