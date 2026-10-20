package org.bytefred.satchess.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {
    private fun pos(fen: String) = Fen.parsePosition(fen)

    private fun play(fen: String, vararg sans: String): Position =
        sans.fold(pos(fen)) { p, san -> Rules.apply(p, Rules.parseSan(p, san) ?: error("illegal $san")) }

    @Test
    fun perftStartShallow() {
        val start = pos(Fen.START)
        assertEquals(listOf(20L, 400L, 8902L), (1..3).map { Rules.perft(start, it) })
    }

    @Test
    fun perftKiwipeteShallow() {
        val p = pos("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(listOf(48L, 2039L), (1..2).map { Rules.perft(p, it) })
    }

    @Test
    fun perftPosition3Shallow() {
        val p = pos("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(listOf(14L, 191L, 2812L), (1..3).map { Rules.perft(p, it) })
    }

    @Test
    fun perftPosition4Shallow() {
        val p = pos("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals(listOf(6L, 264L), (1..2).map { Rules.perft(p, it) })
    }

    @Test
    fun perftPosition5Shallow() {
        val p = pos("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")
        assertEquals(listOf(44L, 1486L), (1..2).map { Rules.perft(p, it) })
    }

    @Test
    fun fenRoundTrips() {
        for (fen in listOf(
            Fen.START,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 1",
            "8/8/8/8/8/8/8/K6k b - - 0 1",
        )) assertEquals(fen, Fen.toFen(pos(fen)))
    }

    @Test
    fun clocksNormalizedAndPlacementOnly() {
        assertEquals("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w Kq - 0 1", Fen.toFen(pos("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w qK - 5 40")))
        val placementOnly = pos(Fen.START_PLACEMENT)
        assertEquals(Color.WHITE, placementOnly.sideToMove)
        assertTrue(placementOnly.castling.isEmpty())
        assertNull(placementOnly.enPassant)
        assertEquals("${Fen.START_PLACEMENT} w - - 0 1", Fen.toFen(placementOnly))
        assertEquals(Fen.START, Fen.toFen(pos("${Fen.START_PLACEMENT} w KQkq -")))
    }

    @Test
    fun invalidFenRejected() {
        assertFailsWith<IllegalArgumentException> { pos("8/8/8/8/8/8/8 w - - 0 1") }
        assertFailsWith<IllegalArgumentException> { pos("8/8/8/8/8/8/8/7 w - - 0 1") }
        assertFailsWith<IllegalArgumentException> { pos("8/8/8/8/8/8/8/9 w - - 0 1") }
        assertFailsWith<IllegalArgumentException> { pos("8/8/8/8/8/8/8/K6x w - - 0 1") }
        assertFailsWith<IllegalArgumentException> { pos("8/8/8/8/8/8/8/K6k w") }
        assertFailsWith<IllegalArgumentException> { pos("8/8/8/8/8/8/8/K6k x - -") }
        assertFailsWith<IllegalArgumentException> { pos("8/8/8/8/8/8/8/K6k w KX -") }
        assertFailsWith<IllegalArgumentException> { pos("8/8/8/8/8/8/8/K6k w - e3") }
        assertFailsWith<IllegalArgumentException> { pos("8/8/8/8/8/8/8/K6? w - -") }
    }

    @Test
    fun patternsWithUnknownSquares() {
        val p = Fen.parsePattern("????????/8/8/3?4/8/8/8/4K2?")
        assertEquals(10, p.cells.count { it == '?' })
        assertEquals('?', p.cells[Sq.parse("d5")!!])
        assertEquals('K', p.cells[Sq.parse("e1")!!])
        assertEquals('.', p.cells[Sq.parse("f1")!!])
        assertEquals("????????/8/8/3?4/8/8/8/4K2?", Fen.patternToString(p))
        assertEquals(Fen.START_PLACEMENT, Fen.patternToString(Fen.parsePattern("startpos")))
        assertTrue(Fen.parsePattern("startpos").isFixed)
        assertTrue(Fen.parsePattern(List(8) { "????????" }.joinToString("/")).isOpen)
        assertFailsWith<IllegalArgumentException> { Fen.parsePattern("???????/8/8/8/8/8/8/8") }
    }

    @Test
    fun applyUpdatesState() {
        val afterE4 = play(Fen.START, "e4")
        assertEquals(Sq.parse("e3"), afterE4.enPassant)
        assertEquals(Color.BLACK, afterE4.sideToMove)
        assertNull(play(Fen.START, "e4", "e6").enPassant)

        val kiwi = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        assertEquals(setOf(CastleRight.BLACK_KING, CastleRight.BLACK_QUEEN), play(kiwi, "O-O").castling)
        assertEquals("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R4RK1 b kq - 0 1", Fen.toFen(play(kiwi, "O-O")))
        assertEquals(setOf(CastleRight.WHITE_QUEEN, CastleRight.BLACK_KING, CastleRight.BLACK_QUEEN), play(kiwi, "Rf1").castling)
        // Rook captured on its home square loses the right.
        val capture = play("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "Rxa8")
        assertEquals(setOf(CastleRight.WHITE_KING, CastleRight.BLACK_KING), capture.castling)
    }

    @Test
    fun enPassantCaptureAndHorizontalPin() {
        val p = play("4k3/3p4/8/4P3/8/8/8/4K3 b - - 0 1", "d5")
        val ep = Rules.parseSan(p, "exd6")!!
        assertTrue(ep.enPassant)
        assertEquals("4k3/8/3P4/8/8/8/8/4K3 b - - 0 1", Fen.toFen(Rules.apply(p, ep)))
        // King and enemy rook on the same rank: e.p. would expose the king.
        val pinned = pos("8/8/8/K2pP2r/8/8/8/7k w - d6 0 1")
        assertNull(Rules.parseSan(pinned, "exd6"))
    }

    @Test
    fun castlingRestrictions() {
        val base = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"
        assertEquals(setOf("O-O", "O-O-O"), castles(base))
        assertEquals(setOf("O-O-O"), castles("r3k2r/8/8/8/8/8/8/R3K2R w Q - 0 1"))
        assertEquals(setOf("O-O"), castles("r3k2r/8/8/8/8/8/8/RN2K2R w KQkq - 0 1"))
        assertEquals(emptySet(), castles("4r1k1/8/8/8/8/8/8/R3K2R w KQ - 0 1"))
        assertEquals(setOf("O-O-O"), castles("5rk1/8/8/8/8/8/8/R3K2R w KQ - 0 1"))
        assertEquals(setOf("O-O-O"), castles("6r1/8/1k6/8/8/8/8/R3K2R w KQ - 0 1"))
        // b1 attacked does not prevent O-O-O; only the king's path matters.
        assertEquals(setOf("O-O", "O-O-O"), castles("1r4k1/8/8/8/8/8/8/R3K2R w KQ - 0 1"))
    }

    private fun castles(fen: String): Set<String> {
        val p = pos(fen)
        return Rules.legalMoves(p).filter { it.castle != null }.map { Rules.san(p, it) }.toSet()
    }

    @Test
    fun sanGeneration() {
        val start = pos(Fen.START)
        assertEquals(listOf("Nf3", "e4"), listOf(Move(Sq.parse("g1")!!, Sq.parse("f3")!!), Move(Sq.parse("e2")!!, Sq.parse("e4")!!)).map { Rules.san(start, it) })

        val knights = pos("4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1")
        assertEquals("Nbd2", Rules.san(knights, Rules.parseSan(knights, "Nbd2")!!))
        val rooks = pos("R7/8/7k/8/8/8/8/R3K3 w - - 0 1")
        assertEquals("R1a5", Rules.san(rooks, Move(Sq.parse("a1")!!, Sq.parse("a5")!!)))
        val queens = pos("4k3/8/8/8/8/8/Q1Q5/Q3K3 w - - 0 1")
        assertEquals("Qa2b1", Rules.san(queens, Move(Sq.parse("a2")!!, Sq.parse("b1")!!)))

        val promo = pos("1n2k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        assertEquals("axb8=Q+", Rules.san(promo, Move(Sq.parse("a7")!!, Sq.parse("b8")!!, PieceType.QUEEN)))
        assertEquals("a8=N", Rules.san(promo, Move(Sq.parse("a7")!!, Sq.parse("a8")!!, PieceType.KNIGHT)))
        assertEquals("O-O", Rules.san(pos("4k3/8/8/8/8/8/8/4K2R w K - 0 1"), Move(Sq.parse("e1")!!, Sq.parse("g1")!!)))
        assertEquals("Qh4#", Rules.san(play(Fen.START, "f3", "e5", "g4"), Move(Sq.parse("d8")!!, Sq.parse("h4")!!)))
        assertEquals("Bb5+", Rules.san(play(Fen.START, "e4", "d5"), Move(Sq.parse("f1")!!, Sq.parse("b5")!!)))
        assertFailsWith<IllegalArgumentException> { Rules.san(start, Move(Sq.parse("e2")!!, Sq.parse("e5")!!)) }
    }

    @Test
    fun sanParsingIsTolerant() {
        val start = pos(Fen.START)
        assertEquals(Move(Sq.parse("g1")!!, Sq.parse("f3")!!), Rules.parseSan(start, "Nf3!?"))
        assertNull(Rules.parseSan(start, "Nd2"))
        assertNull(Rules.parseSan(start, "e5"))
        assertNull(Rules.parseSan(start, "garbage"))

        val knights = pos("4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1")
        assertNull(Rules.parseSan(knights, "Nd2"), "ambiguous")
        assertEquals(Sq.parse("f1"), Rules.parseSan(knights, "Nfd2")?.from)

        val promo = pos("1n2k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        val a8 = Sq.parse("a8")!!
        for (s in listOf("a8=Q", "a8Q", "a8q", "a8=q+")) assertEquals(Move(Sq.parse("a7")!!, a8, PieceType.QUEEN), Rules.parseSan(promo, s), s)
        assertEquals(PieceType.BISHOP, Rules.parseSan(promo, "axb8b")?.promotion)
        assertEquals(PieceType.BISHOP, Rules.parseSan(promo, "ab8=B")?.promotion)
        assertNull(Rules.parseSan(promo, "a8"), "promotion piece missing")

        val castle = pos("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        for (s in listOf("O-O", "0-0", "o-o+")) assertEquals(CastleRight.WHITE_KING, Rules.parseSan(castle, s)?.castle, s)
        assertEquals(CastleRight.WHITE_QUEEN, Rules.parseSan(castle, "0-0-0")?.castle)

        val capture = play(Fen.START, "e4", "d5")
        assertEquals(Rules.parseSan(capture, "exd5"), Rules.parseSan(capture, "ed5"))
    }

    @Test
    fun foolsMateAndStalemate() {
        val mate = play(Fen.START, "f3", "e5", "g4", "Qh4#")
        assertTrue(Rules.isCheckmate(mate))
        assertTrue(Rules.isInCheck(mate))
        assertFalse(Rules.isStalemate(mate))

        val stalemate = pos("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue(Rules.isStalemate(stalemate))
        assertFalse(Rules.isCheckmate(stalemate))
        assertFalse(Rules.isCheckmate(pos(Fen.START)))
        assertFalse(Rules.isStalemate(pos(Fen.START)))
    }

    @Test
    fun attacks() {
        val start = pos(Fen.START)
        assertTrue(Rules.isAttacked(start, Sq.parse("f3")!!, Color.WHITE))
        assertFalse(Rules.isAttacked(start, Sq.parse("e4")!!, Color.WHITE))
        assertTrue(Rules.isAttacked(start, Sq.parse("e6")!!, Color.BLACK))
        assertFalse(Rules.isInCheck(start))
    }
}
