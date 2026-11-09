package org.bytefred.satchess.sat

import org.bytefred.satchess.chess.Color
import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Piece
import org.bytefred.satchess.chess.Position
import org.bytefred.satchess.chess.Rules
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.Start
import org.bytefred.satchess.puzzle.UNKNOWN_SQUARES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Projections and material sanity. */
class ProjectionTest {
    private fun keys(puzzle: Puzzle) = solveAll(puzzle).keys.toSet()

    @Test
    fun squareProjection() = assertEquals(
        setOf("h4 = empty") + "QRBNPqrbnp".map { "h4 = $it" },
        keys(Puzzle(plies = 0, start = Start(board = "4k3/8/8/8/7?/8/8/4K3"), project = listOf("square@0:h4"))),
    )

    @Test
    fun pieceProjection() = assertEquals(
        ('a'..'h').map { "K on ${it}1" }.toSet(),
        keys(Puzzle(plies = 0, start = Start(board = "k7/8/8/8/8/8/8/????????"), project = listOf("piece@0:K"))),
    )

    @Test
    fun movesOnlyProjectionMergesBoards() {
        // Two unknown squares; projecting only the moves counts each move (from, to, promotion) once.
        val puzzle = Puzzle(plies = 1, start = Start(board = "4k3/8/8/8/8/8/8/?3K2?"), project = listOf("moves"))
        val withBoard = solveAll(puzzle.copy(project = listOf("moves", "board@0"))).count
        val movesOnly = solveAll(puzzle).solutions.map { it.moves.single().uci }
        assertEquals(movesOnly.size, movesOnly.toSet().size)
        assertTrue(movesOnly.size < withBoard)
    }

    @Test
    fun freeSideCountsMovesOfBothColours() {
        // Ply 1: Kh8, Ke1 and a rook of either colour on a1. White's Ra2-a1 and Black's ...Ra2-a1
        // have the same from / to but are different moves: 14 white + 12 black moves.
        val puzzle = Puzzle(
            plies = 1,
            start = Start(board = "8/8/8/8/8/8/8/8".replace("8", "????????"), sideToMove = "?", castling = "-"),
            positions = mapOf("1" to "7k/8/8/8/8/8/8/?3K3"),
            squares = mapOf("1" to mapOf("a1" to "Rr")),
            project = listOf("moves"),
        )
        val perSide = listOf("w", "b").map { solveAll(puzzle.copy(start = puzzle.start.copy(sideToMove = it))).count }
        assertEquals(listOf(14, 12), perSide)
        assertEquals(26, solveAll(puzzle).count)
        assertEquals(26, solveAll(puzzle.copy(project = listOf("moves", "sideToMove@0"))).count)
    }

    @Test
    fun freeSideCountsABoardOnce() {
        // board@0 does not include the side: a board found in the white pass is not repeated.
        val puzzle = Puzzle(plies = 0, start = Start(board = "4k3/8/8/8/8/8/8/3?K3", sideToMove = "?"), squares = mapOf("0" to mapOf("d1" to "Q.")), project = listOf("board@0"))
        assertEquals(2, solveAll(puzzle).count)
    }

    @Test
    fun domainForAllUnknownSquares() {
        // chess.com #2: where is the white king? Every '?' square is a white king or empty.
        val puzzle = Puzzle(
            plies = 0,
            start = Start(board = "????????/????????/????????/?r?b????/B???????/????????/????????/???k????"),
            squares = mapOf("0" to mapOf(UNKNOWN_SQUARES to "K.")),
            project = listOf("board@0"),
        )
        val oracle = (0 until 64).filter { Fen.parsePattern(puzzle.start.board).cells[it] == '?' }.filter { sq ->
            val board = Fen.parsePattern(puzzle.start.board).cells.mapIndexed { i, c ->
                if (i == sq) Piece.WK else Piece.fromFen(c)
            }
            !Rules.isInCheck(Position(board), Color.BLACK) // White to move: Black is not in check
        }.map { "K on ${Sq.name(it)}" }
        assertEquals(listOf("K on b3"), oracle) // the Ba4 check on d1 must be blocked, c2 touches d1
        assertEquals(oracle.toSet(), keys(puzzle.copy(project = listOf("piece@0:K"))))
        assertEquals(1, solveAll(puzzle).count) // nothing else on the board
    }

    @Test
    fun explicitSquareOverridesTheUnknownDomain() {
        val puzzle = Puzzle(
            plies = 0,
            start = Start(board = "4k3/8/8/8/8/8/8/??2K3"),
            squares = mapOf("0" to mapOf(UNKNOWN_SQUARES to ".", "a1" to "R.")),
            project = listOf("board@0"),
        )
        assertEquals(setOf("4k3/8/8/8/8/8/8/R3K3", "4k3/8/8/8/8/8/8/4K3"), keys(puzzle))
    }

    @Test
    fun defaultSanityWithUnknownSquare() {
        val board = "4k3/8/8/8/8/?7/PPPPPPPP/4K3"
        assertEquals(setOf("a3 = empty"), keys(Puzzle(plies = 0, start = Start(board = board), squares = mapOf("0" to mapOf("a3" to "P.")), project = listOf("square@0:a3"))))
    }

    @Test
    fun noPawnsOnTheBackRanks() = assertEquals(
        setOf("a8 = empty"),
        keys(Puzzle(plies = 0, start = Start(board = "?3k3/8/8/8/8/8/8/4K3"), squares = mapOf("0" to mapOf("a8" to "Pp.")), project = listOf("square@0:a8"))),
    )
}
