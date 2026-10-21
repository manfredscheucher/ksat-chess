package org.bytefred.satchess.sat

import org.bytefred.satchess.chess.Color
import org.bytefred.satchess.chess.Piece
import org.bytefred.satchess.chess.PieceType
import org.bytefred.satchess.chess.Sq

/**
 * Lazily built "square is attacked by colour" variables for one board (one ply).
 *
 * Sliding attacks use one ray variable per (square, direction, colour), defined recursively:
 * `ray(sq, d) <=> slider(next) OR (empty(next) AND ray(next, d))`, i.e. the first occupied square
 * seen from sq in direction d holds a rook/queen (orthogonal) or bishop/queen (diagonal) of that
 * colour. All definitions are equivalences, so the variables can be used positively (check goal)
 * and negatively (king safety, castling path). Variables are only created for squares asked for.
 *
 * [transparent] (a piece index or -1) is treated as empty for the rays; with the defending king
 * it gives the "king flight squares" layer needed for checkmate / stalemate.
 */
internal class AttackLayer(
    private val cnf: Cnf,
    /** Variable "square holds piece index" on this board (0 = empty, 1..12 = [Piece] ordinal + 1). */
    private val at: (sq: Int, piece: Int) -> Int,
    private val transparent: Int = -1,
) {
    private val attackedVars = IntArray(64 * 2)
    private val rayVars = IntArray(64 * 8 * 2)
    private val openVars = IntArray(64)

    fun attacked(sq: Int, by: Color): Int {
        val key = sq * 2 + by.ordinal
        if (attackedVars[key] == 0) {
            val lits = ArrayList<Int>()
            val knight = pieceIndex(by, PieceType.KNIGHT)
            for (n in Geometry.neighbours(sq, Geometry.KNIGHT_STEPS)) lits += at(n, knight)
            val king = pieceIndex(by, PieceType.KING)
            for (n in Geometry.neighbours(sq, Geometry.KING_STEPS)) lits += at(n, king)
            val pawn = pieceIndex(by, PieceType.PAWN)
            for (dc in intArrayOf(-1, 1)) {
                val n = Geometry.step(sq, -Geometry.forward(by), dc)
                if (n >= 0 && Sq.row(n) != 0 && Sq.row(n) != 7) lits += at(n, pawn)
            }
            for (d in Geometry.DIRECTIONS.indices) ray(sq, d, by).let { if (it != 0) lits += it }
            attackedVars[key] = cnf.or(lits.toIntArray())
        }
        return attackedVars[key]
    }

    /** Ray variable, or 0 if [sq] is on the board edge in direction [d]. */
    private fun ray(sq: Int, d: Int, by: Color): Int {
        val key = (sq * 8 + d) * 2 + by.ordinal
        if (rayVars[key] != 0) return rayVars[key]
        val (dr, dc) = Geometry.DIRECTIONS[d]
        val next = Geometry.step(sq, dr, dc)
        if (next < 0) return 0
        val slider = pieceIndex(by, if (d < 4) PieceType.ROOK else PieceType.BISHOP)
        val sliders = intArrayOf(at(next, pieceIndex(by, PieceType.QUEEN)), at(next, slider))
        val further = ray(next, d, by)
        val v: Int
        if (further == 0) {
            v = cnf.or(sliders)
        } else {
            v = cnf.newVar()
            val open = open(next)
            for (s in sliders) cnf.add(-s, v)
            cnf.add(-open, -further, v)
            cnf.add(-v, sliders[0], sliders[1], open)
            cnf.add(-v, sliders[0], sliders[1], further)
        }
        rayVars[key] = v
        return v
    }

    private fun open(sq: Int): Int {
        if (transparent < 0) return at(sq, EMPTY)
        if (openVars[sq] == 0) openVars[sq] = cnf.or(intArrayOf(at(sq, EMPTY), at(sq, transparent)))
        return openVars[sq]
    }
}

/** Piece index used in the encoding: 0 = empty, 1..12 = [Piece] ordinal + 1. */
internal const val EMPTY = 0

internal fun pieceIndex(piece: Piece): Int = piece.ordinal + 1

internal fun pieceIndex(color: Color, type: PieceType): Int = pieceIndex(Piece.of(color, type))
