package org.bytefred.satchess.ui.solver

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.sat.SolveEvent
import org.bytefred.satchess.sat.SolveOptions
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The desktop service runs the real solver off the UI thread and delivers events on it. */
class CoroutineSolverServiceTest {

    @Test
    fun solvesAOnePlyProofGameAndDeliversEventsOnTheUiThread() = runBlocking {
        val puzzle = Puzzle(plies = 1, positions = mapOf("1" to "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"), project = listOf("moves"))
        val events = mutableListOf<SolveEvent>()
        val done = CompletableDeferred<Unit>()
        var allOnUiThread = true
        createSolverService().solve(puzzle, SolveOptions(maxSolutions = 0)) { event ->
            allOnUiThread = allOnUiThread && SwingUtilities.isEventDispatchThread()
            events += event
            if (event is SolveEvent.Done || event is SolveEvent.Failed) done.complete(Unit)
        }
        withTimeout(30_000) { done.await() }
        assertTrue(allOnUiThread)
        val found = events.filterIsInstance<SolveEvent.Found>()
        assertEquals(listOf("e4"), found.single().solution.moves.map { it.san })
        assertEquals(SolveEvent.Done::class, events.last()::class)
    }
}
