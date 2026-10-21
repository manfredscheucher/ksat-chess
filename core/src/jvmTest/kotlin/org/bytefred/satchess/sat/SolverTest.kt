package org.bytefred.satchess.sat

import org.bytefred.ksat.facade.Solver
import org.bytefred.satchess.puzzle.MovePattern
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.Start
import org.bytefred.satchess.puzzle.UNKNOWN_SQUARES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The driver: events, limits, cancellation, errors, shortest, solver choice. */
class SolverTest {
    private fun events(puzzle: Puzzle, options: SolveOptions = SolveOptions(TEST_SOLVER), cancel: (Int) -> Boolean = { false }): List<SolveEvent> {
        val events = ArrayList<SolveEvent>()
        PuzzleSolver.solve(puzzle, options, { cancel(events.count { it is SolveEvent.Found }) }) { events += it }
        return events
    }

    @Test
    fun invalidFenFails() {
        val failed = events(Puzzle(plies = 1, start = Start(board = "rnbqkbnr/pppppppp"))).single() as SolveEvent.Failed
        assertTrue("8 ranks" in failed.message, failed.message)
    }

    @Test
    fun invalidProjectionFails() {
        val e = events(Puzzle(plies = 1, project = listOf("board@5"))).last()
        assertTrue(e is SolveEvent.Failed && "outside" in e.message, e.toString())
    }

    @Test
    fun maxSolutionsStopsEarly() {
        val done = events(Puzzle(plies = 1), SolveOptions(TEST_SOLVER, maxSolutions = 5)).last() as SolveEvent.Done
        assertEquals(5, done.count)
        assertFalse(done.complete)
    }

    @Test
    fun cancelBetweenSolves() {
        val list = events(Puzzle(plies = 1), SolveOptions(TEST_SOLVER, maxSolutions = 0)) { found -> found >= 3 }
        val done = list.last() as SolveEvent.Done
        assertEquals(3, done.count)
        assertFalse(done.complete)
    }

    @Test
    fun eventOrder() {
        val list = events(Puzzle(plies = 1), SolveOptions(TEST_SOLVER, maxSolutions = 0))
        assertTrue(list.first() is SolveEvent.Encoded)
        assertEquals(20, list.count { it is SolveEvent.Found })
        assertEquals(SolveEvent.Done::class, list.last()::class)
        assertTrue((list.last() as SolveEvent.Done).complete)
    }

    @Test
    fun shortestProved() {
        val puzzle = Puzzle(
            plies = 4,
            positions = mapOf("4" to "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR"),
            shortest = true,
        )
        val done = events(puzzle).last() as SolveEvent.Done
        assertEquals(true, done.shortest)
        assertEquals(ShortestCheck.PROVEN, done.shortestCheck)
    }

    @Test
    fun shortestWithProjectionAtTheLastPly() {
        // board@2 must become board@0 in the 0-ply check instead of failing as out of range.
        val puzzle = Puzzle(
            plies = 2,
            positions = mapOf("2" to "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"),
            shortest = true,
            project = listOf("board@2", "square@2:e4", "piece@1:P"),
        )
        val done = events(puzzle).last() as SolveEvent.Done
        assertEquals(1, done.count)
        assertEquals(ShortestCheck.PROVEN, done.shortestCheck)
    }

    @Test
    fun shorterPuzzleRemapsProjection() {
        val puzzle = Puzzle(plies = 4, project = listOf("moves", "board@0", "board@4", "square@4:h4", "piece@2:K", "sideToMove@0"))
        assertEquals(listOf("moves", "board@0", "board@2", "square@2:h4", "sideToMove@0"), PuzzleSolver.shorterPuzzle(puzzle)!!.puzzle.project)
    }

    @Test
    fun shortestOfRelaxedPuzzleIsNotProven() {
        // 1.e4 e5 in 6 plies starting with 1.Nf3 (the knight has to return). The 4-ply check drops
        // the move pattern and finds 1.e3 e6 2.e4 e5, which does not start with Nf3: not proven.
        val puzzle = Puzzle(
            plies = 6,
            positions = mapOf("6" to "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"),
            moves = mapOf("0" to MovePattern(san = "Nf3")),
            shortest = true,
        )
        val list = events(puzzle, SolveOptions(TEST_SOLVER, maxSolutions = 1))
        val done = list.last() as SolveEvent.Done
        assertEquals(false, done.shortest)
        assertEquals(ShortestCheck.NOT_PROVEN_RELAXED, done.shortestCheck)
        assertTrue(list.any { it is SolveEvent.Info && "relaxed" in it.message })
    }

    @Test
    fun shorterPuzzleMergesTheEndsIntoPlyZero() {
        val puzzle = Puzzle(
            plies = 2,
            start = Start(board = "4k3/8/8/8/8/8/8/????K???"),
            positions = mapOf("2" to "4????/8/8/8/8/8/8/R???????"),
            squares = mapOf("0" to mapOf("a1" to "RQ.", "b1" to "N."), "2" to mapOf(UNKNOWN_SQUARES to "k.", "h1" to "R.")),
        )
        val shorter = PuzzleSolver.shorterPuzzle(puzzle)!!.puzzle
        assertEquals(0, shorter.plies)
        assertEquals("4k3/8/8/8/8/8/8/R???K???", shorter.start.board)
        val domains = shorter.squares.getValue("0")
        assertEquals(mapOf("a1" to "RQ.", "b1" to ".", "h1" to "R.", "e8" to "k.", "f8" to "k."), domains.filterKeys { it in setOf("a1", "b1", "h1", "e8", "f8") })
        assertEquals(setOf("0"), shorter.squares.keys)
        // Contradicting ends: no 0-ply window exists.
        assertEquals(null, PuzzleSolver.shorterPuzzle(puzzle.copy(positions = mapOf("2" to "8/8/8/8/8/8/8/4K3"))))
    }

    @Test
    fun errorsEndWithFailed() {
        val list = events(Puzzle(plies = 1)) { throw NotImplementedError("boom") }
        val failed = list.last() as SolveEvent.Failed
        assertTrue("boom" in failed.message, failed.message)
    }

    @Test
    fun shortestRefuted() {
        // 1.e4 e5 in 6 plies (with a knight trip) - but 1.e3 e6 2.e4 e5 does it in 4.
        val puzzle = Puzzle(
            plies = 6,
            positions = mapOf("6" to "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"),
            shortest = true,
        )
        val done = events(puzzle).last() as SolveEvent.Done
        assertEquals(false, done.shortest)
        assertEquals(ShortestCheck.NOT_SHORTEST, done.shortestCheck)
    }

    @Test
    fun shorterPuzzleKeepsStartAndEnd() {
        val puzzle = Puzzle(plies = 5, positions = mapOf("2" to "x", "5" to "y"), squares = mapOf("0" to mapOf("a1" to "R")))
        val shorter = PuzzleSolver.shorterPuzzle(puzzle)!!
        assertEquals(3, shorter.puzzle.plies)
        assertEquals(mapOf("3" to "y"), shorter.puzzle.positions)
        assertEquals(puzzle.squares, shorter.puzzle.squares)
        assertTrue(shorter.relaxed) // the board at ply 2 was dropped
    }

    @Test
    fun everySolverWorks() {
        for (solver in Solver.entries) {
            val done = events(Puzzle(plies = 1), SolveOptions(solver, maxSolutions = 0)).last() as SolveEvent.Done
            assertEquals(20, done.count, solver.name)
        }
    }

    @Test
    fun rowColVariantAgrees() {
        val options = SolveOptions(TEST_SOLVER, maxSolutions = 0, encoder = EncoderOptions(MoveAmo.ROW_COL))
        assertEquals(400, (events(Puzzle(plies = 2), options).last() as SolveEvent.Done).count)
    }
}
