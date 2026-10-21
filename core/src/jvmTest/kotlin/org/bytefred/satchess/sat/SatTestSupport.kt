package org.bytefred.satchess.sat

import org.bytefred.ksat.facade.Solver
import org.bytefred.satchess.chess.CastleRight
import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.Start
import kotlin.test.assertTrue
import kotlin.test.fail

/** Slow tests (8-ply puzzles, SAT perft 3) only run with `-PslowTests`. */
val slowTests: Boolean = System.getProperty("slowTests") == "true"

/**
 * Solver for the encoding tests: Kissat is the fastest port on these small instances (the
 * CaDiCaL port has a per-variable setup cost, see doc/encoding.md). SolverChoiceTest covers the others.
 */
val TEST_SOLVER = Solver.KISSAT

/** Faster checkmate / stalemate refinement for the tests; GoalTest checks both modes against the oracle. */
val TEST_REFINEMENT = Refinement.ESCAPE_MOVE

class SolveResult(val solutions: List<Solution>, val done: SolveEvent.Done, val infos: List<String>) {
    val count: Int get() = done.count
    val keys: List<String> get() = solutions.map { it.projectedKey }
}

/** Runs the solver to the end (all solutions unless [max] > 0) and fails on [SolveEvent.Failed]. */
fun solveAll(puzzle: Puzzle, max: Int = 0, options: SolveOptions = SolveOptions(TEST_SOLVER, max, refinement = TEST_REFINEMENT)): SolveResult {
    val solutions = ArrayList<Solution>()
    val infos = ArrayList<String>()
    var done: SolveEvent.Done? = null
    PuzzleSolver.solve(puzzle, options) { event ->
        when (event) {
            is SolveEvent.Found -> solutions += event.solution
            is SolveEvent.Done -> done = event
            is SolveEvent.Failed -> fail("solver failed: ${event.message}")
            is SolveEvent.Info -> infos += event.message
            is SolveEvent.Encoded -> Unit
        }
    }
    val result = SolveResult(solutions, done ?: fail("no Done event"), infos)
    assertTrue(solutions.all { it.verified }, "unverified solution: ${solutions.firstOrNull { !it.verified }}")
    return result
}

/** A puzzle starting from a full FEN (side, castling and e.p. taken from the FEN). */
fun puzzleFromFen(fen: String, plies: Int, project: List<String> = listOf("moves", "board@0")): Puzzle {
    val position = Fen.parsePosition(fen)
    val castling = CastleRight.entries.filter { it in position.castling }.joinToString("") { it.fen.toString() }
    return Puzzle(
        plies = plies,
        start = Start(
            board = Fen.placement(position.board),
            sideToMove = position.sideToMove.fen.toString(),
            castling = castling.ifEmpty { "-" },
            enPassant = position.enPassant?.let { Sq.name(it) } ?: "-",
        ),
        project = project,
    )
}
