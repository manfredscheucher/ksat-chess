package org.bytefred.satchess.chess

import kotlin.test.Test
import kotlin.test.assertEquals

/** The deepest perft counts (10^4..10^5 nodes); JVM only to keep the JS test run short. */
class PerftDeepTest {
    private fun perft(fen: String, depth: Int) = Rules.perft(Fen.parsePosition(fen), depth)

    @Test
    fun startDepth4() = assertEquals(197281L, perft(Fen.START, 4))

    @Test
    fun kiwipeteDepth3() =
        assertEquals(97862L, perft("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 3))

    @Test
    fun position3Depth4() = assertEquals(43238L, perft("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 4))

    @Test
    fun position4Depth3() =
        assertEquals(9467L, perft("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 3))

    @Test
    fun position5Depth3() = assertEquals(62379L, perft("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 3))
}
