package org.bytefred.satchess.sat

import org.bytefred.satchess.chess.Move
import org.bytefred.satchess.chess.PieceType
import org.bytefred.satchess.chess.Position
import org.bytefred.satchess.chess.Rules
import kotlin.math.abs

/** How a final position that is no real checkmate / stalemate is excluded (doc/encoding.md section 5). */
enum class Refinement {
    /** Block the full state at the last ply (board, castling, e.p.); doc/spec.md section 5 item 8. */
    FULL_STATE,
    /**
     * Block only the squares that make one legal move of the side to move legal: its from / to
     * squares, the squares between them, and every square that could attack the king after the
     * move (rays up to the first piece, knight, pawn and king neighbours). Every position that
     * agrees on these squares has the same legal move, so the clause is still exact.
     */
    ESCAPE_MOVE,
}

/**
 * Refinement clause for a final position [p] that has a legal move, per [mode]. [Refinement.ESCAPE_MOVE]
 * needs exactly one king of the side to move (the escape squares are taken around it); otherwise
 * the full state is blocked (cannot happen with [History], kept as a safety net).
 */
internal fun refinementClause(e: Encoding, model: (Int) -> Boolean, p: Position, mode: Refinement): IntArray {
    val escape = if (mode == Refinement.ESCAPE_MOVE && kingCount(p) == 1) {
        Rules.legalMoves(p).firstOrNull { !isCastleOrEp(p, it) }
    } else {
        null
    }
    if (escape == null) return e.finalState(model).map { -it }.toIntArray()
    return escapeSquares(p, escape).map { sq -> -e.at(e.plies, sq, p[sq]?.let { pieceIndex(it) } ?: EMPTY) }.toIntArray()
}

private fun kingCount(p: Position): Int =
    p.board.count { it != null && it.color == p.sideToMove && it.type == PieceType.KING }

/** Castling and e.p. depend on more than the board (rights, e.p. state); they never serve as witness. */
private fun isCastleOrEp(p: Position, m: Move): Boolean {
    val piece = p[m.from] ?: return true
    return (piece.type == PieceType.KING && abs(m.to - m.from) == 2) ||
        (piece.type == PieceType.PAWN && m.to % 8 != m.from % 8 && p[m.to] == null)
}

private fun escapeSquares(p: Position, m: Move): Set<Int> {
    val side = p.sideToMove
    val after = Rules.apply(p, m)
    val king = (0 until 64).first { after[it]?.let { x -> x.color == side && x.type == PieceType.KING } == true }
    val squares = hashSetOf(m.from, m.to, king)
    Geometry.pairIndex(m.from, m.to).takeIf { it >= 0 }?.let { squares += Geometry.pairs[it].between.toList() }
    squares += Geometry.neighbours(king, Geometry.KNIGHT_STEPS)
    squares += Geometry.neighbours(king, Geometry.KING_STEPS) // covers the two pawn attack squares
    for ((dr, dc) in Geometry.DIRECTIONS) {
        var sq = Geometry.step(king, dr, dc)
        while (sq >= 0) {
            squares += sq
            if (after[sq] != null) break
            sq = Geometry.step(sq, dr, dc)
        }
    }
    return squares
}
