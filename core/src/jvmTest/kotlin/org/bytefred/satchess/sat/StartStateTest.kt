package org.bytefred.satchess.sat

import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Rules
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.Start
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Side to move, castling rights and en passant at ply 0. */
class StartStateTest {
    private val rooks = "r3k2r/8/8/8/8/8/8/R3K2R"

    private fun count(puzzle: Puzzle) = solveAll(puzzle).count

    private fun legal(fen: String) = Rules.legalMoves(Fen.parsePosition(fen)).size

    @Test
    fun blackToMove() {
        val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        assertEquals(Rules.perft(Fen.parsePosition(fen), 2).toInt(), count(puzzleFromFen(fen, 2)))
    }

    @Test
    fun castlingAutoNoneAndSubset() {
        assertEquals(legal("$rooks w KQkq - 0 1"), count(Puzzle(plies = 1, start = Start(board = rooks), project = listOf("moves"))))
        assertEquals(legal("$rooks w - - 0 1"), count(Puzzle(plies = 1, start = Start(board = rooks, castling = "-"), project = listOf("moves"))))
        assertEquals(legal("$rooks w Kq - 0 1"), count(Puzzle(plies = 1, start = Start(board = rooks, castling = "Kq"), project = listOf("moves"))))
    }

    @Test
    fun castlingFree() {
        val free = Puzzle(plies = 1, start = Start(board = rooks, castling = "?"), project = listOf("moves"))
        assertEquals(legal("$rooks w KQkq - 0 1"), count(free))
        val castled = solveAll(free.copy(positions = mapOf("1" to "r3k2r/8/8/8/8/8/8/R4RK1")))
        assertEquals(1, castled.count)
        assertTrue(castled.solutions.single().fens[0].split(' ')[2].contains('K'))
    }

    @Test
    fun castlingRightNeedsKingAndRookAtHome() =
        assertEquals(0, count(Puzzle(plies = 0, start = Start(board = "r3k3/8/8/8/8/8/8/R3K3", castling = "Kq"))))

    @Test
    fun enPassantSquareImpliesTheDoublePush() {
        val board = "4k3/8/8/3pP3/8/8/8/4K3"
        assertEquals(1, count(Puzzle(plies = 0, start = Start(board = board, enPassant = "d6"))))
        // Wrong side to move, or no pawn that could just have double-pushed:
        assertEquals(0, count(Puzzle(plies = 0, start = Start(board = board, sideToMove = "b", enPassant = "d6"))))
        assertEquals(0, count(Puzzle(plies = 0, start = Start(board = board, enPassant = "c6"))))
    }

    @Test
    fun enPassantOnlyWithTheSquare() {
        val board = "4k3/8/8/3pP3/8/8/8/4K3"
        val target = mapOf("1" to "4k3/8/3P4/8/8/8/8/4K3")
        assertEquals(1, count(Puzzle(plies = 1, start = Start(board = board, enPassant = "d6"), positions = target)))
        assertEquals(0, count(Puzzle(plies = 1, start = Start(board = board), positions = target)))
    }

    @Test
    fun sideToMoveFree() {
        val start = Start(board = "4k3/8/8/8/8/8/8/4K3", sideToMove = "?")
        assertEquals(1, count(Puzzle(plies = 0, start = start, project = listOf("board@0"))))
        assertEquals(2, count(Puzzle(plies = 0, start = start, project = listOf("board@0", "sideToMove@0"))))
        assertEquals(40, count(Puzzle(plies = 1, start = start.copy(board = "startpos"), project = listOf("moves"))))
    }
}
