package org.bytefred.satchess.sat

import org.bytefred.satchess.puzzle.MovePattern
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.Start
import org.bytefred.satchess.puzzle.UNKNOWN_SQUARES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidationTest {
    private fun problem(puzzle: Puzzle, part: String) {
        val problems = puzzleProblems(puzzle)
        assertTrue(problems.any { part in it }, "expected '$part' in $problems")
    }

    @Test
    fun validPuzzleHasNoProblems() = assertEquals(
        emptyList(),
        puzzleProblems(
            Puzzle(
                plies = 2,
                start = Start(board = "startpos", sideToMove = "?", castling = "Kq", enPassant = "e6"),
                positions = mapOf("0" to "startpos", "2" to "????????/8/8/8/8/8/8/4K3"),
                squares = mapOf("2" to mapOf("a8" to "Rr.", UNKNOWN_SQUARES to "K.")),
                moves = mapOf("0" to MovePattern(san = "Nf3"), "1" to MovePattern(piece = "p", capture = true)),
                project = listOf("moves", "board@2", "square@0:h4", "piece@1:K", "sideToMove@0"),
            ),
        ),
    )

    @Test fun negativePlies() = problem(Puzzle(plies = -1), "plies must be >= 0")
    @Test fun positionsOutsideTheWindow() = problem(Puzzle(plies = 2, positions = mapOf("3" to "startpos")), "positions: ply 3 is outside 0..2")
    @Test fun positionsKeyNoNumber() = problem(Puzzle(plies = 2, positions = mapOf("x" to "startpos")), "not a number")
    @Test fun squaresOutsideTheWindow() = problem(Puzzle(plies = 1, squares = mapOf("2" to mapOf("a1" to "R"))), "squares: ply 2")
    @Test fun movesAtTheLastPly() = problem(Puzzle(plies = 2, moves = mapOf("2" to MovePattern(san = "e4"))), "moves: ply 2 is outside 0..1")
    @Test fun projectOutOfRange() = problem(Puzzle(plies = 1, project = listOf("board@5")), "outside 0..1")
    @Test fun projectUnknownItem() = problem(Puzzle(plies = 1, project = listOf("pieces")), "project 'pieces'")
    @Test fun badSide() = problem(Puzzle(start = Start(sideToMove = "x")), "start.sideToMove")
    @Test fun badCastling() = problem(Puzzle(start = Start(castling = "KX")), "start.castling")
    @Test fun badEnPassant() = problem(Puzzle(start = Start(enPassant = "e4")), "rank 3 or 6")
    @Test fun badStartBoard() = problem(Puzzle(start = Start(board = "8/8")), "8 ranks")
    @Test fun badPositionBoard() = problem(Puzzle(plies = 1, positions = mapOf("1" to "8/8/8/8/8/8/8/9")), "'9'")
    @Test fun badSquare() = problem(Puzzle(squares = mapOf("0" to mapOf("i9" to "R"))), "invalid square 'i9'")
    @Test fun badDomainChar() = problem(Puzzle(squares = mapOf("0" to mapOf(UNKNOWN_SQUARES to "Kx"))), "invalid piece 'x'")
    @Test fun emptyDomain() = problem(Puzzle(squares = mapOf("0" to mapOf("a1" to ""))), "empty domain")
    @Test fun badSan() = problem(Puzzle(plies = 1, moves = mapOf("0" to MovePattern(san = "Zz9"))), "cannot read SAN")

    @Test
    fun allProblemsAreListed() =
        assertEquals(2, puzzleProblems(Puzzle(plies = 1, start = Start(sideToMove = "x"), project = listOf("board@9"))).size)
}
