package org.bytefred.satchess.ui.model

import org.bytefred.satchess.chess.Sq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BoardEditingTest {
    private val e4 = Sq.parse("e4")!!
    private val e2 = Sq.parse("e2")!!

    @Test
    fun paletteClickPlacesTheSelectedPiece() {
        val board = paletteClick(CLEAR_BOARD, e4, 'N')
        assertEquals('N', board[e4])
        assertEquals(63, board.count { it == EMPTY })
    }

    @Test
    fun paletteClickOnTheSamePieceEmptiesTheSquare() {
        val board = paletteClick(paletteClick(CLEAR_BOARD, e4, 'N'), e4, 'N')
        assertEquals(CLEAR_BOARD, board)
    }

    @Test
    fun paletteClickOverwritesADifferentPiece() {
        assertEquals('q', paletteClick(paletteClick(CLEAR_BOARD, e4, 'N'), e4, 'q')[e4])
    }

    @Test
    fun paletteClickWithUnknownTogglesBetweenUnknownAndEmpty() {
        val once = paletteClick(CLEAR_BOARD, e4, UNKNOWN)
        assertEquals(UNKNOWN, once[e4])
        assertEquals(EMPTY, paletteClick(once, e4, UNKNOWN)[e4])
    }

    @Test
    fun dragMovesAPieceAndEmptiesItsOrigin() {
        val board = movePiece(INITIAL_BOARD, e2, e4)
        assertEquals('P', board[e4])
        assertEquals(EMPTY, board[e2])
    }

    @Test
    fun draggingAnEmptyOrUnknownSquareChangesNothing() {
        assertSame(OPEN_BOARD, movePiece(OPEN_BOARD, e2, e4))
        assertSame(CLEAR_BOARD, movePiece(CLEAR_BOARD, e2, e4))
        assertSame(INITIAL_BOARD, movePiece(INITIAL_BOARD, e2, e2))
    }

    @Test
    fun dragOffTheBoardRemovesThePiece() {
        assertEquals(EMPTY, removePiece(INITIAL_BOARD, e2)[e2])
    }

    @Test
    fun constraintLevelIsDerivedFromTheUnknownSquares() {
        assertEquals(ConstraintLevel.FIXED, constraintLevel(INITIAL_BOARD))
        assertEquals(ConstraintLevel.OPEN, constraintLevel(OPEN_BOARD))
        assertEquals(ConstraintLevel.PARTIAL, constraintLevel(setSquare(INITIAL_BOARD, e4, UNKNOWN)))
    }

    @Test
    fun paletteHasTwelvePiecesEmptyAndUnknown() {
        assertEquals("KQRBNPkqrbnp.?", PALETTE.joinToString(""))
    }
}
