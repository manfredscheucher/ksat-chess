package org.bytefred.satchess.ui

import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.Question
import org.bytefred.satchess.sat.SolveEvent
import org.bytefred.satchess.sat.SolveOptions
import org.bytefred.satchess.sat.Solution
import org.bytefred.satchess.ui.model.INITIAL_BOARD
import org.bytefred.satchess.ui.model.OPEN_BOARD
import org.bytefred.satchess.ui.model.PliesUnit
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
        vm.paletteSelection = 'K'
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
    fun pliesInputInMovesFollowsBAndRejectsGarbage() {
        vm.selectPly(vm.puzzle.plies)
        vm.changePliesUnit(PliesUnit.MOVES)
        assertEquals("2", vm.pliesText)
        vm.setPliesInput("3.5")
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
    fun presetUpdatesTheProjectionField() {
        vm.setQuestion(Question.HELPMATE)
        assertEquals("moves", vm.projectionText)
        assertEquals("b", vm.puzzle.start.sideToMove)
    }

    @Test
    fun solveCollectsEventsAndCancelStopsTheRun() {
        vm.limit = SolutionLimit.N
        vm.limitN = 5
        vm.solve()
        assertTrue(vm.run.running)
        assertEquals(5, solver.lastOptions?.maxSolutions)
        solver.emit(SolveEvent.Encoded(100, 2000, 5))
        solver.emit(SolveEvent.Found(0, Solution(listOf("a", "b"), emptyList(), "key", true), 10))
        assertEquals(100, vm.run.variables)
        assertEquals(1, vm.run.solutions.size)
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
}
