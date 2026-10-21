package org.bytefred.satchess.ui

import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Position
import org.bytefred.satchess.chess.Rules
import org.bytefred.satchess.sat.Solution
import org.bytefred.satchess.sat.SolvedMove

/** A [Solution] for a SAN line from the initial position, built with the reference move generator. */
fun sampleSolution(vararg sans: String, projectedKey: String = sans.joinToString(" ")): Solution {
    var position: Position = Fen.parsePosition(Fen.START)
    val fens = mutableListOf(Fen.toFen(position))
    val moves = sans.map { san ->
        val move = requireNotNull(Rules.parseSan(position, san)) { "illegal $san" }
        val solved = SolvedMove(move.uci, Rules.san(position, move), move.from, move.to)
        position = Rules.apply(position, move)
        fens += Fen.toFen(position)
        solved
    }
    return Solution(fens, moves, projectedKey, verified = true)
}
