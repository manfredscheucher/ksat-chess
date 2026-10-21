package org.bytefred.satchess.sat

import org.bytefred.satchess.puzzle.Goal
import org.bytefred.satchess.puzzle.MovePattern
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.Start
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Move patterns (SAN and object form) as constraints on single plies. */
class PatternTest {
    private fun count(puzzle: Puzzle) = solveAll(puzzle).count

    private fun onePly(fen: String, pattern: MovePattern) =
        solveAll(puzzleFromFen(fen, 1, listOf("moves")).copy(moves = mapOf("0" to pattern))).solutions.map { it.moves.single().san }.toSet()

    private val foolsMate = Puzzle(plies = 4, goal = Goal.CHECKMATE, project = listOf("moves"))

    @Test fun firstMoveFixed() = assertEquals(2, count(foolsMate.copy(moves = mapOf("0" to MovePattern(san = "f3")))))
    @Test fun lastMoveWithMateSign() = assertEquals(8, count(foolsMate.copy(moves = mapOf("3" to MovePattern(san = "Qh4#")))))
    @Test fun impossibleSan() = assertEquals(0, count(foolsMate.copy(moves = mapOf("3" to MovePattern(san = "Qxh4")))))

    @Test
    fun objectPattern() = assertEquals(
        2,
        count(foolsMate.copy(moves = mapOf("1" to MovePattern(piece = "p", to = "e5"), "2" to MovePattern(from = "g2")))),
    )

    @Test
    fun disambiguation() {
        val fen = "4k3/8/8/8/8/8/8/1N1NK3 w - - 0 1"
        assertEquals(setOf("Nbc3"), onePly(fen, MovePattern(san = "Nbc3")))
        assertEquals(setOf("Nbc3", "Ndc3"), onePly(fen, MovePattern(piece = "N", to = "c3")))
    }

    @Test
    fun captureFlag() {
        val fen = "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1"
        assertEquals(setOf("exd5"), onePly(fen, MovePattern(capture = true)))
        assertEquals(setOf("exd5"), onePly(fen, MovePattern(san = "exd5")))
    }

    @Test
    fun enPassantCountsAsCapture() =
        assertEquals(setOf("exd6"), onePly("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1", MovePattern(capture = true)))

    @Test
    fun castlingPatterns() {
        val fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"
        assertEquals(setOf("O-O"), onePly(fen, MovePattern(san = "O-O")))
        assertEquals(setOf("O-O-O"), onePly(fen, MovePattern(castle = "Q")))
        assertEquals(setOf("O-O", "O-O-O"), onePly(fen, MovePattern(castle = "any")))
        assertEquals(emptySet(), onePly(fen, MovePattern(castle = "k")))
        assertEquals(emptySet(), onePly(fen, MovePattern(san = "Kg1")))
    }

    @Test
    fun promotionPattern() {
        assertEquals(setOf("c8=N"), onePly("4k3/2P5/8/8/8/8/8/4K3 w - - 0 1", MovePattern(promo = "n")))
        assertEquals(setOf("c8=Q+"), onePly("4k3/2P5/8/8/8/8/8/4K3 w - - 0 1", MovePattern(san = "c8=Q")))
    }

    @Test
    fun checkFlag() {
        val fen = "4k3/8/8/8/8/8/8/R3K3 w - - 0 1"
        assertEquals(setOf("Ra8+"), onePly(fen, MovePattern(piece = "R", check = true)))
        val quiet = onePly(fen, MovePattern(piece = "R", check = false))
        assertEquals(9, quiet.size)
        assertTrue(quiet.none { it.endsWith("+") })
    }

    @Test
    fun blackPiecePatternWhenBlackMoves() = assertEquals(
        setOf("Nf6"),
        onePly("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1", MovePattern(san = "Nf6")),
    )

    @Test
    fun accelerated() {
        // chess.com puzzle 3 shape: White's moves fixed as SAN on even plies, Black mates in 8 plies.
        if (!slowTests) return
        val puzzle = Puzzle(
            plies = 8,
            start = Start(),
            moves = mapOf("0" to "f3", "2" to "Kf2", "4" to "Kg3", "6" to "Kh4").mapValues { MovePattern(san = it.value) },
            goal = Goal.CHECKMATE,
            project = listOf("moves"),
        )
        assertEquals(1, solveAll(puzzle, max = 1).count)
    }
}
