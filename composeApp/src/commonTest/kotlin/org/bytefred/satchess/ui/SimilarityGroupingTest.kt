package org.bytefred.satchess.ui

import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.sat.Solution
import org.bytefred.satchess.sat.SolvedMove
import org.bytefred.satchess.ui.model.SimilarGroup
import org.bytefred.satchess.ui.model.SimilarGrouper
import org.bytefred.satchess.ui.model.SimilarityKey
import org.bytefred.satchess.ui.model.similarityKey
import org.bytefred.satchess.ui.model.similaritySummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Group similar solutions": key = sorted white events + sorted black events (real lines via the move generator). */
class SimilarityGroupingTest {

    @Test
    fun eventsArePieceToSquarePlusCapturedPieceSplitByColour() {
        val key = similarityKey(sampleSolution("e4", "d5", "exd5"))
        assertEquals(
            SimilarityKey(
                start = Fen.START,
                white = listOf("white pawn to d5 x black pawn", "white pawn to e4"),
                black = listOf("black pawn to d5"),
            ),
            key,
        )
    }

    @Test
    fun swappingTheOrderOfTwoWhiteMovesGroupsTogether() {
        val a = sampleSolution("f3", "e5", "g4", "Qh4#")
        val b = sampleSolution("g4", "e5", "f3", "Qh4#")
        assertEquals(similarityKey(a), similarityKey(b))
        assertEquals(listOf(listOf(0, 1)), groupSimilar(listOf(a, b)).map { it.indices })
    }

    @Test
    fun whiteAndBlackEventsAreNeverMixed() {
        val key = similarityKey(sampleSolution("Nf3", "Nf6", "Nc3", "Nc6", "e4", "d5", "exd5"))!!
        assertTrue(key.white.all { it.startsWith("white ") })
        assertTrue(key.black.all { it.startsWith("black ") })
        assertEquals(4, key.white.size)
        assertEquals(3, key.black.size)
    }

    @Test
    fun sameTargetSquareByTheOtherColourDoesNotGroup() {
        // After 1. Nc3 Nf6 2. Nd5 Nxd5 vs 1. Nc3 Nf6 2. Nb5 Nd5: different events; and the d5 knight
        // moves of White and Black stay separate even when both land on d5.
        val a = sampleSolution("Nc3", "Nf6", "Nd5", "Nxd5")
        val b = sampleSolution("Nc3", "Nf6", "Nb5", "Nd5")
        assertTrue("white knight to d5" in similarityKey(a)!!.white)
        assertTrue("black knight to d5 x white knight" in similarityKey(a)!!.black)
        assertTrue("black knight to d5" in similarityKey(b)!!.black)
        assertNotEquals(similarityKey(a), similarityKey(b))
    }

    @Test
    fun castlingIsTheKingMoveAndUnreadableLinesFormTheirOwnGroup() {
        val castled = similarityKey(sampleSolution("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "O-O"))!!
        assertTrue("white king to g1" in castled.white)
        val broken = Solution(listOf("garbage"), listOf(SolvedMove("e2e4", "e4", 52, 36)), "k", verified = false)
        assertNull(similarityKey(broken))
        assertEquals(listOf(listOf(0), listOf(1)), groupSimilar(listOf(broken, broken)).map { it.indices })
        assertEquals("(unreadable line)", similaritySummary(null))
        assertEquals("White: white pawn to e4 · Black: -", similaritySummary(similarityKey(sampleSolution("e4"))))
    }

    @Test
    fun differentCapturedPieceDoesNotGroup() {
        // White knight takes on d5: once a pawn, once a knight.
        val pawn = sampleSolution("Nc3", "d5", "Nxd5")
        val knight = sampleSolution("Nc3", "Nf6", "e4", "Nd5", "Nxd5")
        val pawnEvent = similarityKey(pawn)!!.white.single { "x" in it }
        val knightEvent = similarityKey(knight)!!.white.single { "x" in it }
        assertEquals("white knight to d5 x black pawn", pawnEvent)
        assertEquals("white knight to d5 x black knight", knightEvent)
    }

    @Test
    fun enPassantCountsAsCapturingAPawn() {
        val key = similarityKey(sampleSolution("e4", "a6", "e5", "d5", "exd6"))!!
        assertEquals(true, "white pawn to d6 x black pawn" in key.white)
    }

    @Test
    fun differentEventsStaySeparateGroupsInFoundOrder() {
        val lines = listOf(sampleSolution("e4", "e5"), sampleSolution("d4", "e5"), sampleSolution("e4", "e5"))
        assertEquals(listOf(listOf(0, 2), listOf(1)), groupSimilar(lines).map { it.indices })
    }

    @Test
    fun groupsGrowIncrementally() {
        val lines = listOf(sampleSolution("e4", "e5", "Nf3"), sampleSolution("d4", "d5"), sampleSolution("Nf3", "e5", "e4"))
        val grouper = SimilarGrouper()
        grouper.add(lines[0])
        assertEquals(listOf(listOf(0)), grouper.groups().map { it.indices })
        lines.drop(1).forEach(grouper::add)
        assertEquals(3, grouper.size)
        assertEquals(listOf(listOf(0, 2), listOf(1)), grouper.groups().map { it.indices })
    }

    @Test
    fun differentStartPositionsNeverGroup() {
        // the same event "white knight to f3", once from g1 and once from h4 (an open A, "what was the last move?")
        val fromG1 = Solution(
            listOf("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1"),
            listOf(SolvedMove("g1f3", "Nf3", 62, 45)), "Nf3", verified = true,
        )
        val fromH4 = Solution(
            listOf("rnbqkbnr/pppppppp/8/8/7N/8/PPPPPPPP/RNBQKB1R w KQkq - 0 1", "rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1"),
            listOf(SolvedMove("h4f3", "Nf3", 39, 45)), "Nf3", verified = true,
        )
        assertEquals(similarityKey(fromG1)!!.white, similarityKey(fromH4)!!.white)
        assertEquals(2, groupSimilar(listOf(fromG1, fromH4)).size)
    }

    private fun groupSimilar(solutions: List<Solution>): List<SimilarGroup> =
        SimilarGrouper().apply { solutions.forEach(::add) }.groups()
}
