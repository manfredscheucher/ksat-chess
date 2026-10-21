package org.bytefred.satchess.ui.model

import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.Goal
import org.bytefred.satchess.puzzle.MovePattern
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.PuzzleJson
import org.bytefred.satchess.puzzle.Question
import org.bytefred.satchess.puzzle.Start
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PuzzleEditingTest {
    private val target = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"
    private val proofGame = Puzzle(plies = 2, positions = mapOf("2" to target))

    // ── puzzle <-> boards ──

    @Test
    fun boardsPerPlyComeFromStartAndPositions() {
        assertEquals(INITIAL_BOARD, proofGame.boardAt(0))
        assertEquals(Fen.parsePattern(target).cells, proofGame.boardAt(2))
        assertEquals(OPEN_BOARD, proofGame.boardAt(1))
    }

    @Test
    fun initialBoardIsStoredAsStartposAndOpenBoardsAreDropped() {
        val edited = proofGame.withBoardAt(0, CLEAR_BOARD).withBoardAt(0, INITIAL_BOARD)
        assertEquals("startpos", edited.start.board)
        assertEquals(emptyMap(), proofGame.withBoardAt(2, OPEN_BOARD).positions)
    }

    @Test
    fun editedBoardsSurviveAJsonRoundTrip() {
        val edited = proofGame.withBoardAt(1, paletteClick(OPEN_BOARD, 0, 'k'))
        val back = PuzzleJson.decode(PuzzleJson.encode(edited))
        assertEquals(edited, back)
        assertEquals('k', back.boardAt(1)[0])
    }

    @Test
    fun changingPliesMovesBAlongAndDropsWhatNoLongerFits() {
        val p = proofGame.copy(
            plies = 4,
            positions = mapOf("2" to target, "4" to target),
            moves = mapOf("0" to MovePattern(san = "e4"), "3" to MovePattern(san = "Nf3")),
            squares = mapOf("4" to mapOf("h4" to "Q")),
        )
        val shorter = p.withPlies(3)
        assertEquals(3, shorter.plies)
        assertEquals(setOf("2", "3"), shorter.positions.keys)
        assertEquals(setOf("0"), shorter.moves.keys)
        assertEquals(setOf("3"), shorter.squares.keys)
        val longer = p.withPlies(6)
        assertEquals(setOf("2", "6"), longer.positions.keys)
        assertEquals(setOf("0", "3"), longer.moves.keys)
    }

    @Test
    fun windowsOfZeroPliesKeepADomainsAndDoNotHideB() {
        val zero = Puzzle(plies = 0, squares = mapOf("0" to mapOf("h4" to "Q")))
        assertEquals(mapOf("0" to mapOf("h4" to "Q")), zero.withPlies(3).squares)
        val toZero = proofGame.copy(squares = mapOf("0" to mapOf("a1" to "R"), "2" to mapOf("h4" to "Q"))).withPlies(0)
        assertEquals(emptyMap(), toZero.positions)
        assertEquals(mapOf("0" to mapOf("a1" to "R")), toZero.squares)
    }

    @Test
    fun puzzleProblemsAreFoundBeforeLoading() {
        assertNull(puzzleProblem(proofGame))
        assertNotNull(puzzleProblem(proofGame.copy(plies = -1)))
        assertNotNull(puzzleProblem(proofGame.copy(plies = 100000)))
        assertNotNull(puzzleProblem(proofGame.copy(start = Start(board = "xyz"))))
        assertNotNull(puzzleProblem(proofGame.copy(positions = mapOf("2" to "8/8"))))
        assertNotNull(puzzleProblem(proofGame.copy(positions = mapOf("5" to target))))
        assertNotNull(puzzleProblem(proofGame.copy(moves = mapOf("2" to MovePattern(san = "e4")))))
        assertNotNull(puzzleProblem(proofGame.copy(squares = mapOf("x" to emptyMap()))))
    }

    // ── plies vs moves ──

    @Test
    fun movesFormatting() {
        assertEquals("6.5", formatMoves(13))
        assertEquals("6", formatMoves(12))
        assertEquals("0", formatMoves(0))
    }

    @Test
    fun pliesInputInBothUnits() {
        assertEquals(13, parsePliesInput("13", PliesUnit.PLIES))
        assertEquals(13, parsePliesInput("6.5", PliesUnit.MOVES))
        assertEquals(12, parsePliesInput("6", PliesUnit.MOVES))
        assertEquals(1, parsePliesInput(".5", PliesUnit.MOVES))
        assertNull(parsePliesInput("6.5", PliesUnit.PLIES))
        assertNull(parsePliesInput("-1", PliesUnit.PLIES))
        assertNull(parsePliesInput("", PliesUnit.PLIES))
        assertNull(parsePliesInput("abc", PliesUnit.MOVES))
        assertNull(parsePliesInput("1000", PliesUnit.PLIES))
        assertEquals(13, parsePliesInput("6,5", PliesUnit.MOVES))
        assertNull(parsePliesInput("-0.5", PliesUnit.MOVES))
        assertNull(parsePliesInput("+5", PliesUnit.PLIES))
        assertNull(parsePliesInput("6.3", PliesUnit.MOVES))
        assertNull(parsePliesInput(".", PliesUnit.MOVES))
    }

    @Test
    fun pliesSentence() {
        assertEquals(
            "13 plies = PG 6.5 · White starts · White moves last · Black to move at B",
            pliesSentence(13, "w"),
        )
        assertEquals(
            "4 plies = PG 2 · Black starts · White moves last · Black to move at B",
            pliesSentence(4, "b"),
        )
        assertEquals("1 ply = PG 0.5 · either side starts", pliesSentence(1, "?"))
        assertEquals("0 plies = PG 0 · position only · White to move", pliesSentence(0, "w"))
    }

    // ── presets ──

    @Test
    fun proofGamePresetStartsFromTheInitialPosition() {
        val p = applyPreset(Puzzle(plies = 4, start = Start(board = target, sideToMove = "b"), goal = Goal.CHECK), Question.PROOF_GAME)
        assertEquals(Question.PROOF_GAME, p.question)
        assertEquals(Start(), p.start)
        assertEquals(Goal.NONE, p.goal)
        assertEquals(listOf("moves"), p.project)
    }

    @Test
    fun proofGamePresetMovesACustomABoardToAnOpenB() {
        val p = applyPreset(Puzzle(plies = 2, start = Start(board = target)), Question.PROOF_GAME)
        assertEquals(INITIAL_BOARD, p.boardAt(0))
        assertEquals(Fen.parsePattern(target).cells, p.boardAt(2))
    }

    @Test
    fun lastMovesPresetKeepsTheGivenPositionAsB() {
        val p = applyPreset(Puzzle(plies = 2, start = Start(board = target)), Question.LAST_MOVES)
        assertEquals(OPEN_BOARD, p.boardAt(0))
        assertEquals(Fen.parsePattern(target).cells, p.boardAt(2))
        assertEquals("?", p.start.sideToMove)
    }

    @Test
    fun helpmatePresetKeepsTheGivenPositionAsA() {
        val p = applyPreset(Puzzle(plies = 4, start = Start(board = placementText(OPEN_BOARD)), positions = mapOf("4" to target)), Question.HELPMATE)
        assertEquals(Fen.parsePattern(target).cells, p.boardAt(0))
        assertEquals(OPEN_BOARD, p.boardAt(4))
        assertEquals(Goal.CHECKMATE, p.goal)
        assertEquals("b", p.start.sideToMove)
    }

    @Test
    fun unknownPiecePresetProjectsOnTheSingleUnknownSquare() {
        val board = setSquareText(INITIAL_BOARD, "h4", UNKNOWN)
        val p = applyPreset(Puzzle(plies = 2).withBoardAt(0, board), Question.UNKNOWN_PIECE)
        assertEquals(listOf("square@0:h4"), p.project)
        val two = applyPreset(Puzzle(plies = 2).withBoardAt(0, setSquareText(board, "a3", UNKNOWN)), Question.UNKNOWN_PIECE)
        assertEquals(listOf("board@0"), two.project)
    }

    @Test
    fun customPresetOnlyChangesTheQuestion() {
        assertEquals(proofGame.copy(question = Question.CUSTOM), applyPreset(proofGame, Question.CUSTOM))
    }

    // ── FEN field ──

    @Test
    fun fenFieldAcceptsPlacementsWithUnknowns() {
        val edit = applyFenText(proofGame, 1, "8/8/8/8/8/8/8/????K???")
        assertNull(edit.error)
        assertEquals('K', assertNotNull(edit.puzzle).boardAt(1)[60])
    }

    @Test
    fun fenFieldWithAFullFenSetsTheStartParameters() {
        val edit = applyFenText(proofGame, 0, "$target b KQkq e3 0 2")
        val p = assertNotNull(edit.puzzle)
        assertEquals(Start(board = target, sideToMove = "b", castling = "KQkq", enPassant = "e3"), p.start)
    }

    @Test
    fun invalidFenGivesAnErrorInsteadOfThrowing() {
        for (bad in listOf("", "xyz", "8/8/8", "9/8/8/8/8/8/8/8", "$target w", "$target x KQkq -", "$target w KQkq e9")) {
            val edit = applyFenText(proofGame, 0, bad)
            assertNull(edit.puzzle, "should reject '$bad'")
            assertNotNull(edit.error)
        }
        assertNotNull(applyFenText(proofGame, 1, "$target w - -").error)
    }

    @Test
    fun fenTextShowsThePlacementOfThePly() {
        assertEquals(Fen.START_PLACEMENT, proofGame.fenTextAt(0))
        assertEquals("????????/????????/????????/????????/????????/????????/????????/????????", proofGame.fenTextAt(1))
    }

    // ── start parameters, patterns, projection ──

    @Test
    fun castlingRightsToggleInCanonicalOrder() {
        assertEquals("Kq", toggleCastlingRight("q", 'K', true))
        assertEquals("-", toggleCastlingRight("K", 'K', false))
        assertEquals("k", toggleCastlingRight("auto", 'k', true))
        assertTrue(isCastlingField("KQkq"))
        assertFalse(isCastlingField("KK"))
        assertFalse(isCastlingField("X"))
    }

    @Test
    fun enPassantValidation() {
        assertNull(enPassantError("-", "w"))
        assertNull(enPassantError("e3", "b"))
        assertNull(enPassantError("d6", "?"))
        assertNotNull(enPassantError("e3", "w"))
        assertNotNull(enPassantError("e4", "b"))
        assertNotNull(enPassantError("z9", "b"))
    }

    @Test
    fun movePatternsAndSquareDomains() {
        val p = proofGame.withMovePattern(0, MovePattern(san = "e4")).withSquareDomain(1, "h4", "QR")
        assertEquals(setOf(0), p.movePatternPlies())
        assertEquals(mapOf("1" to mapOf("h4" to "QR")), p.squares)
        assertEquals(emptyMap(), p.withMovePattern(0, null).moves)
        assertEquals(emptyMap(), p.withSquareDomain(1, "h4", "").squares)
        assertEquals("Kf2", MovePattern(san = "Kf2").label())
        assertEquals("p x", MovePattern(piece = "p", capture = true).label())
        assertNull(squareDomainError("QR."))
        assertNotNull(squareDomainError("QX"))
    }

    @Test
    fun projectionParsing() {
        assertEquals(listOf("moves", "board@0"), parseProjection("moves, board@0"))
        assertEquals(listOf("square@0:h4", "piece@3:K", "sideToMove@0"), parseProjection("square@0:h4,piece@3:K , sideToMove@0"))
        assertNull(parseProjection(""))
        assertNull(parseProjection("moves, foo"))
        assertTrue(isPathProjection(listOf("moves", "board@0")))
        assertFalse(isPathProjection(listOf("square@0:h4")))
    }

    private fun setSquareText(cells: List<Char>, square: String, value: Char) =
        setSquare(cells, Sq.parse(square)!!, value)
}
