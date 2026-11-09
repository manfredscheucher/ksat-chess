package org.bytefred.satchess.sat

import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.PuzzleJson
import org.bytefred.satchess.puzzle.Start
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The retro accounting of [History]: can the position at ply 0 have arisen from the initial array? */
class HistoryTest {
    /** 1 if the position (White to move) is consistent, 0 if not. */
    private fun consistent(board: String, sideToMove: String = "w"): Int =
        solveAll(Puzzle(plies = 0, start = Start(board = board, sideToMove = sideToMove, castling = "-"))).count

    @Test fun initialPosition() = assertEquals(1, consistent("startpos"))
    @Test fun bareKings() = assertEquals(1, consistent("4k3/8/8/8/8/8/8/4K3"))

    // identity: counts and bishop colours fall out of the matching
    @Test
    fun ninePawns() {
        val result = solveAll(Puzzle(plies = 0, start = Start(board = "4k3/8/8/8/P7/8/PPPPPPPP/4K3", castling = "-")))
        assertEquals(0, result.count)
        assertEquals(listOf(START_UNREACHABLE), result.infos)
    }
    @Test fun threeKnightsNeedAPromotion() = assertEquals(0, consistent("4k3/8/8/8/8/N7/PPPPPPPP/1N2K1N1"))
    @Test fun threeKnightsWithSevenPawns() = assertEquals(1, consistent("4k3/8/8/8/8/N7/PPPPPPP1/1N2K1N1"))
    @Test fun twoDarkBishops() = assertEquals(0, consistent("4k3/8/8/8/8/8/PPPPPPPP/2B1K1B1"))
    @Test fun lightAndDarkBishops() = assertEquals(1, consistent("4k3/8/8/8/8/8/PPPPPPPP/2B1KB2"))
    @Test fun twoKings() = assertEquals(0, consistent("4k3/8/8/8/8/8/8/K3K3"))

    // paths: a pawn's file shift costs one capture per file
    @Test fun pawnTwoFilesOverInOneRank() = assertEquals(0, consistent("4k3/8/8/8/8/2P5/1PPPPPPP/4K3"))
    @Test fun pawnOneFileOverNeedsAVictim() = assertEquals(0, consistent("rnbqkbnr/pppppppp/8/8/8/1P6/PP1PPPPP/RNBQKBNR"))
    @Test fun pawnOneFileOverWithAVictim() = assertEquals(1, consistent("rnbqkb1r/pppppppp/8/8/8/1P6/PP1PPPPP/RNBQKBNR"))

    // home squares: a pawn at home never moved, nothing else was ever on its square
    @Test fun promotionThroughAnOccupiedHomeSquare() =
        // White's b-pawn became the second queen: it had to pass b7, where Black's b-pawn has stood all game
        assertEquals(0, consistent("rnbqkbnr/pppppppp/8/8/3Q4/8/P1PPPPPP/RNBQKBNR"))

    @Test fun promotionThroughAVacatedHomeSquare() =
        // same, but Black's b-pawn left b7 by capturing White's a-pawn on a6
        assertEquals(1, consistent("rnbqkbnr/p1pppppp/p7/8/3Q4/8/2PPPPPP/RNBQKBNR"))

    // boxed bishops: with e7 and g7 at home the f8 bishop never moved
    @Test fun boxedBishopCannotBeCapturedElsewhere() =
        // bxc3 happened, the only missing black man is the f8 bishop
        assertEquals(0, consistent("rnbqk1nr/pppppppp/8/8/8/2P5/P1PPPPPP/RNBQKBNR"))

    @Test fun bishopThatCouldLeaveCanBeCapturedElsewhere() =
        assertEquals(1, consistent("rnbqk1nr/pppppp1p/6p1/8/8/2P5/P1PPPPPP/RNBQKBNR"))

    // crossing: pawns on one file never pass each other
    @Test fun promotionPathBlockedByAPawnThatNeverLeftItsFile() =
        // Black's h-pawn promoted (second queen). It had to leave the h-file (h2 never moved) by a capture,
        // the only missing white man is the dark-squared bishop, so the capture was on a dark square. With
        // the white g-pawn still on its file (g3) the only entry to the g-file below it is g2: light.
        assertEquals(0, consistent("rnbqkbnr/ppppppp1/8/7q/8/6P1/PPPPPP1P/RN1QKBNR"))

    @Test fun promotionPathFreeWhenTheFileIsOpen() =
        assertEquals(1, consistent("rnbqkbnr/ppppppp1/8/7q/8/8/PPPPPP1P/RN1QKBNR"))

    /**
     * Smullyan's "which piece on h4?" (chess.com #5): the white dark-squared bishop. Two plies of
     * history are needed: White's last move was cxd8=R+ (discovered check), and Black's move before
     * it rules out a queen or rook on d8 (it would have been check with White to move).
     */
    @Test
    fun smullyanPieceOnH4() {
        val puzzle = PuzzleJson.decode(File("../puzzles/chess-com-5/5.json").readText())
        assertEquals(listOf("h4 = B"), solveAll(puzzle).keys)
    }
}
