package org.bytefred.satchess.sat

import kotlinx.serialization.Serializable
import org.bytefred.ksat.facade.Solver
import org.bytefred.satchess.puzzle.Puzzle

@Serializable
data class SolveOptions(
    val solver: Solver = Solver.CADICAL,
    /** Stop after this many solutions; 0 = enumerate all. */
    val maxSolutions: Int = 1,
)

/** One solution, serializable so it can cross the Web Worker boundary. */
@Serializable
data class Solution(
    /** Full FEN of the position at every ply 0..plies. */
    val fens: List<String>,
    /** Move per ply (fens.size - 1 entries). */
    val moves: List<SolvedMove>,
    /** Human-readable value of the projection, e.g. "h4 = R" or the SAN line. */
    val projectedKey: String,
    /** True if the reference move generator confirmed every move and the goal. */
    val verified: Boolean,
)

@Serializable
data class SolvedMove(val uci: String, val san: String, val from: Int, val to: Int)

/** Progress events streamed by [PuzzleSolver.solve]. */
@Serializable
sealed interface SolveEvent {
    @Serializable data class Encoded(val variables: Int, val clauses: Int, val millis: Long) : SolveEvent
    @Serializable data class Found(val index: Int, val solution: Solution, val millis: Long) : SolveEvent
    @Serializable data class Info(val message: String) : SolveEvent
    /** [complete] = search space exhausted (count is exact), false = stopped at maxSolutions. */
    @Serializable data class Done(val count: Int, val complete: Boolean, val millis: Long) : SolveEvent
    @Serializable data class Failed(val message: String) : SolveEvent
}

object PuzzleSolver {
    /**
     * Encodes and solves [puzzle], streaming events to [onEvent]. Synchronous (call it off the
     * UI thread: a coroutine on JVM, the Web Worker on JS). [isCancelled] is polled between solves.
     */
    fun solve(
        puzzle: Puzzle,
        options: SolveOptions = SolveOptions(),
        isCancelled: () -> Boolean = { false },
        onEvent: (SolveEvent) -> Unit,
    ): Unit = TODO()
}
