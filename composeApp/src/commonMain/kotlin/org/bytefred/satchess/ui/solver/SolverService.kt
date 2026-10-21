package org.bytefred.satchess.ui.solver

import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.sat.SolveEvent
import org.bytefred.satchess.sat.SolveOptions

/**
 * Runs [org.bytefred.satchess.sat.PuzzleSolver.solve] off the UI thread: a coroutine on
 * Dispatchers.Default on the JVM, a Web Worker on the web (doc/spec.md sections 6 and 7).
 *
 * Events are delivered on the UI thread, in order. A new [solve] cancels the running one; after
 * [cancel] no further events of that run are delivered.
 */
interface SolverService {
    fun solve(puzzle: Puzzle, options: SolveOptions, onEvent: (SolveEvent) -> Unit)
    fun cancel()
}

/** The platform's solver service. */
expect fun createSolverService(): SolverService
