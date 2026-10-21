package org.bytefred.satchess.sat

import org.bytefred.ksat.facade.Solver
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.Start
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
        assertEquals(true, (events(puzzle).last() as SolveEvent.Done).shortest)
    }

    @Test
    fun shortestRefuted() {
        // 1.e4 e5 in 6 plies (with a knight trip) - but 1.e3 e6 2.e4 e5 does it in 4.
        val puzzle = Puzzle(
            plies = 6,
            positions = mapOf("6" to "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"),
            shortest = true,
        )
        assertEquals(false, (events(puzzle).last() as SolveEvent.Done).shortest)
    }

    @Test
    fun shorterPuzzleKeepsStartAndEnd() {
        val puzzle = Puzzle(plies = 5, positions = mapOf("2" to "x", "5" to "y"), squares = mapOf("0" to mapOf("a1" to "R")))
        val shorter = PuzzleSolver.shorterPuzzle(puzzle)!!
        assertEquals(3, shorter.plies)
        assertEquals(mapOf("3" to "y"), shorter.positions)
        assertEquals(puzzle.squares, shorter.squares)
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
