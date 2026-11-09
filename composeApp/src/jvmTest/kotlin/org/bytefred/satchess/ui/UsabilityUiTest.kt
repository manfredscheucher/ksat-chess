package org.bytefred.satchess.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.bytefred.satchess.sat.SolveEvent
import org.bytefred.satchess.ui.platform.loadPref
import org.bytefred.satchess.ui.platform.savePref
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** One test per usability rule taken over from ktoy. */
@OptIn(ExperimentalTestApi::class)
class UsabilityUiTest {
    private fun resetPrefs() {
        savePref(WELCOME_PREF, "off")
        savePref(COORDINATES_PREF, "true")
        savePref(LAYOUT_PREF, LayoutMode.AUTO.name)
        savePref(SOLUTION_VIEW_PREF, SolutionView.ANIMATED.name)
    }

    @BeforeTest fun before() = resetPrefs()
    @AfterTest fun after() = resetPrefs()

    @Test
    fun oneClickSwitchesFromOneMenuToAnotherAndTheOpenTitleCloses() = runComposeUiTest {
        setContent { App(PuzzleViewModel(FakeSolverService())) }
        onNodeWithTag("menu-File").performClick()
        onNodeWithText("Load…").assertIsDisplayed()
        onNodeWithTag("menu-View").performClick()
        onNodeWithText("Coordinates", substring = true).assertIsDisplayed()
        onNodeWithText("Load…").assertDoesNotExist()
        onNodeWithTag("menu-View").performClick()
        onNodeWithText("Coordinates", substring = true).assertDoesNotExist()
    }

    @Test
    fun escapeClosesAnOpenMenu() = runComposeUiTest {
        setContent { App(PuzzleViewModel(FakeSolverService())) }
        onNodeWithTag("menu-File").performClick()
        onNodeWithText("Load…").assertIsDisplayed()
        onNodeWithTag("menu-bar").assertIsFocused()
        onNodeWithTag("menu-bar").performKeyInput { pressKey(Key.Escape) }
        onNodeWithText("Load…").assertDoesNotExist()
    }

    @Test
    fun viewMenuChoicesSurviveAReload() = runComposeUiTest {
        val vm = PuzzleViewModel(FakeSolverService())
        setContent { App(vm) }
        onNodeWithTag("menu-View").performClick()
        onNodeWithText("portrait", substring = true).performClick()
        onNodeWithTag("menu-View").performClick()
        onNodeWithText("Coordinates", substring = true).performClick()
        waitForIdle()
        val reloaded = PuzzleViewModel(FakeSolverService())
        assertEquals(LayoutMode.PORTRAIT, reloaded.layoutMode)
        assertFalse(reloaded.showCoordinates)
    }

    @Test
    fun welcomeShowsUntilDontShowAgainIsTicked() = runComposeUiTest {
        savePref(WELCOME_PREF, "on")
        setContent { App(PuzzleViewModel(FakeSolverService())) }
        onNodeWithText("Feedback is very welcome", substring = true).assertIsDisplayed()
        onNodeWithTag("dontShowAgain").performClick()
        onNodeWithTag("welcomeStart").performClick()
        onNodeWithText("Feedback is very welcome", substring = true).assertDoesNotExist()
        assertEquals("off", loadPref(WELCOME_PREF))
    }

    @Test
    fun examplesDialogSelectsFirstAndReopensAsItWasLeft() = runComposeUiTest {
        val vm = PuzzleViewModel(FakeSolverService())
        setContent { App(vm) }
        waitForIdle()
        val before = vm.puzzle.title
        onNodeWithTag("menu-File").performClick()
        onNodeWithText("Examples…").performClick()
        onNodeWithTag("loadExample").assertIsNotEnabled()
        onNodeWithText("Demos").performClick()
        onNodeWithText("En passant").performClick()
        // a click only selects
        runOnIdle { assertEquals(before, vm.puzzle.title) }
        onNodeWithTag("loadExample").assertIsEnabled()
        onNodeWithText("Cancel").performClick()

        onNodeWithTag("menu-File").performClick()
        onNodeWithText("Examples…").performClick()
        onNodeWithText("En passant").assertIsDisplayed()
        onNodeWithTag("loadExample").assertIsEnabled().performClick()
        runOnIdle { assertEquals("En passant", vm.puzzle.title) }
    }

    @Test
    fun loadedPuzzleLinksToItsSource() = runComposeUiTest {
        val vm = PuzzleViewModel(FakeSolverService())
        val opened = mutableListOf<String>()
        val uriHandler = object : UriHandler { override fun openUri(uri: String) { opened += uri } }
        setContent { CompositionLocalProvider(LocalUriHandler provides uriHandler) { App(vm) } }
        val puzzle = loadExamples().first { it.puzzle.id == "chess-com-5/1" }.puzzle
        runOnIdle { vm.load(puzzle) }
        onNodeWithTag("puzzleSource").assertTextContains("Source", substring = true)
        // the link is a span inside the credit text, not a node of its own: fire its listener
        val text = onNodeWithTag("puzzleSource").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val link = text.getLinkAnnotations(0, text.length).single().item as LinkAnnotation.Clickable
        runOnIdle { link.linkInteractionListener!!.onClick(link) }
        runOnIdle { assertEquals(listOf(puzzle.source?.url), opened) }

        // demos have no source: no credit line at all
        runOnIdle { vm.load(loadExamples().first { it.puzzle.id == "demos/fools-mate" }.puzzle) }
        onNodeWithTag("puzzleSource").assertDoesNotExist()
    }

    @Test
    fun editMenuUndoesTheLastChange() = runComposeUiTest {
        val vm = PuzzleViewModel(FakeSolverService())
        setContent { App(vm) }
        val before = vm.puzzle
        runOnIdle { vm.clearBoard() }
        onNodeWithTag("menu-Edit").performClick()
        onNodeWithText("Undo").performClick()
        runOnIdle { assertEquals(before, vm.puzzle) }
    }

    @Test
    fun askBeforeReplacingAnEditedPuzzle() = runComposeUiTest {
        val vm = PuzzleViewModel(FakeSolverService())
        setContent { App(vm) }
        waitForIdle()

        // no edit: New just happens, also after a solver run
        vm.solve()
        onNodeWithTag("menu-File").performClick()
        onNodeWithText("New").performClick()
        onNodeWithText("Save changes?").assertDoesNotExist()
        runOnIdle { assertEquals(PuzzleViewModel.NEW_PUZZLE, vm.puzzle) }

        // an edit asks; Cancel keeps it, Discard goes on
        runOnIdle { vm.clearBoard() }
        val edited = vm.puzzle
        onNodeWithTag("menu-File").performClick()
        onNodeWithText("New").performClick()
        onNodeWithText("Save changes?").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        runOnIdle { assertEquals(edited, vm.puzzle) }
        assertNull(vm.pendingLeave)

        onNodeWithTag("menu-File").performClick()
        onNodeWithText("New").performClick()
        onNodeWithText("Discard").performClick()
        runOnIdle { assertEquals(PuzzleViewModel.NEW_PUZZLE, vm.puzzle) }
    }

    @Test
    fun savingEndsTheQuestionAndCancellingKeepsThePuzzle() {
        val vm = PuzzleViewModel(FakeSolverService())
        vm.clearBoard()
        assertTrue(vm.unsavedEdits)
        var left = false
        vm.leave { left = true }
        vm.fileSaved(null)
        assertFalse(left)
        assertNull(vm.pendingLeave)
        assertTrue(vm.unsavedEdits)

        vm.leave { left = true }
        vm.fileSaved("x.json")
        assertTrue(left)
        assertFalse(vm.unsavedEdits)
    }

    @Test
    fun portraitHasNoTabsAndShowsResultsBelowThePuzzle() = runComposeUiTest {
        val solver = FakeSolverService()
        val vm = PuzzleViewModel(solver)
        vm.layoutMode = LayoutMode.PORTRAIT
        setContent { App(vm) }
        onNodeWithText("Puzzle").assertDoesNotExist()
        onNodeWithTag("solve").performClick()
        runOnIdle {
            solver.emit(SolveEvent.Found(0, sampleSolution("f3", "e5", "g4", "Qh4#"), 40))
            solver.emit(SolveEvent.Done(1, complete = true, millis = 50))
        }
        // Solve scrolled down to the results by itself; the editor is still in the same column
        onNodeWithTag("countSummary").assertIsDisplayed()
        onNodeWithTag("editorBoard").assertExists()
    }

    @Test
    fun replayControlsResetBackStepRun() = runComposeUiTest {
        val solver = FakeSolverService()
        val vm = PuzzleViewModel(solver)
        setContent { App(vm) }
        onNodeWithTag("solve").performScrollTo().performClick()
        runOnIdle {
            solver.emit(SolveEvent.Found(0, sampleSolution("f3", "e5", "g4", "Qh4#"), 40))
            solver.emit(SolveEvent.Done(1, complete = true, millis = 50))
        }
        onNodeWithTag("replayReset").performScrollTo().assertIsNotEnabled()
        onNodeWithTag("replayStep").performClick()
        onNodeWithTag("replayStep").performClick()
        runOnIdle { assertEquals(2, vm.replayPly) }
        onNodeWithTag("replayBack").performClick()
        runOnIdle { assertEquals(1, vm.replayPly) }
        onNodeWithTag("replayReset").performClick()
        runOnIdle { assertEquals(0, vm.replayPly) }
        mainClock.autoAdvance = false
        onNodeWithTag("replayRun").performClick()
        mainClock.advanceTimeBy(4500)
        mainClock.autoAdvance = true
        runOnIdle { assertEquals(4, vm.replayPly) }
    }

    @Test
    fun staticViewShowsEveryBoardOfTheSolution() = runComposeUiTest {
        val solver = FakeSolverService()
        val vm = PuzzleViewModel(solver)
        setContent { App(vm) }
        onNodeWithTag("solve").performScrollTo().performClick()
        runOnIdle {
            solver.emit(SolveEvent.Found(0, sampleSolution("f3", "e5", "g4", "Qh4#"), 40))
            solver.emit(SolveEvent.Done(1, complete = true, millis = 50))
        }
        onNodeWithTag("replayBoard").performScrollTo().assertIsDisplayed()
        onNodeWithTag("solutionViewToggle").performScrollTo().performClick()
        onAllNodesWithTag("staticBoard").assertCountEquals(5) // 4 plies: boards A, 1, 2, 3, B
        onNodeWithTag("replayBoard").assertDoesNotExist()
        runOnIdle { assertEquals(SolutionView.STATIC.name, loadPref(SOLUTION_VIEW_PREF)) }
        onNodeWithTag("solutionViewToggle").performScrollTo().performClick()
        onNodeWithTag("replayBoard").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aLongColumnGetsAScrollbar() = runComposeUiTest {
        val vm = PuzzleViewModel(FakeSolverService())
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(1000.dp, 300.dp)) { App(vm) }
            }
        }
        assertTrue(onAllNodesWithTag("vscroll-thumb", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }
}
