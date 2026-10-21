package org.bytefred.satchess.ui.solver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.sat.PuzzleSolver
import org.bytefred.satchess.sat.SolveEvent
import org.bytefred.satchess.sat.SolveOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Desktop: the synchronous solver runs on Dispatchers.Default. Cancel sets the run's flag, which the
 * solver polls between SAT calls; events of a cancelled run are dropped. Any throwable ends the run
 * with a Failed event.
 */
actual fun createSolverService(): SolverService = CoroutineSolverService()

private class CoroutineSolverService : SolverService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var cancelled = AtomicBoolean(false)

    override fun solve(puzzle: Puzzle, options: SolveOptions, onEvent: (SolveEvent) -> Unit) {
        cancel()
        val runCancelled = AtomicBoolean(false)
        cancelled = runCancelled
        fun deliver(event: SolveEvent) {
            scope.launch(Dispatchers.Main) { if (!runCancelled.get()) onEvent(event) }
        }
        scope.launch {
            try {
                PuzzleSolver.solve(puzzle, options, isCancelled = { runCancelled.get() }, onEvent = ::deliver)
            } catch (e: Throwable) {
                // Also OutOfMemoryError / StackOverflowError: the UI must leave "solving ..." in any case.
                deliver(SolveEvent.Failed(e.message ?: e.toString()))
            }
        }
    }

    override fun cancel() {
        cancelled.set(true)
    }
}
