package org.bytefred.satchess.sat

import kotlinx.serialization.Serializable
import org.bytefred.ksat.SatResult
import org.bytefred.ksat.facade.Ksat
import org.bytefred.ksat.facade.Solver
import org.bytefred.satchess.chess.BoardPattern
import org.bytefred.satchess.chess.Color
import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.Goal
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.UNKNOWN_SQUARES
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
     * cancelled. [shortestCheck]: outcome of the `shortest` check, null = not asked (or nothing
     * found). [shortest] is the same as a flag: null = not asked, true = plies - 2 proved
     * impossible, false = not proven (see [shortestCheck] for why).
     */
    @Serializable data class Done(
        val count: Int,
        val complete: Boolean,
        val millis: Long,
        val shortest: Boolean? = null,
        val shortestCheck: ShortestCheck? = null,
    ) : SolveEvent
    @Serializable data class Failed(val message: String) : SolveEvent
}

/** Outcome of the `shortest` check: is the same puzzle solvable with plies - 2 (doc/encoding.md section 6)? */
@Serializable
enum class ShortestCheck {
    /** plies - 2 is impossible (UNSAT): proven shortest. */
    PROVEN,
    /** The puzzle with plies - 2 and all its constraints has a solution: not shortest. */
    NOT_SHORTEST,
    /**
     * Only a relaxed puzzle could be checked (move patterns and constraints at intermediate plies
     * dropped) and it has a solution: neither proven nor refuted.
     */
    NOT_PROVEN_RELAXED,
    /** Cancelled before the check finished. */
    CANCELLED,
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
        val problems = puzzleProblems(puzzle)
        if (problems.isNotEmpty()) {
            onEvent(SolveEvent.Failed(problems.joinToString("; ")))
            return
        }
        // Every path ends with Done or Failed: the UI waits for one of them.
        try {
            Run(puzzle, options, isCancelled, onEvent).run()
        } catch (e: IllegalArgumentException) {
            onEvent(SolveEvent.Failed(e.message ?: e.toString()))
        } catch (e: Throwable) {
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
                if (!projection.includesSide) for (names in found) projection.blockNames(names)?.let(search::addClause)
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
            val check = if (puzzle.shortest && count > 0) checkShortest() else null
            onEvent(SolveEvent.Done(count, complete, millis(), check?.let { it == ShortestCheck.PROVEN }, check))
        }

        /** Solves the same puzzle with plies - 2 (see [shorterPuzzle]). */
        private fun checkShortest(): ShortestCheck {
            val shorter = shorterPuzzle(puzzle) ?: return ShortestCheck.PROVEN.also {
                onEvent(SolveEvent.Info("shortest: fewer plies are impossible"))
            }
            val plies = shorter.puzzle.plies
            for (side in sides(shorter.puzzle)) {
                if (isCancelled()) return ShortestCheck.CANCELLED
                val encoding = Encoding(shorter.puzzle, side, options.encoder)
                val search = Search(encoding, Projection(encoding), options.solver, options.refinement)
                if (search.next() == null) continue
                return if (shorter.relaxed) {
                    onEvent(SolveEvent.Info("shortest: not proven, $plies plies are enough without the move patterns / intermediate constraints (relaxed check)"))
                    ShortestCheck.NOT_PROVEN_RELAXED
                } else {
                    onEvent(SolveEvent.Info("shortest: NOT shortest, $plies plies are enough"))
                    ShortestCheck.NOT_SHORTEST
                }
            }
            onEvent(SolveEvent.Info("shortest: $plies plies are impossible (UNSAT)"))
            return ShortestCheck.PROVEN
        }
    }

    /** The plies - 2 puzzle of the "shortest" check; [relaxed] = some constraints were dropped. */
    internal class ShorterPuzzle(val puzzle: Puzzle, val relaxed: Boolean)

    /**
     * The puzzle with plies - 2 for the "shortest" check, or null if no shorter window can have a
     * solution (plies < 2, or with plies = 2 the boards / domains at ply 0 and ply 2 contradict).
     * Constraints and projection items at ply 0 stay, those at the last ply move to the new last
     * ply (merged with ply 0 if that is the new last ply), those in between and move patterns are
     * dropped (they have no counterpart in the shorter window). Expects [puzzleProblems] to be empty.
     */
    internal fun shorterPuzzle(puzzle: Puzzle): ShorterPuzzle? {
        val n = puzzle.plies
        if (n < 2) return null
        val last = (n - 2).toString()
        val firstBoard = puzzle.positions["0"]
        val lastBoard = puzzle.positions[n.toString()]
        val firstSquares = puzzle.squares["0"]
        val lastSquares = puzzle.squares[n.toString()]
        var start = puzzle.start
        val positions = HashMap<String, String>()
        val squares = HashMap<String, Map<String, String>>()
        if (n > 2) {
            firstBoard?.let { positions["0"] = it }
            lastBoard?.let { positions[last] = it }
            firstSquares?.let { squares["0"] = it }
            lastSquares?.let { squares[last] = it }
        } else {
            // Ply 2 becomes ply 0: one board and one set of domains for both.
            val boards = listOfNotNull(start.board, firstBoard)
            val merged = mergeBoards(boards + listOfNotNull(lastBoard)) ?: return null
            start = start.copy(board = merged)
            val domains = intersectDomains(
                expandUnknown(firstSquares, mergeBoards(boards)!!),
                expandUnknown(lastSquares, lastBoard ?: OPEN_BOARD),
            ) ?: return null
            if (domains.isNotEmpty()) squares["0"] = domains
        }
        val project = puzzle.project.mapNotNull { item ->
            when (parseProjectItem(item, n).ply) {
                null, 0 -> item
                n -> item.substringBefore('@') + "@$last" + item.substringAfter('@').dropWhile { it.isDigit() }
                else -> null
            }
        }
        fun dropsPlies(keys: Set<String>) = keys.any { it != "0" && it != n.toString() }
        val relaxed = puzzle.moves.isNotEmpty() || dropsPlies(puzzle.positions.keys) || dropsPlies(puzzle.squares.keys)
        val shorter = puzzle.copy(
            plies = n - 2,
            start = start,
            positions = positions,
            squares = squares,
            moves = emptyMap(),
            shortest = false,
            project = project,
        )
        return ShorterPuzzle(shorter, relaxed)
    }
}

private val OPEN_BOARD = List(8) { "????????" }.joinToString("/")

/** The board pattern that has the fixed cells of all [boards], or null if two of them differ. */
internal fun mergeBoards(boards: List<String>): String? {
    val cells = MutableList(64) { '?' }
    for (board in boards) for ((sq, c) in Fen.parsePattern(board).cells.withIndex()) {
        if (c == '?') continue
        if (cells[sq] != '?' && cells[sq] != c) return null
        cells[sq] = c
    }
    return Fen.patternToString(BoardPattern(cells))
}

/** [domains] with the [UNKNOWN_SQUARES] entry spelled out per '?' square of [board]. */
private fun expandUnknown(domains: Map<String, String>?, board: String): Map<String, String> {
    if (domains == null) return emptyMap()
    val unknown = domains[UNKNOWN_SQUARES] ?: return domains
    val cells = Fen.parsePattern(board).cells
    val explicit = domains - UNKNOWN_SQUARES
    return explicit + (0 until 64).filter { cells[it] == '?' && Sq.name(it) !in explicit }
        .associate { Sq.name(it) to unknown }
}

/** Domains that hold on both sides (per square the common characters), or null if one becomes empty. */
private fun intersectDomains(a: Map<String, String>, b: Map<String, String>): Map<String, String>? {
    val result = HashMap(a)
    for ((name, allowed) in b) {
        val common = a[name]?.filter { it in allowed } ?: allowed
        if (common.isEmpty()) return null
        result[name] = common
    }
    return result
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
    // MINISAT runs with its default option pre=1 (SimpSolver): blocking clauses would use variables
    // the first solve eliminated (see ksat enumerateModels)
    private val incremental = solver != Solver.MICROSAT && solver != Solver.MINISAT
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
