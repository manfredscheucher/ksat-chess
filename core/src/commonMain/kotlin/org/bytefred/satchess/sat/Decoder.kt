package org.bytefred.satchess.sat

import org.bytefred.satchess.chess.CastleRight
import org.bytefred.satchess.chess.Color
import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Move
import org.bytefred.satchess.chess.Piece
import org.bytefred.satchess.chess.Position
import org.bytefred.satchess.chess.Rules
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.Goal

/** Turns models of an [Encoding] into [Solution]s and checks them with the reference [Rules]. */
internal class Decoder(private val e: Encoding, private val projection: Projection) {

    /** The full position (board, side, castling, e.p.) at ply [t] of [model]. */
    fun position(model: (Int) -> Boolean, t: Int): Position {
        val board = List(64) { sq -> Piece.entries.firstOrNull { model(e.at(t, sq, it)) } }
        val side = e.side(t)
        val castling = CastleRight.entries.filterTo(mutableSetOf()) { model(e.castling(t, it)) }
        val epFile = (0 until 8).firstOrNull { model(e.enPassant(t, it)) }
        val epRow = if (side == Color.WHITE) 2 else 5 // target behind the pawn that just double-pushed
        return Position(board, side, castling, epFile?.let { Sq.of(epRow, it) })
    }

    /** Does the position at the last ply satisfy the goal according to the move generator? */
    fun goalHolds(position: Position): Boolean = when (e.puzzle.goal) {
        Goal.NONE -> true
        Goal.CHECK -> Rules.isInCheck(position)
        Goal.CHECKMATE -> Rules.isCheckmate(position)
        Goal.STALEMATE -> Rules.isStalemate(position)
    }

    fun solution(model: (Int) -> Boolean): Solution {
        val positions = (0..e.plies).map { position(model, it) }
        var verified = !e.checkRules || !Rules.isInCheck(positions[0], positions[0].sideToMove.opposite)
        val moves = (0 until e.plies).map { t ->
            val pair = Geometry.pairs.first { model(e.move(t, it.index)) }
            val promotion = Encoding.PROMOTIONS.indices.firstOrNull { model(e.promotion(t, it)) }
                ?.let { Encoding.PROMOTIONS[it] }
            val before = positions[t]
            val legal = Rules.legalMoves(before).firstOrNull {
                it.from == pair.from && it.to == pair.to && it.promotion == promotion
            }
            if (legal == null || Rules.apply(before, legal) != positions[t + 1]) verified = false
            val move = legal ?: Move(pair.from, pair.to, promotion)
            SolvedMove(move.uci, if (legal != null) Rules.san(before, legal) else move.uci, pair.from, pair.to)
        }
        if (!goalHolds(positions.last())) verified = false
        val fens = positions.map { Fen.toFen(it) }
        return Solution(fens, moves, projection.describe(fens, sanLine(moves, e.sideToMove)), verified)
    }

    private fun sanLine(moves: List<SolvedMove>, first: Color): String = buildString {
        val offset = if (first == Color.WHITE) 0 else 1
        for ((i, m) in moves.withIndex()) {
            val number = (i + offset) / 2 + 1
            if (isNotEmpty()) append(' ')
            if ((i + offset) % 2 == 0) append("$number. ") else if (i == 0) append("$number... ")
            append(m.san)
        }
    }
}
