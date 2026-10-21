package org.bytefred.satchess.sat

import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Position
import org.bytefred.satchess.chess.Rules
import org.bytefred.satchess.puzzle.Goal
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.RuleFlags
import org.bytefred.satchess.puzzle.Start
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Goals at the last ply, checked against the move generator as oracle. */
class GoalTest {
    private fun holds(goal: Goal, p: Position) = when (goal) {
        Goal.NONE -> true
        Goal.CHECK -> Rules.isInCheck(p)
        Goal.CHECKMATE -> Rules.isCheckmate(p)
        Goal.STALEMATE -> Rules.isStalemate(p)
    }

    /** Number of [plies]-move lines from [fen] that end in [goal], by brute force. */
    private fun oracle(fen: String, plies: Int, goal: Goal): Int {
        fun count(p: Position, depth: Int): Int =
            if (depth == 0) (if (holds(goal, p)) 1 else 0)
            else Rules.legalMoves(p).sumOf { count(Rules.apply(p, it), depth - 1) }
        return count(Fen.parsePosition(fen), plies)
    }

    private fun sat(fen: String, plies: Int, goal: Goal, refinement: Refinement, rules: RuleFlags) = solveAll(
        puzzleFromFen(fen, plies, listOf("moves")).copy(goal = goal, rules = rules),
        options = SolveOptions(TEST_SOLVER, 0, refinement = refinement),
    ).count

    private fun assertMatchesOracle(fen: String, plies: Int, goal: Goal, rules: RuleFlags = RuleFlags()) {
        val expected = oracle(fen, plies, goal)
        for (refinement in Refinement.entries) {
            assertEquals(expected, sat(fen, plies, goal, refinement, rules), "$goal in $plies from $fen ($refinement)")
        }
    }

    @Test
    fun foolsMates() {
        val result = solveAll(Puzzle(plies = 4, goal = Goal.CHECKMATE, project = listOf("moves")))
        assertEquals(8, result.count)
        assertTrue(result.solutions.all { it.moves.last().san == "Qh4#" }, result.keys.toString())
    }

    @Test fun mateInOne() = assertMatchesOracle("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1", 1, Goal.CHECKMATE)
    @Test fun mateInOneWithManyCandidates() = assertMatchesOracle("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4", 1, Goal.CHECKMATE)
    @Test fun checksInOne() = assertMatchesOracle(SatPerftTest.KIWIPETE, 1, Goal.CHECK)
    @Test fun stalemateInOne() = assertMatchesOracle("7k/8/6Q1/8/8/8/8/K7 w - - 0 1", 1, Goal.STALEMATE)
    @Test fun stalemateInTwoBlackToMove() = assertMatchesOracle("k7/2Q5/8/8/8/8/8/6K1 b - - 0 1", 2, Goal.STALEMATE)
    @Test fun checkmateInTwo() = assertMatchesOracle("6k1/8/6K1/8/8/8/8/R7 b - - 0 1", 2, Goal.CHECKMATE)

    // Without exactly one king of the side to move, ESCAPE_MOVE falls back to the full state.
    private val kingsFree = RuleFlags(oneKingEach = false)
    @Test fun stalemateWithoutKingToMove() = assertMatchesOracle("4k3/8/8/8/8/8/8/R7 w - - 0 1", 0, Goal.STALEMATE, kingsFree)
    @Test fun stalemateInOneWithoutWhiteKing() = assertMatchesOracle("4k3/8/8/8/8/8/8/R7 w - - 0 1", 1, Goal.STALEMATE, kingsFree)
    @Test fun stalemateInOneWithoutBlackKing() = assertMatchesOracle("8/8/8/8/8/8/r7/7K b - - 0 1", 1, Goal.STALEMATE, kingsFree)
    @Test fun stalemateWithTwoKingsToMove() = assertMatchesOracle("4k3/8/8/8/8/8/8/K6K w - - 0 1", 0, Goal.STALEMATE, kingsFree)

    @Test
    fun checkmateWithOpenStartBoard() {
        // Back-rank mate: which white piece on d8 mates?
        val puzzle = Puzzle(
            plies = 0,
            start = Start(board = "3?3k/6pp/8/8/8/8/8/K7", sideToMove = "b"),
            squares = mapOf("0" to mapOf("d8" to "QRBN.")),
            goal = Goal.CHECKMATE,
            project = listOf("square@0:d8"),
        )
        assertEquals(setOf("d8 = Q", "d8 = R"), solveAll(puzzle).keys.toSet())
    }
}
