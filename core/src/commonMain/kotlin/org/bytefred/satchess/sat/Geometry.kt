package org.bytefred.satchess.sat

import org.bytefred.satchess.chess.CastleRight
import org.bytefred.satchess.chess.Color
import org.bytefred.satchess.chess.Sq
import kotlin.math.abs
import kotlin.math.max

/** Static board geometry shared by all encodings (squares as in [Sq]: row 0 = rank 8). */
object Geometry {
    /** One (from, to) pair a piece could ever move along: queen lines and knight jumps. */
    class MovePair(val index: Int, val from: Int, val to: Int, val isKnight: Boolean) {
        val dRow = Sq.row(to) - Sq.row(from)
        val dCol = Sq.col(to) - Sq.col(from)
        val distance = max(abs(dRow), abs(dCol))
        val isOrthogonal = !isKnight && (dRow == 0 || dCol == 0)

        /** Squares strictly between from and to (empty for knight jumps and adjacent squares). */
        val between: IntArray =
            if (isKnight) IntArray(0)
            else IntArray(distance - 1) { k ->
                val step = k + 1
                Sq.of(Sq.row(from) + step * dRow.sign(), Sq.col(from) + step * dCol.sign())
            }
    }

    val KNIGHT_STEPS = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
    val KING_STEPS = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
    /** The 8 ray directions; the first four are orthogonal (rook), the last four diagonal (bishop). */
    val DIRECTIONS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1)

    val pairs: List<MovePair>
    private val pairIndex = IntArray(64 * 64) { -1 }

    init {
        val list = ArrayList<MovePair>()
        for (from in 0 until 64) for (to in 0 until 64) {
            if (from == to) continue
            val dr = abs(Sq.row(to) - Sq.row(from))
            val dc = abs(Sq.col(to) - Sq.col(from))
            val line = dr == 0 || dc == 0 || dr == dc
            val knight = (dr == 1 && dc == 2) || (dr == 2 && dc == 1)
            if (line || knight) {
                pairIndex[from * 64 + to] = list.size
                list += MovePair(list.size, from, to, knight)
            }
        }
        pairs = list
    }

    /** Index into [pairs] or -1 if no piece can ever move from [from] to [to]. */
    fun pairIndex(from: Int, to: Int): Int = pairIndex[from * 64 + to]

    /** Square reached from [sq] by (dRow, dCol), or -1 if off the board. */
    fun step(sq: Int, dRow: Int, dCol: Int): Int {
        val r = Sq.row(sq) + dRow
        val c = Sq.col(sq) + dCol
        return if (r in 0..7 && c in 0..7) Sq.of(r, c) else -1
    }

    fun neighbours(sq: Int, steps: List<Pair<Int, Int>>): List<Int> =
        steps.map { (dr, dc) -> step(sq, dr, dc) }.filter { it >= 0 }

    /** Row delta of a pawn step for [color] (white moves up = towards row 0). */
    fun forward(color: Color): Int = if (color == Color.WHITE) -1 else 1
    fun pawnStartRow(color: Color): Int = if (color == Color.WHITE) 6 else 1
    fun promotionRow(color: Color): Int = if (color == Color.WHITE) 0 else 7
    /** Row of a [color] pawn that may capture en passant (it stands next to the pushed pawn). */
    fun epCaptureRow(color: Color): Int = if (color == Color.WHITE) 3 else 4

    /** Squares involved in castling with one right. */
    class Castle(val right: CastleRight) {
        private val row = if (right.color == Color.WHITE) 7 else 0
        private val kingSide = right.isKingSide
        val kingFrom = Sq.of(row, 4)
        val kingTo = Sq.of(row, if (kingSide) 6 else 2)
        val rookFrom = Sq.of(row, if (kingSide) 7 else 0)
        val rookTo = Sq.of(row, if (kingSide) 5 else 3)
        /** Squares between king and rook, all of which must be empty. */
        val empty: IntArray = if (kingSide) intArrayOf(Sq.of(row, 5), Sq.of(row, 6))
        else intArrayOf(Sq.of(row, 1), Sq.of(row, 2), Sq.of(row, 3))
        /** King start, transit and destination, none of which may be attacked. */
        val kingPath: IntArray = intArrayOf(kingFrom, Sq.of(row, if (kingSide) 5 else 3), kingTo)
    }

    val castles: Map<CastleRight, Castle> = CastleRight.entries.associateWith { Castle(it) }

    private fun Int.sign(): Int = if (this > 0) 1 else if (this < 0) -1 else 0
}
