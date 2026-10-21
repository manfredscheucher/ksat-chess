package org.bytefred.satchess.sat

import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.RuleFlags
import org.bytefred.satchess.puzzle.Start
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

    private fun material(board: String) =
        solveAll(Puzzle(plies = 0, start = Start(board = board), rules = RuleFlags(materialSane = true))).count

    @Test fun ninePawns() = assertEquals(0, material("4k3/8/8/8/P7/8/PPPPPPPP/4K3"))
    @Test fun threeKnightsNeedAPromotion() = assertEquals(0, material("4k3/8/8/8/8/N7/PPPPPPPP/1N2K1N1"))
    @Test fun threeKnightsWithSevenPawns() = assertEquals(1, material("4k3/8/8/8/8/N7/PPPPPPP1/1N2K1N1"))
    @Test fun twoDarkBishops() = assertEquals(0, material("4k3/8/8/8/8/8/PPPPPPPP/2B1K1B1"))
    @Test fun lightAndDarkBishops() = assertEquals(1, material("4k3/8/8/8/8/8/PPPPPPPP/2B1KB2"))

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
