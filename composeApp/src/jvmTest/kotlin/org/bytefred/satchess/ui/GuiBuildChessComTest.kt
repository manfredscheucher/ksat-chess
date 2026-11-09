package org.bytefred.satchess.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.ui.platform.savePref
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Builds the five bundled chess.com puzzles (puzzles/chess-com-5) from File > New using only what a
 * human can do in the GUI: menus, dropdowns, chips, palette, board clicks, text fields, dialogs. The
 * view model is only read for the final comparison. Each step is logged with a running count
 * (see the test's system-out).
 *
 * Gap: the projection (`project`) has no UI any more (removed on purpose, 2026-11-09). Only the
 * question preset sets it. For puzzles 1, 3, 4 (A = initial position) "moves" and the file's default
 * "moves, board@0" are the same thing; puzzle 5 gets its "square@0:h4" from the preset; puzzle 2's
 * "piece@3:K" cannot be reached (see [puzzle2ProjectionPieceAt3K]).
 */
@OptIn(ExperimentalTestApi::class)
class GuiBuildChessComTest {
    private fun resetPrefs() {
        savePref(WELCOME_PREF, "off")
        savePref(COORDINATES_PREF, "true")
        savePref(INPUT_MODE_PREF, InputMode.HYBRID.name)
        savePref(LAYOUT_PREF, LayoutMode.AUTO.name)
    }

    @BeforeTest fun before() = resetPrefs()
    @AfterTest fun after() = resetPrefs()

    private fun bundled(n: Int): Puzzle = loadExamples().first { it.puzzle.id == "chess-com-5/$n" }.puzzle

    /** The built puzzle with the metadata a human does not enter (title, credits, expected) taken over. */
    private fun Puzzle.withMetaOf(b: Puzzle) =
        copy(id = b.id, title = b.title, composer = b.composer, source = b.source, description = b.description, expected = b.expected)

    /** Human interactions, one log line each. */
    private class Steps(val name: String) {
        val log = mutableListOf<String>()
        fun step(text: String) { log += "${log.size + 1}. $text" }
        fun print() = println("== $name: ${log.size} interactions\n" + log.joinToString("\n"))
    }

    private fun ComposeUiTest.newPuzzle(s: Steps) {
        onNodeWithTag("menu-File").performClick(); s.step("File menu")
        onNodeWithText("New").performClick(); s.step("New")
    }

    private fun ComposeUiTest.question(s: Steps, label: String) {
        onNodeWithTag("question").performScrollTo().performClick(); s.step("Question dropdown")
        onNodeWithText(label).performClick(); s.step("pick '$label'")
    }

    private fun ComposeUiTest.plies(s: Steps, n: Int) {
        onNodeWithTag("plies").performScrollTo().performTextReplacement(n.toString()); s.step("type N = $n")
    }

    private fun ComposeUiTest.ply(s: Steps, label: String) {
        val chip = onNodeWithTag("ply-$label")
        chip.performScrollTo()
        if (chip.fetchSemanticsNode().boundsInRoot.isEmpty) {
            // scrolled away above the board (performScrollTo does not reach into the sideways-scrolling
            // timeline): swipe the panel down, like a human
            onNodeWithTag("boardLegend").performTouchInput { swipeDown(startY = top, endY = top + 2000f) }
            s.step("scroll up to the timeline")
        }
        chip.performClick(); s.step("timeline chip $label")
    }

    private fun ComposeUiTest.button(s: Steps, label: String) {
        onNodeWithText(label).performScrollTo().performClick(); s.step("button '$label'")
    }

    private fun ComposeUiTest.palette(s: Steps, piece: Char) {
        onNodeWithTag("palette-$piece").performScrollTo().performClick(); s.step("palette $piece")
    }

    /** Clicks [squares] on the editor board (a8 = top left, coordinates shown as in the default view). */
    private fun ComposeUiTest.squares(s: Steps, vararg squares: String) {
        for (name in squares) {
            val sq = Sq.parse(name)!!
            val file = sq % 8
            val row = sq / 8
            onNodeWithTag("editorBoard").performScrollTo().performTouchInput {
                click(Offset((file + 0.5f) * width / 8f, (row + 0.5f) * height / 8f))
            }
            s.step("click $name")
        }
    }

    /** Places [piece] on [squares] (or empties them if they already hold it). */
    private fun ComposeUiTest.place(s: Steps, piece: Char, vararg squares: String) {
        palette(s, piece)
        squares(s, *squares)
    }

    private fun ComposeUiTest.movePattern(s: Steps, plyChip: String, san: String) {
        ply(s, plyChip)
        onNodeWithTag("timelineAdd").performScrollTo().performClick(); s.step("timeline '+'")
        onNodeWithText("Move pattern ...").performClick(); s.step("'Move pattern ...'")
        // fields of the dialog: ply t (prefilled with the selected ply), SAN, piece, from, to, promo
        onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))[1].performTextReplacement(san); s.step("type SAN $san")
        onNodeWithText("Save").performClick(); s.step("Save")
    }

    private fun ComposeUiTest.build(n: Int, steps: ComposeUiTest.(Steps) -> Unit): Puzzle {
        val vm = PuzzleViewModel(FakeSolverService())
        setContent { App(vm) }
        waitForIdle()
        val s = Steps("chess.com #$n")
        newPuzzle(s)
        steps(s)
        waitForIdle()
        s.print()
        return vm.puzzle
    }

    // ── 1: proof game in 4.0, both kingside knights gone, ...d5 ──────────────────

    private fun ComposeUiTest.build1() = build(1) { s ->
        // New is already "Proof game", A = initial position
        plies(s, 8)
        ply(s, "B")
        button(s, "Initial")
        place(s, 'N', "g1")
        place(s, 'n', "g8")
        place(s, 'p', "d7", "d5")
    }

    @Test
    fun puzzle1KnightRoundTrip() = runComposeUiTest {
        val b = bundled(1)
        val built = build1()
        assertEquals(listOf("moves"), built.project) // = the file's default "moves, board@0" for a fixed A
        assertEquals(b, built.withMetaOf(b).copy(project = b.project))
    }

    // ── 2: where is the white king? ─────────────────────────────────────────────

    private fun ComposeUiTest.build2() = build(2) { s ->
        question(s, "Unknown piece")
        plies(s, 3)
        // A: everything open, side to move and castling free
        button(s, "All ?")
        button(s, "? (either)")
        button(s, "? (free)")
        // B: the four known pieces; every other square is the white king or empty
        ply(s, "B")
        place(s, 'r', "b5")
        place(s, 'b', "d5")
        place(s, 'B', "a4")
        place(s, 'k', "d1")
        onNodeWithTag("unknownSquaresOptions").performScrollTo().performClick(); s.step("'Every ? square: Change ...'")
        onNodeWithText("White king or empty").performClick(); s.step("shortcut 'White king or empty'")
        onNodeWithTag("applyOptions").performClick(); s.step("Apply")
    }

    @Test
    fun puzzle2WhereIsTheWhiteKing() = runComposeUiTest {
        val b = bundled(2)
        val built = build2()
        // the preset sees no single '?' on A and projects the whole A board; the UI has no way to ask
        // "where is the white king at ply 3" (piece@3:K)
        assertEquals(listOf("board@0"), built.project)
        assertEquals(b, built.withMetaOf(b).copy(project = b.project))
    }

    @Ignore // The projection field has no UI (removed 2026-11-09); no preset yields piece@3:K.
    @Test
    fun puzzle2ProjectionPieceAt3K() = runComposeUiTest {
        val b = bundled(2)
        assertEquals(b, build2().withMetaOf(b))
    }

    // ── 3: helpmate, White plays f3 Kf2 Kg3 Kh4 ─────────────────────────────────

    private fun ComposeUiTest.build3() = build(3) { s ->
        question(s, "Helpmate") // also sets goal checkmate, but Black to move first
        button(s, "White")
        plies(s, 8)
        movePattern(s, "A", "f3")
        movePattern(s, "2", "Kf2")
        movePattern(s, "4", "Kg3")
        movePattern(s, "6", "Kh4")
    }

    @Test
    fun puzzle3KingWalkIntoMate() = runComposeUiTest {
        val b = bundled(3)
        val built = build3()
        assertEquals(listOf("moves"), built.project)
        assertEquals(b, built.withMetaOf(b).copy(project = b.project))
    }

    // ── 4: where did the bishop go? ─────────────────────────────────────────────

    private fun ComposeUiTest.build4() = build(4) { s ->
        plies(s, 8)
        ply(s, "B")
        button(s, "Initial")
        place(s, 'p', "c7", "c6", "d7", "e7", "e6")
        place(s, 'P', "e2", "e4")
        place(s, 'B', "f1")
    }

    @Test
    fun puzzle4WhereDidTheBishopGo() = runComposeUiTest {
        val b = bundled(4)
        val built = build4()
        assertEquals(listOf("moves"), built.project)
        assertEquals(b, built.withMetaOf(b).copy(project = b.project))
    }

    // ── 5: the piece on h4 (Smullyan): a 2-ply window, the diagram at B ──────────

    private fun ComposeUiTest.build5() = build(5) { s ->
        question(s, "Unknown piece")
        plies(s, 2)
        // A: everything open, side to move free
        button(s, "All ?")
        button(s, "? (either)")
        // B: the diagram; h4 holds some piece of either colour
        ply(s, "B")
        button(s, "Clear")
        place(s, 'K', "h8")
        place(s, 'k', "b7")
        place(s, 'R', "d8", "d7")
        place(s, 'n', "c8", "a1")
        place(s, 'p', "a7", "e7", "g7", "a6", "d6", "c5", "c4")
        place(s, 'P', "a5", "b4", "c3", "d3", "g3", "e2", "h2")
        onNodeWithText("One of several ...").performScrollTo().performClick(); s.step("tool 'One of several ...'")
        squares(s, "h4")
        onNodeWithText("Any piece (not empty)").performClick(); s.step("shortcut 'Any piece (not empty)'")
        onNodeWithTag("applyOptions").performClick(); s.step("Apply")
    }

    @Test
    fun puzzle5ThePieceOnH4() = runComposeUiTest {
        val b = bundled(5)
        val built = build5()
        // the preset only looks at A (all '?', so it projects the whole A board); "the piece on h4 at
        // ply 2" (square@2:h4) has no UI
        assertEquals(listOf("board@0"), built.project)
        assertEquals(b, built.withMetaOf(b).copy(project = b.project))
    }
}
