package org.bytefred.satchess.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.Start
import org.bytefred.satchess.sat.SolveEvent
import org.bytefred.satchess.ui.platform.savePref
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Drives the real App: load an example, edit a board, break the FEN, solve with a fake solver. */
@OptIn(ExperimentalTestApi::class)
class AppSmokeTest {
    @BeforeTest
    fun noWelcome() = savePref(WELCOME_PREF, "off")

    @Test
    fun loadExampleEditBoardAndShowResults() = runComposeUiTest {
        val solver = FakeSolverService()
        val vm = PuzzleViewModel(solver)
        setContent { App(vm) }

        onNodeWithTag("menu-File").performClick()
        onNodeWithText("Examples…").performClick()
        onNodeWithText("Demos").performClick()
        onNodeWithText("Black moves first").performClick()
        onNodeWithTag("loadExample").performClick()
        onNodeWithTag("puzzleTitle").assertTextContains("Black moves first")

        // Select ply 2 (open), place a white king on e1 via the palette.
        onNodeWithTag("ply-2").performClick()
        onNodeWithTag("palette-K").performScrollTo().performClick()
        onNodeWithTag("editorBoard").performTouchInput {
            val square = width / 8f
            click(Offset(4.5f * square, 7.5f * square))
        }
        waitForIdle()
        assertEquals('K', vm.board[60])
        assertTrue(vm.fenText.endsWith("????K???"))

        // Invalid FEN: inline error, no crash, Solve disabled.
        onNodeWithTag("fen").performTextReplacement("this is not a fen")
        waitForIdle()
        assertTrue(vm.fenError != null)
        onNodeWithTag("fen").performTextReplacement("8/8/8/8/8/8/8/4K3")

        onNodeWithTag("solve").performScrollTo().performClick()
        runOnIdle {
            solver.emit(SolveEvent.Encoded(1234, 56789, 12))
            solver.emit(SolveEvent.Found(0, sampleSolution("f3", "e5", "g4", "Qh4#"), 40))
            solver.emit(SolveEvent.Done(1, complete = true, millis = 50))
        }
        onNodeWithTag("countSummary").performScrollTo().assertTextContains("unique", substring = true)
        onNodeWithTag("replayBoard").performScrollTo().assertIsDisplayed()
        onNodeWithText("Qh4#").performScrollTo().performClick()
        runOnIdle { assertEquals(4, vm.replayPly) }
    }

    @Test
    fun squareOptionsDialogAndAbout() = runComposeUiTest {
        val vm = PuzzleViewModel(FakeSolverService())
        setContent { App(vm) }
        runOnIdle { vm.load(Puzzle(plies = 0, start = Start(board = "8/8/8/8/8/8/8/4K3"))) }

        // Long press on the empty a8 opens its options ("empty" checked); add "k", apply.
        onNodeWithTag("editorBoard").performScrollTo().performTouchInput { longClick(Offset(width / 16f, height / 16f)) }
        onNodeWithTag("option-k").performScrollTo().performClick()
        onNodeWithTag("applyOptions").performClick()
        runOnIdle {
            assertEquals('?', vm.board[0])
            assertEquals(setOf('k', '.'), vm.boardOptions[0])
        }

        // Default for every '?' square, via the button below the board.
        onNodeWithTag("unknownSquaresOptions").performScrollTo().performClick()
        onNodeWithText("Kings or empty").performClick()
        onNodeWithTag("applyOptions").performClick()
        runOnIdle { assertEquals("Kk.", vm.puzzle.squares["0"]?.get("?")) }

        // Dialog input mode: a click opens the square dialog, which leads to the options.
        runOnIdle { vm.inputMode = InputMode.DIALOG }
        onNodeWithTag("editorBoard").performScrollTo().performTouchInput { click(Offset(width * 15 / 16f, height / 16f)) }
        onNodeWithTag("dialogOptions").performClick()
        onNodeWithTag("applyOptions").assertIsDisplayed()
        runOnIdle { assertEquals(OptionsTarget.Square(7), vm.optionsTarget) }
        onNodeWithText("Cancel").performClick()

        onNodeWithTag("menu-Help").performClick()
        onNodeWithText("About ksat-chess").performClick()
        onNodeWithTag("about").assertIsDisplayed()
        onNodeWithText("CaDiCaL (MIT)").assertDoesNotExist()
        onNodeWithText("License texts").performScrollTo().performClick()
        onNodeWithText("CaDiCaL (MIT)").performScrollTo().assertIsDisplayed()
        onNodeWithText("Chess pieces: cburnett (BSD 3-clause)").performScrollTo().assertIsDisplayed()
    }
}

