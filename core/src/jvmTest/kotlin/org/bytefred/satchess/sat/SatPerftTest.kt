package org.bytefred.satchess.sat

import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Rules
import kotlin.test.Test
import kotlin.test.assertEquals

/** SAT perft: the number of move sequences the encoding admits must equal the move generator's perft. */
class SatPerftTest {
    private fun satPerft(fen: String, plies: Int) = solveAll(puzzleFromFen(fen, plies)).count

    @Test fun startDepth0() = assertEquals(1, satPerft(Fen.START, 0))
    @Test fun startDepth1() = assertEquals(20, satPerft(Fen.START, 1))
    @Test fun startDepth2() = assertEquals(400, satPerft(Fen.START, 2))

    @Test
    fun startDepth3() {
        if (!slowTests) return
        assertEquals(8902, satPerft(Fen.START, 3))
    }

    /** One ply from many positions: the SAT moves must be exactly the generator's legal moves. */
    @Test
    fun onePlyMatchesMoveGenerator() {
        for (fen in CROSS_CHECK_POSITIONS) {
            val expected = Rules.legalMoves(Fen.parsePosition(fen)).map { it.uci }.sorted()
            val actual = solveAll(puzzleFromFen(fen, 1, listOf("moves"))).solutions.map { it.moves.single().uci }.sorted()
            assertEquals(expected, actual, fen)
        }
    }

    @Test
    fun position3Depth2() = assertEquals(191, satPerft(CROSS_CHECK_POSITIONS[2], 2))

    @Test
    fun kiwipeteDepth2() {
        if (!slowTests) return
        assertEquals(2039, satPerft(KIWIPETE, 2))
    }

    companion object {
        const val KIWIPETE = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        val CROSS_CHECK_POSITIONS = listOf(
            Fen.START,
            KIWIPETE,
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3",
            "rnbqkbnr/pppp1ppp/8/8/3Pp3/4P3/PPP2PPP/RNBQKBNR b KQkq d3 0 3",
            "4k3/2P5/8/8/8/8/8/4K3 w - - 0 1",
            "3bk3/2P5/8/8/8/8/8/4K3 w - - 0 1",
            "4k3/8/8/8/8/8/2p5/4K3 b - - 0 1",
            "4r3/8/8/8/4R3/8/8/4K2k w - - 0 1",
            "7k/8/8/8/8/5r2/PPPPP1PP/R3K2R w KQ - 0 1",
            "4r2k/8/8/8/8/8/PPPP1PPP/R3K2R w KQ - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1",
            "8/8/8/2k5/3Pp3/8/8/4K3 b - d3 0 1",
        )
    }
}
