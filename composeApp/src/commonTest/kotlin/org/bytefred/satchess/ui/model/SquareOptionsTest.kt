package org.bytefred.satchess.ui.model

import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.PuzzleJson
import org.bytefred.satchess.puzzle.Start
import org.bytefred.satchess.puzzle.UNKNOWN_SQUARES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Square options: fixed piece / fixed empty / a set of options, and their mapping to Puzzle.squares. */
class SquareOptionsTest {
    private val a1 = Sq.parse("a1")!!
    private val e1 = Sq.parse("e1")!!
    private val h8 = Sq.parse("h8")!!
    private val kingOrEmpty = setOf('K', EMPTY)

    /** chess.com #2 style: a few fixed pieces, everything else open. */
    private val whereIsTheKing = Puzzle(
        plies = 0,
        start = Start(board = "????????/????????/????????/?r?b????/B???????/????????/????????/???k????"),
        project = listOf("piece@0:K"),
    )

    @Test
    fun cellsAreFixedAndUnknownSquaresHoldEverythingByDefault() {
        assertEquals(SquareState.Fixed('k'), whereIsTheKing.squareStateAt(0, Sq.parse("d1")!!))
        assertEquals(SquareState.Fixed(EMPTY), Puzzle().squareStateAt(0, Sq.parse("e4")!!))
        assertEquals(SquareState.Options(ALL_OPTIONS.toSet()), whereIsTheKing.squareStateAt(0, a1))
        assertEquals(ALL_OPTIONS.toSet(), whereIsTheKing.defaultOptionsAt(0))
    }

    @Test
    fun optionsOfASquareAreStoredAsQuestionMarkPlusDomain() {
        val p = Puzzle().withSquareOptions(0, e1, kingOrEmpty)
        assertEquals(UNKNOWN, p.boardAt(0)[e1])
        assertEquals(mapOf("0" to mapOf("e1" to "K.")), p.squares)
        assertEquals(SquareState.Options(kingOrEmpty), p.squareStateAt(0, e1))
        assertEquals(PuzzleJson.decode(PuzzleJson.encode(p)), p)
    }

    @Test
    fun aSingleOptionIsAFixedSquare() {
        val p = Puzzle().withSquareOptions(0, e1, kingOrEmpty).withSquareOptions(0, e1, setOf('Q'))
        assertEquals(SquareState.Fixed('Q'), p.squareStateAt(0, e1))
        assertEquals(emptyMap(), p.squares)
    }

    @Test
    fun allOptionsIsAPlainQuestionMark() {
        val p = Puzzle().withSquareOptions(0, e1, kingOrEmpty).withSquareOptions(0, e1, ALL_OPTIONS.toSet())
        assertEquals(UNKNOWN, p.boardAt(0)[e1])
        assertEquals(emptyMap(), p.squares)
    }

    @Test
    fun placingAPieceReplacesTheOptions() {
        val p = Puzzle().withSquareOptions(0, e1, kingOrEmpty)
        val placed = p.withCellsAt(0, paletteClick(p.boardAt(0), e1, 'R'))
        assertEquals(SquareState.Fixed('R'), placed.squareStateAt(0, e1))
        assertEquals(emptyMap(), placed.squares)
        // Editing another square keeps the options of e1.
        val other = p.withCellsAt(0, paletteClick(p.boardAt(0), a1, 'R'))
        assertEquals(mapOf("e1" to "K."), other.squares["0"])
    }

    @Test
    fun theDefaultForUnknownSquaresIsTheQuestionMarkKey() {
        val p = whereIsTheKing.withDefaultOptions(0, kingOrEmpty)
        assertEquals(mapOf("0" to mapOf(UNKNOWN_SQUARES to "K.")), p.squares)
        assertEquals(SquareState.Options(kingOrEmpty), p.squareStateAt(0, a1))
        assertEquals(SquareState.Fixed('k'), p.squareStateAt(0, Sq.parse("d1")!!))
        assertEquals(kingOrEmpty, p.optionsAt(0)[h8])
        assertNull(p.optionsAt(0)[Sq.parse("d1")!!])
        assertEquals(emptyMap(), p.withDefaultOptions(0, ALL_OPTIONS.toSet()).squares)
    }

    @Test
    fun ownOptionsOverrideTheDefaultAndOnlyDifferencesAreStored() {
        val p = whereIsTheKing.withDefaultOptions(0, kingOrEmpty)
        val any = p.withSquareOptions(0, a1, ALL_OPTIONS.toSet())
        assertEquals("KQRBNPkqrbnp.", any.squares["0"]?.get("a1"))
        assertEquals(SquareState.Options(ALL_OPTIONS.toSet()), any.squareStateAt(0, a1))
        // Back to the default: no own entry needed.
        assertEquals(mapOf(UNKNOWN_SQUARES to "K."), any.withSquareOptions(0, a1, kingOrEmpty).squares["0"])
        // A new default that equals an own entry makes it redundant.
        val own = p.withSquareOptions(0, a1, setOf('Q', 'q'))
        assertEquals(mapOf(UNKNOWN_SQUARES to "Qq"), own.withDefaultOptions(0, setOf('Q', 'q')).squares["0"])
    }

    @Test
    fun optionsAtLaterPliesMoveWithB() {
        val p = Puzzle(plies = 2).withDefaultOptions(2, kingOrEmpty).withSquareOptions(2, e1, setOf('R', 'r'))
        val longer = p.withPlies(4)
        assertEquals(mapOf("4" to mapOf(UNKNOWN_SQUARES to "K.", "e1" to "Rr")), longer.squares)
        assertEquals(SquareState.Options(setOf('R', 'r')), longer.squareStateAt(4, e1))
    }

    @Test
    fun importedDomainsAreReadLeniently() {
        val p = Puzzle(start = Start(board = "?7/8/8/8/8/8/8/8"), squares = mapOf("0" to mapOf("a8" to "kK.K", UNKNOWN_SQUARES to "x")))
        assertEquals(SquareState.Options(setOf('K', 'k', EMPTY)), p.squareStateAt(0, Sq.parse("a8")!!))
        assertEquals(ALL_OPTIONS.toSet(), p.defaultOptionsAt(0))
    }

    @Test
    fun noOptionsAreRejected() {
        assertFailsWith<IllegalArgumentException> { Puzzle().withSquareOptions(0, e1, emptySet()) }
        assertFailsWith<IllegalArgumentException> { Puzzle().withDefaultOptions(0, setOf('x')) }
    }

    @Test
    fun labels() {
        assertEquals("anything", optionsLabel(ALL_OPTIONS.toSet()))
        assertEquals("any piece", optionsLabel(ALL_OPTIONS.toSet() - EMPTY))
        assertEquals("K or empty", optionsLabel(kingOrEmpty))
        assertEquals("K, k or empty", optionsLabel(setOf(EMPTY, 'k', 'K')))
        assertEquals("empty", optionsLabel(setOf(EMPTY)))
        assertEquals("Kk.", optionsText(setOf(EMPTY, 'k', 'K')))
    }

    @Test
    fun boardsWithOnlyOptionsArePartialInTheTimeline() {
        assertEquals(ConstraintLevel.OPEN, constraintLevel(OPEN_BOARD))
        assertEquals(ConstraintLevel.PARTIAL, constraintLevel(OPEN_BOARD, restricted = true))
    }
}
