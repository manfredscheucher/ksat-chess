package org.bytefred.satchess.sat

import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.Start
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Rule tests ported from the Python project (tests/test_rules.py and configs/tests). */
class EncodingRulesTest {
    /** All first moves (UCI) from a placement with White to move and castling "auto". */
    private fun moves(placement: String): Set<String> =
        solveAll(Puzzle(plies = 1, start = Start(board = placement), project = listOf("moves")))
            .solutions.mapTo(HashSet()) { it.moves.single().uci }

    private fun from(placement: String, square: String) = moves(placement).filter { it.startsWith(square) }.toSet()

    /** Number of lines between two boards (White starts, project = moves). */
    private fun lines(plies: Int, vararg boards: Pair<Int, String>): Int = solveAll(
        Puzzle(
            plies = plies,
            start = Start(board = boards.first { it.first == 0 }.second),
            positions = boards.filter { it.first != 0 }.associate { it.first.toString() to it.second },
            project = listOf("moves"),
        ),
    ).count

    @Test fun kingInCentreHasEightMoves() =
        assertEquals(setOf("d4c5", "d4d5", "d4e5", "d4c4", "d4e4", "d4c3", "d4d3", "d4e3"), moves("8/8/8/8/3K4/8/8/4k3"))

    @Test fun kingInCornerHasThreeMoves() = assertEquals(3, moves("K7/8/8/8/8/8/8/7k").size)
    @Test fun rookHasFourteenMoves() = assertEquals(14, from("4k3/8/8/8/3R4/8/8/4K3", "d4").size)
    @Test fun knightInCentre() = assertEquals(8, from("4k3/8/8/8/3N4/8/8/4K3", "d4").size)
    @Test fun knightInCorner() = assertEquals(2, from("4k3/8/8/8/8/8/8/N3K3", "a1").size)
    @Test fun bishopHasThirteenMoves() = assertEquals(13, from("4k3/8/8/8/3B4/8/8/4K3", "d4").size)
    @Test fun pawnDoublePush() = assertEquals(setOf("d2d3", "d2d4"), from("4k3/8/8/8/8/8/3P4/4K3", "d2"))
    @Test fun pawnBlocked() = assertEquals(emptySet(), from("4k3/8/8/8/8/3P4/3P4/4K3", "d2"))
    @Test fun pawnCapturesDiagonally() =
        assertEquals(setOf("d2d3", "d2d4", "d2c3", "d2e3"), from("4k3/8/8/8/8/2p1p3/3P4/4K3", "d2"))
    @Test fun rookBlockedByOwnKing() = assertEquals(10, from("4k3/8/8/8/8/8/3P4/R3K3", "a1").size)
    @Test fun queenCombinesRookAndBishop() = assertEquals(27, from("4k3/8/8/8/3Q4/8/8/4K3", "d4").size)

    @Test
    fun kingCannotMoveIntoCheck() {
        val king = from("4k3/8/8/8/8/8/r7/4K3", "e1")
        assertEquals(setOf("e1d1", "e1f1"), king)
    }

    @Test
    fun pinnedRookStaysOnTheFile() {
        val rook = from("4r3/8/8/8/4R3/8/8/4K2k", "e4")
        assertTrue(rook.isNotEmpty() && rook.all { it[2] == 'e' }, rook.toString())
    }

    @Test fun bishopBlocksCheck() = assertEquals(setOf("c3e5"), from("4r3/8/8/8/8/2B5/8/4K2k", "c3"))

    @Test fun promotionToAllFourPieces() = assertEquals(4, from("4k3/2P5/8/8/8/8/8/4K3", "c7").size)
    @Test fun promotionWithCapture() = assertEquals(8, from("3bk3/2P5/8/8/8/8/8/4K3", "c7").size)

    @Test
    fun promotionResult() = assertEquals(1, lines(1, 0 to "4k3/2P5/8/8/8/8/8/4K3", 1 to "2Q1k3/8/8/8/8/8/8/4K3"))

    @Test
    fun foolsMateBoardsHaveTwoMoveOrders() =
        assertEquals(2, lines(4, 0 to "startpos", 4 to "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR"))

    @Test
    fun enPassantAfterDoublePush() =
        // 1.e3 b5 2.cxb6 e.p. and 1.e3 b6 2.cxb6
        assertEquals(2, lines(3, 0 to "rnbqkbnr/pppppppp/8/2P5/8/8/PP1PPPPP/RNBQKBNR", 3 to "rnbqkbnr/p1pppppp/1P6/8/8/4P3/PP1P1PPP/RNBQKBNR"))

    @Test
    fun enPassantOnlyImmediately() =
        // b7-b5 must be answered at once: with a white tempo move in between, b6 cannot be reached.
        assertEquals(0, lines(5, 0 to "rnbqkbnr/pppppppp/8/2P5/8/8/PP1PPPPP/RNBQKBNR", 5 to "rnbqkbnr/p1pppp1p/1P4p1/8/8/4P3/PP1P1PPP/RNBQKBNR"))

    @Test fun kingsideCastling() = assertEquals(1, lines(1, 0 to "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R", 1 to "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R4RK1"))
    @Test fun queensideCastling() = assertEquals(1, lines(1, 0 to "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R", 1 to "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/2KR3R"))

    @Test
    fun noCastlingThroughCheck() {
        val king = from("7k/8/8/8/8/5r2/PPPPP1PP/R3K2R", "e1")
        assertFalse("e1g1" in king)
        assertTrue("e1c1" in king)
    }

    @Test
    fun noCastlingOutOfCheck() {
        val king = from("4r2k/8/8/8/8/8/PPPP1PPP/R3K2R", "e1")
        assertFalse("e1g1" in king || "e1c1" in king)
    }

    @Test
    fun noCastlingWithPieceBetween() {
        val king = from("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R1B1K2R", "e1")
        assertFalse("e1c1" in king)
        assertTrue("e1g1" in king)
    }

    @Test
    fun castlingRightLostAfterKingMove() = assertEquals(
        0,
        lines(
            3,
            0 to "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R",
            1 to "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R4K1R",
            3 to "r4rk1/pppppppp/8/8/8/8/PPPPPPPP/R4RK1",
        ),
    )
}
