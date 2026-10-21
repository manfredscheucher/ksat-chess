package org.bytefred.satchess.sat

import kotlinx.serialization.Serializable
import org.bytefred.ksat.SatResult
import org.bytefred.ksat.facade.Ksat
import org.bytefred.ksat.facade.Solver
import org.bytefred.satchess.chess.Color
import org.bytefred.satchess.puzzle.Goal
import org.bytefred.satchess.puzzle.Puzzle
import kotlin.time.TimeSource

@Serializable
data class SolveOptions(
    val solver: Solver = Solver.CADICAL,
    /** Stop after this many solutions; 0 = enumerate all. */
    val maxSolutions: Int = 1,
    val encoder: EncoderOptions = EncoderOptions(),
    val refinement: Refinement = Refinement.ESCAPE_MOVE,
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
    /**
     * [complete] = search space exhausted (count is exact), false = stopped at maxSolutions or
     * cancelled. [shortest]: null = not asked, true = plies - 2 proved impossible, false = a
     * shorter solution exists (or the check was cancelled).
     */
    @Serializable data class Done(
        val count: Int,
        val complete: Boolean,
        val millis: Long,
        val shortest: Boolean? = null,
    ) : SolveEvent
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
    ) {
        try {
            Run(puzzle, options, isCancelled, onEvent).run()
        } catch (e: IllegalArgumentException) {
            onEvent(SolveEvent.Failed(e.message ?: e.toString()))
        } catch (e: Exception) {
            onEvent(SolveEvent.Failed("internal error: ${e.message ?: e.toString()}"))
        }
    }

    /** The sides to move at ply 0 a puzzle asks for ("?" = both, solved one after the other). */
    fun sides(puzzle: Puzzle): List<Color> = when (puzzle.start.sideToMove) {
        "w" -> listOf(Color.WHITE)
        "b" -> listOf(Color.BLACK)
        "?" -> listOf(Color.WHITE, Color.BLACK)
        else -> throw IllegalArgumentException("start.sideToMove must be 'w', 'b' or '?', got '${puzzle.start.sideToMove}'")
    }

    private class Run(
        val puzzle: Puzzle,
        val options: SolveOptions,
        val isCancelled: () -> Boolean,
        val onEvent: (SolveEvent) -> Unit,
    ) {
        val clock = TimeSource.Monotonic.markNow()
        fun millis() = clock.elapsedNow().inWholeMilliseconds

        fun run() {
            val sides = sides(puzzle)
            val found = ArrayList<Set<String>>() // projected values of earlier passes, for "?"
            var count = 0
            var complete = true
            for (side in sides) {
                if (sides.size > 1) onEvent(SolveEvent.Info("pass: ${side.name.lowercase()} to move at ply 0"))
                val encoding = Encoding(puzzle, side, options.encoder)
                onEvent(SolveEvent.Encoded(encoding.cnf.numVars, encoding.cnf.numClauses, millis()))
                val projection = Projection(encoding)
                val search = Search(encoding, projection, options.solver, options.refinement)
                if (!projection.includesSide) for (names in found) search.addClause(projection.blockNames(names))
                while (true) {
                    if (isCancelled()) {
                        onEvent(SolveEvent.Info("cancelled"))
                        onEvent(SolveEvent.Done(count, complete = false, millis = millis()))
                        return
                    }
                    if (options.maxSolutions in 1..count) {
                        complete = false
                        break
                    }
                    val model = search.next() ?: break
                    val solution = search.decoder.solution(model)
                    found += projection.trueNames(model)
                    search.addClause(projection.block(model))
                    onEvent(SolveEvent.Found(count++, solution, millis()))
                }
                if (search.refinements > 0) {
                    onEvent(SolveEvent.Info("refinement: ${search.refinements} candidate final positions were no ${puzzle.goal.name.lowercase()}"))
                }
                if (!complete) break
            }
            val shortest = if (puzzle.shortest && count > 0) proveShortest() else null
            onEvent(SolveEvent.Done(count, complete, millis(), shortest))
        }

        /** Solves the same puzzle with plies - 2 (see [shorterPuzzle]); true if that is impossible. */
        private fun proveShortest(): Boolean? {
            val shorter = shorterPuzzle(puzzle) ?: return true.also {
                onEvent(SolveEvent.Info("shortest: fewer plies are impossible"))
            }
            for (side in sides(shorter)) {
                if (isCancelled()) return null
                val encoding = Encoding(shorter, side, options.encoder)
                val search = Search(encoding, Projection(encoding), options.solver, options.refinement)
                if (search.next() != null) {
                    onEvent(SolveEvent.Info("shortest: NOT shortest, ${shorter.plies} plies are enough"))
                    return false
                }
            }
            onEvent(SolveEvent.Info("shortest: ${shorter.plies} plies are impossible (UNSAT)"))
            return true
        }
    }

    /**
     * The puzzle with plies - 2 for the "shortest" check, or null if plies < 2. Constraints at
     * ply 0 stay, constraints at the last ply move to the new last ply, constraints in between
     * and move patterns are dropped (they have no counterpart in the shorter window).
     */
    internal fun shorterPuzzle(puzzle: Puzzle): Puzzle? {
        val n = puzzle.plies
        if (n < 2) return null
        fun <V> remap(map: Map<String, V>): Map<String, V> = buildMap {
            for ((key, value) in map) when (key.toIntOrNull()) {
                0 -> put(key, value)
                n -> put((n - 2).toString(), value)
            }
        }
        return puzzle.copy(
            plies = n - 2,
            positions = remap(puzzle.positions),
            squares = remap(puzzle.squares),
            moves = emptyMap(),
            shortest = false,
        )
    }
}

/**
 * One incremental solver over an [Encoding]. [next] returns the next model that also passes the
 * goal check of the move generator: checkmate / stalemate are encoded only as necessary
 * conditions, so a candidate whose final position is no real mate / stalemate is excluded by a
 * refinement clause (see [Refinement]) and the search continues.
 */
internal class Search(
    private val e: Encoding,
    projection: Projection,
    solver: Solver,
    private val refinement: Refinement,
) {
    val decoder = Decoder(e, projection)
    private val incremental = solver != Solver.MICROSAT
    private val extra = ArrayList<IntArray>()
    private val solverChoice = solver
    private var sat = newSolver()

    /** Candidates rejected by the move generator so far (checkmate / stalemate refinement). */
    var refinements = 0
        private set

    private fun newSolver(): Ksat = Ksat(solverChoice, e.cnf.numVars).also { k ->
        e.cnf.forEachClause { k.addClause(it) }
        for (c in extra) k.addClause(c)
    }

    fun addClause(clause: IntArray) {
        extra += clause
        if (incremental) sat.addClause(clause)
    }

    fun next(): ((Int) -> Boolean)? {
        val refine = e.puzzle.goal == Goal.CHECKMATE || e.puzzle.goal == Goal.STALEMATE
        while (true) {
            if (!incremental) sat = newSolver()
            if (sat.solve() != SatResult.SAT) return null
            val values = BooleanArray(e.cnf.numVars + 1) { it > 0 && sat.valueOf(it) }
            val model: (Int) -> Boolean = { values[it] }
            val last = decoder.position(model, e.plies)
            if (!refine || decoder.goalHolds(last)) return model
            addClause(refinementClause(e, model, last, refinement))
            refinements++
        }
    }
}
