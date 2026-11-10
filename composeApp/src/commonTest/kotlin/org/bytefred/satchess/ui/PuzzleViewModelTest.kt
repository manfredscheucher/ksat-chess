package org.bytefred.satchess.ui

import androidx.compose.ui.input.key.Key
import org.bytefred.ksat.facade.Solver
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.Goal
import org.bytefred.satchess.puzzle.MovePattern
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.Start
import org.bytefred.satchess.sat.Solution
import org.bytefred.satchess.sat.SolveEvent
import org.bytefred.satchess.sat.SolveOptions
import org.bytefred.satchess.ui.model.INITIAL_BOARD
import org.bytefred.satchess.ui.model.OPEN_BOARD
import org.bytefred.satchess.ui.model.OPTIONS_TOOL
import org.bytefred.satchess.ui.solver.SolverService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A solver service that records requests and lets the test push events. */
class FakeSolverService : SolverService {
    var lastPuzzle: Puzzle? = null
    var lastOptions: SolveOptions? = null
    var cancelled = 0
    private var listener: ((SolveEvent) -> Unit)? = null

    override fun solve(puzzle: Puzzle, options: SolveOptions, onEvent: (SolveEvent) -> Unit) {
        lastPuzzle = puzzle
        lastOptions = options
        listener = onEvent
    }

    override fun cancel() {
        cancelled++
        listener = null
    }

    fun emit(event: SolveEvent) = listener?.invoke(event)
}

class PuzzleViewModelTest {
    private val solver = FakeSolverService()
    private val vm = PuzzleViewModel(solver)

    @Test
    fun startsWithTheFirstExampleOrASharedPuzzle() {
        val example = Example("Demo", Puzzle(title = "first", plies = 2))
        vm.start(null, listOf(example))
        assertEquals("first", vm.puzzle.title)
        val other = PuzzleViewModel(FakeSolverService())
        other.start("""{"title": "shared", "plies": 3}""", listOf(example))
        assertEquals("shared", other.puzzle.title)
    }

    @Test
    fun invalidImportKeepsThePuzzleAndShowsAMessage() {
        val before = vm.puzzle
        assertFalse(vm.importJson("{ not json"))
        assertEquals(before, vm.puzzle)
        assertNotNull(vm.message)
    }

    @Test
    fun boardEditingGoesThroughTheSelectedPly() {
        vm.selectPly(vm.puzzle.plies)
        assertEquals(OPEN_BOARD, vm.board)
        assertEquals(OPTIONS_TOOL, vm.paletteSelection) // the default pick
        vm.selectPalette(OPTIONS_TOOL) // the picked one again: deselects
        assertNull(vm.paletteSelection)
        vm.clickSquare(60) // nothing picked: nothing happens
        assertEquals(OPEN_BOARD, vm.board)
        vm.selectPalette('K')
        vm.clickSquare(60)
        assertEquals('K', vm.board[60])
        assertEquals("????????/????????/????????/????????/????????/????????/????????/????K???", vm.fenText)
        vm.dragPiece(60, 61)
        assertEquals('K', vm.board[61])
        vm.dragOff(61)
        assertEquals('.', vm.board[61])
    }

    @Test
    fun invalidFenShowsAnInlineErrorAndKeepsTheBoard() {
        vm.setFenInput("not a fen")
        assertNotNull(vm.fenError)
        assertEquals("not a fen", vm.fenText)
        assertEquals(INITIAL_BOARD, vm.board)
        assertFalse(vm.canSolve)
        vm.setFenInput("8/8/8/8/8/8/8/4K3")
        assertNull(vm.fenError)
        assertEquals('K', vm.board[60])
    }

    @Test
    fun selectingAPlyShowsTheMoveEditorUntilABoardIsSelected() {
        vm.selectMove(2)
        assertEquals(2, vm.selectedMove)
        vm.setMovePattern(2, MovePattern(san = "Kf2"))
        assertEquals("Kf2", vm.puzzle.moves["2"]?.san)
        vm.setPliesInput("2") // the ply no longer exists: back to the board
        assertNull(vm.selectedMove)
        vm.selectMove(1)
        vm.selectPly(0)
        assertNull(vm.selectedMove)
        vm.setPliesInput("0")
        vm.selectMove(0) // no plies, no move to edit
        assertNull(vm.selectedMove)
    }

    @Test
    fun pliesInputFollowsBAndRejectsGarbage() {
        vm.selectPly(vm.puzzle.plies)
        assertEquals("4", vm.pliesText)
        vm.setPliesInput("7")
        assertEquals(7, vm.puzzle.plies)
        assertEquals(7, vm.selectedPly)
        vm.setPliesInput("x")
        assertTrue(vm.pliesError)
        assertEquals(7, vm.puzzle.plies)
        assertFalse(vm.canSolve)
    }

    @Test
    fun typingNThroughSmallValuesDoesNotDropConstraints() {
        vm.load(Puzzle(plies = 13, positions = mapOf("5" to "8/8/8/8/8/8/8/4K3", "13" to "8/8/8/8/8/8/8/3K4")))
        vm.setPliesInput("1")
        assertEquals(emptyMap(), vm.puzzle.positions.filterKeys { it == "5" })
        vm.setPliesInput("14")
        assertEquals(setOf("5", "14"), vm.puzzle.positions.keys)
    }

    @Test
    fun importRejectsPuzzlesThatWouldBreakTheEditor() {
        assertFalse(vm.importJson("""{"plies": -1}"""))
        assertFalse(vm.importJson("""{"plies": 2, "positions": {"2": "not a board"}}"""))
        assertEquals(PuzzleViewModel.NEW_PUZZLE, vm.puzzle)
        assertNotNull(vm.message)
    }

    @Test
    fun solveCollectsEventsAndCancelStopsTheRun() {
        vm.solve()
        assertTrue(vm.run.running)
        assertEquals(0, solver.lastOptions?.maxSolutions) // always every solution; Cancel stops
        assertEquals(Solver.KISSAT, solver.lastOptions?.solver)
        solver.emit(SolveEvent.Encoded(100, 2000, 5))
        solver.emit(SolveEvent.Found(0, Solution(listOf("a", "b"), emptyList(), "key", true), 10))
        assertTrue(vm.run.encoded)
        assertEquals(1, vm.solutions.size)
        solver.emit(SolveEvent.Done(1, complete = true, millis = 20))
        assertFalse(vm.run.running)
        assertNotNull(vm.run.done)

        vm.solve()
        vm.cancel()
        assertTrue(vm.run.cancelled)
        assertFalse(vm.run.running)
    }

    @Test
    fun replayIsClampedToTheSolution() {
        vm.solve()
        solver.emit(SolveEvent.Found(0, Solution(listOf("x", "y", "z"), listOf(), "k", true), 1))
        vm.setReplay(10)
        assertEquals(0, vm.replayPly)
        vm.stepReplay(-1)
        assertEquals(0, vm.replayPly)
    }

    @Test
    fun solutionsAreAppendedInPlace() {
        vm.solve()
        val line = Solution(listOf("a", "b"), emptyList(), "key", true)
        repeat(50_000) { solver.emit(SolveEvent.Found(it, line, 0)) }
        assertEquals(50_000, vm.solutions.size)
        vm.groupSimilar = true
        assertEquals(1, vm.similarGroups().size)
        solver.emit(SolveEvent.Done(50_000, complete = false, millis = 1))
        vm.solve()
        assertEquals(0, vm.solutions.size)
        assertEquals(emptyList(), vm.similarGroups())
    }

    @Test
    fun aFailedRunStopsRunning() {
        vm.solve()
        solver.emit(SolveEvent.Failed("java.lang.OutOfMemoryError: Java heap space"))
        assertFalse(vm.run.running)
        assertNotNull(vm.run.error)
    }

    @Test
    fun replayKeysStepTheShownSolution() {
        assertFalse(vm.replayKey(Key.DirectionRight))
        vm.solve()
        solver.emit(SolveEvent.Found(0, sampleSolution("e4", "e5"), 1))
        assertTrue(vm.replayKey(Key.DirectionRight))
        assertEquals(1, vm.replayPly)
        assertTrue(vm.replayKey(Key.MoveEnd))
        assertEquals(2, vm.replayPly)
        assertFalse(vm.replayKey(Key.A))
    }

    @Test
    fun changingPliesRemapsProjectionItemsOfB() {
        vm.load(Puzzle(plies = 4, positions = mapOf("4" to "8/8/8/8/8/8/8/4K2k"), project = listOf("board@4", "square@2:e4", "piece@0:K")))
        vm.setPliesInput("6")
        assertEquals(listOf("board@6", "square@2:e4", "piece@0:K"), vm.puzzle.project)
        vm.setPliesInput("1")
        assertEquals(listOf("board@1", "piece@0:K"), vm.puzzle.project)
        vm.setPliesInput("0")
        assertEquals(listOf("piece@0:K"), vm.puzzle.project)
    }

    @Test
    fun squareOptionsFromThePaletteToolAndForUnknownSquares() {
        vm.load(Puzzle(plies = 0, start = Start(board = "8/8/8/8/8/8/8/4K3")))
        vm.paletteSelection = OPTIONS_TOOL
        vm.clickSquare(0)
        assertEquals(OptionsTarget.Square(0), vm.optionsTarget)
        assertEquals('.', vm.board[0])
        vm.applyOptions(setOf('k', '.'))
        assertNull(vm.optionsTarget)
        assertEquals('?', vm.board[0])
        assertEquals(setOf('k', '.'), vm.boardOptions[0])
        assertEquals("?7/8/8/8/8/8/8/4K3", vm.fenText)

        vm.openOptions(OptionsTarget.UnknownSquares)
        vm.applyOptions(setOf('K', '.'))
        assertEquals(mapOf("a8" to "k.", "?" to "K."), vm.puzzle.squares["0"])

        // Dragging a piece onto an option square fixes it.
        vm.dragPiece(Sq.parse("e1")!!, 0)
        assertEquals('K', vm.board[0])
        assertEquals(mapOf("?" to "K."), vm.puzzle.squares["0"])
    }

    @Test
    fun staticColumnsAreKeptUntilAnotherPuzzleOrOtherPlies() {
        vm.load(Puzzle(plies = 4))
        assertNull(vm.columnsOverride)
        vm.setStaticColumnsInput("5", boards = 5)
        assertEquals(5, vm.columnsOverride)
        vm.setStaticColumnsInput("0", boards = 5) // invalid: the last valid value stays
        assertEquals(5, vm.columnsOverride)
        assertTrue(vm.staticColumnsError(5))
        vm.setStaticColumnsInput("2", boards = 5)
        vm.setGoal(Goal.CHECK) // other edits keep it
        assertEquals(2, vm.columnsOverride)
        vm.setPliesInput("6")
        assertNull(vm.columnsOverride)
        assertNull(vm.staticColumnsText)

        vm.setStaticColumnsInput("3", boards = 7)
        vm.load(Puzzle(plies = 6))
        assertNull(vm.columnsOverride)
    }

    @Test
    fun importsAreValidatedWithTheCoreRules() {
        assertFalse(vm.importJson("""{"plies": 2, "project": ["nonsense"]}"""))
        assertFalse(vm.importJson("""{"plies": 2, "start": {"castling": "XY"}}"""))
        assertFalse(vm.importJson("""{"plies": 2, "start": {"sideToMove": "w", "enPassant": "e3"}}"""))
        assertTrue(vm.importJson("""{"plies": 2, "squares": {"2": {"?": "Kk."}}}"""))
    }

    @Test
    fun undoAndRedoFollowTheEdits() {
        val original = vm.puzzle
        vm.clearBoard()
        val cleared = vm.puzzle
        assertTrue(vm.canUndo)
        vm.undo()
        assertEquals(original, vm.puzzle)
        assertTrue(vm.canRedo)
        vm.redo()
        assertEquals(cleared, vm.puzzle)
        vm.setPliesInput("5")
        vm.setPliesInput("6")
        assertEquals(6, vm.puzzle.plies)
        vm.undo() // the keystrokes of N are one step
        assertEquals(cleared, vm.puzzle)
        vm.load(Puzzle(plies = 2))
        assertFalse(vm.canUndo || vm.canRedo)
    }
}
