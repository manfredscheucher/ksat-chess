package org.bytefred.satchess.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.lifecycle.ViewModel
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.bytefred.ksat.facade.Solver
import org.bytefred.satchess.puzzle.Goal
import org.bytefred.satchess.puzzle.MovePattern
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.PuzzleJson
import org.bytefred.satchess.puzzle.Question
import org.bytefred.satchess.puzzle.RuleFlags
import org.bytefred.satchess.sat.Solution
import org.bytefred.satchess.sat.SolveEvent
import org.bytefred.satchess.sat.SolveOptions
import org.bytefred.satchess.ui.model.CLEAR_BOARD
import org.bytefred.satchess.ui.model.INITIAL_BOARD
import org.bytefred.satchess.ui.model.OPEN_BOARD
import org.bytefred.satchess.ui.model.OPTIONS_TOOL
import org.bytefred.satchess.ui.model.PliesUnit
import org.bytefred.satchess.ui.model.SimilarGroup
import org.bytefred.satchess.ui.model.SimilarGrouper
import org.bytefred.satchess.ui.model.applyFenText
import org.bytefred.satchess.ui.model.applyPreset
import org.bytefred.satchess.ui.model.boardAt
import org.bytefred.satchess.ui.model.enPassantError
import org.bytefred.satchess.ui.model.fenTextAt
import org.bytefred.satchess.ui.model.formatPlies
import org.bytefred.satchess.ui.model.movePiece
import org.bytefred.satchess.ui.model.optionsAt
import org.bytefred.satchess.ui.model.paletteClick
import org.bytefred.satchess.ui.model.parsePliesInput
import org.bytefred.satchess.ui.model.parseProjection
import org.bytefred.satchess.ui.model.puzzleProblem
import org.bytefred.satchess.ui.model.removePiece
import org.bytefred.satchess.ui.model.setSquare
import org.bytefred.satchess.ui.model.withBoardAt
import org.bytefred.satchess.ui.model.withCellsAt
import org.bytefred.satchess.ui.model.withDefaultOptions
import org.bytefred.satchess.ui.model.withMovePattern
import org.bytefred.satchess.ui.model.withPlies
import org.bytefred.satchess.ui.model.withSquareDomain
import org.bytefred.satchess.ui.model.withSquareOptions
import org.bytefred.satchess.ui.platform.clearShareLink
import org.bytefred.satchess.ui.platform.loadPref
import org.bytefred.satchess.ui.solver.SolverService
import org.bytefred.satchess.ui.solver.createSolverService
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Board input (View menu): the hybrid palette/drag mode, or a dialog per clicked square. */
enum class InputMode { HYBRID, DIALOG }

/** Layout (View menu): by window width, stacked, or side by side. */
enum class LayoutMode { AUTO, PORTRAIT, LANDSCAPE }

/** "Max solutions" in Advanced: 1, a number N, or all. */
enum class SolutionLimit { ONE, N, ALL }

/** Target of the square options dialog: one square of the selected ply, or the ply's '?' default. */
sealed interface OptionsTarget {
    data class Square(val sq: Int) : OptionsTarget
    data object UnknownSquares : OptionsTarget
}

/** State of the current / last solver run; its solutions are [PuzzleViewModel.solutions]. */
data class SolveRun(
    val running: Boolean = false,
    val variables: Int? = null,
    val clauses: Int? = null,
    /** Set when the run finished normally. */
    val done: SolveEvent.Done? = null,
    val error: String? = null,
    val cancelled: Boolean = false,
    val infos: List<String> = emptyList(),
    /** The puzzle as it was solved (results stay meaningful while the user edits on). */
    val puzzle: Puzzle? = null,
)

/**
 * UI state of the puzzle screen. The edited [Puzzle] itself is the state (doc/spec.md section 4),
 * so import/export/share are lossless; the editing rules are the pure functions in ui/model.
 */
class PuzzleViewModel(private val solverService: SolverService = createSolverService()) : ViewModel() {

    var puzzle: Puzzle by mutableStateOf(NEW_PUZZLE); private set

    /** The ply whose board the editor shows (0 = A, plies = B). */
    var selectedPly by mutableIntStateOf(0); private set
    var paletteSelection by mutableStateOf('P')

    var pliesUnit by mutableStateOf(PliesUnit.PLIES); private set
    var pliesText by mutableStateOf(formatPlies(NEW_PUZZLE.plies, PliesUnit.PLIES)); private set
    var pliesError by mutableStateOf(false); private set

    var fenText by mutableStateOf(NEW_PUZZLE.fenTextAt(0)); private set
    var fenError by mutableStateOf<String?>(null); private set
    var enPassantText by mutableStateOf("-"); private set
    var enPassantError by mutableStateOf<String?>(null); private set
    var projectionText by mutableStateOf(NEW_PUZZLE.project.joinToString(", ")); private set
    var projectionError by mutableStateOf(false); private set

    // View menu; MenuBar stores a change, so it is still there after a reload
    var inputMode by mutableStateOf(InputMode.entries.firstOrNull { it.name == loadPref(INPUT_MODE_PREF) } ?: InputMode.HYBRID)
    var showCoordinates by mutableStateOf(loadPref(COORDINATES_PREF) != "false")
    var layoutMode by mutableStateOf(LayoutMode.entries.firstOrNull { it.name == loadPref(LAYOUT_PREF) } ?: LayoutMode.AUTO)

    /** The shares of the side-by-side panels, per panel key. */
    var panelWeights by mutableStateOf<Map<String, Float>>(emptyMap())

    /** The open square options dialog, or null. */
    var optionsTarget by mutableStateOf<OptionsTarget?>(null); private set

    // Advanced solver options (not part of the puzzle file). Capped by default: an open B can have
    // hundreds of thousands of lines; "all" stays selectable.
    var solver by mutableStateOf(Solver.CADICAL)
    var limit by mutableStateOf(SolutionLimit.N)
    var limitN by mutableIntStateOf(DEFAULT_LIMIT)

    var run by mutableStateOf(SolveRun()); private set

    /** Solutions of the current / last run, appended in O(1) per Found event. */
    private val foundSolutions = mutableStateListOf<Solution>()
    val solutions: List<Solution> get() = foundSolutions

    /** "Group similar solutions", updated incrementally (only while the toggle is used). */
    private var similarGrouper = SimilarGrouper()

    /** Results: flat solution list (default) or "Group similar solutions" (same events, any move order). */
    var groupSimilar by mutableStateOf(false)

    private var runStart: TimeMark? = null
    /** Elapsed time of the finished run. */
    var finishedMillis by mutableStateOf(0L); private set
    var selectedSolution by mutableIntStateOf(0); private set
    var replayPly by mutableIntStateOf(0); private set

    /** A transient message for the snackbar ("Link copied", import errors, ...). */
    var message by mutableStateOf<String?>(null); private set

    val board: List<Char> get() = puzzle.boardAt(selectedPly)

    /** Allowed contents of the '?' squares of the selected ply (for drawing the options). */
    val boardOptions: Map<Int, Set<Char>> get() = puzzle.optionsAt(selectedPly)
    val canSolve: Boolean get() = !run.running && !pliesError && fenError == null && enPassantError == null && !projectionError

    // ── loading ──────────────────────────────────────────────────────────────

    fun load(p: Puzzle) {
        solverService.cancel()
        if (!openingShareLink) clearShareLink()
        update(p)
        savedPuzzle = p
        selectedPly = if (p.positions.containsKey(p.plies.toString())) p.plies else 0
        pliesText = formatPlies(p.plies, pliesUnit)
        pliesError = false
        enPassantText = p.start.enPassant
        enPassantError = null
        projectionText = p.project.joinToString(", ")
        projectionError = false
        optionsTarget = null
        run = SolveRun()
        clearSolutions()
        selectedSolution = 0
        replayPly = 0
        syncFen()
    }

    fun newPuzzle() = load(NEW_PUZZLE)

    private var started = false

    /** First screen: a puzzle shared via link wins, else the first bundled example, else "New". */
    fun start(sharedJson: String?, examples: List<Example>) {
        if (started) return
        started = true
        if (sharedJson != null && openShareLink(sharedJson)) return
        examples.firstOrNull()?.let { load(it.puzzle) }
    }

    private var openingShareLink = false

    /** Opens a puzzle from a share link; the link stays in the URL until another puzzle is opened. */
    fun openShareLink(json: String): Boolean {
        openingShareLink = true
        try {
            return importJson(json)
        } finally {
            openingShareLink = false
        }
    }

    /** Imports puzzle JSON; returns false (and shows a message) on invalid input. */
    fun importJson(text: String): Boolean = try {
        val p = PuzzleJson.decode(text)
        val problem = puzzleProblem(p)
        if (problem == null) load(p) else showMessage("Not a valid puzzle file: $problem")
        problem == null
    } catch (e: SerializationException) {
        showMessage("Not a valid puzzle file: ${e.message}")
        false
    } catch (e: IllegalArgumentException) {
        showMessage("Not a valid puzzle file: ${e.message}")
        false
    }

    fun exportJson(): String = PuzzleJson.encode(puzzle)

    // ── unsaved edits ───────────────────────────────────────────────────────
    // Derived from what the last load or save held. A solver run or looking through boards is no edit.

    private var savedPuzzle: Puzzle = NEW_PUZZLE
    val unsavedEdits: Boolean get() = puzzle != savedPuzzle

    /** What New / Load / Examples wants to do once "Save changes?" is answered; the dialog shows while it is set. */
    var pendingLeave by mutableStateOf<(() -> Unit)?>(null); private set

    /** Run [action] (which replaces the puzzle), asking first if there are unsaved edits. */
    fun leave(action: () -> Unit) {
        if (unsavedEdits) pendingLeave = action else action()
    }

    fun discardAndLeave() {
        val action = pendingLeave
        pendingLeave = null
        action?.invoke()
    }

    fun cancelLeave() {
        pendingLeave = null
    }

    /** The save finished: [fileName] is null if it was cancelled, which also cancels a pending leave. */
    fun fileSaved(fileName: String?) {
        if (fileName == null) {
            cancelLeave()
            return
        }
        savedPuzzle = puzzle
        showMessage("Saved $fileName")
        discardAndLeave()
    }

    /** Compact JSON for the share link (no pretty-printing whitespace in the URL). */
    fun shareJson(): String = compactJson.encodeToString(Puzzle.serializer(), puzzle)

    /** A file name for Export, from the puzzle id or title. */
    fun suggestedFileName(): String =
        (puzzle.id.substringAfterLast('/').ifEmpty { puzzle.title }).ifEmpty { "puzzle" }
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")

    fun showMessage(text: String?) {
        message = text
    }

    // ── question, plies, goal ───────────────────────────────────────────────

    fun setQuestion(question: Question) {
        update(applyPreset(puzzle, question))
        enPassantText = puzzle.start.enPassant
        enPassantError = null
        projectionText = puzzle.project.joinToString(", ")
        projectionError = false
        selectedPly = selectedPly.coerceAtMost(puzzle.plies)
        syncFen()
    }

    /**
     * While N is being typed, every keystroke re-derives the window from the puzzle as it was before the
     * first keystroke, so intermediate values ("1" on the way to "14") never drop constraints for good.
     */
    fun setPliesInput(text: String) {
        pliesText = text
        val plies = parsePliesInput(text, pliesUnit)
        pliesError = plies == null
        if (plies == null) return
        val base = pliesEditBase ?: (puzzle to selectedPly).also { pliesEditBase = it }
        val (basePuzzle, baseSelected) = base
        puzzle = basePuzzle.withPlies(plies)
        selectedPly = if (baseSelected == basePuzzle.plies) plies else baseSelected.coerceAtMost(plies)
        projectionText = puzzle.project.joinToString(", ")
        projectionError = false
        syncFen()
    }

    /** The puzzle and selected ply before the current run of N keystrokes; any other edit ends it. */
    private var pliesEditBase: Pair<Puzzle, Int>? = null

    fun changePliesUnit(unit: PliesUnit) {
        pliesUnit = unit
        pliesText = formatPlies(puzzle.plies, unit)
        pliesError = false
    }

    fun setGoal(goal: Goal) = update(puzzle.copy(goal = goal))
    fun setShortest(on: Boolean) = update(puzzle.copy(shortest = on))
    fun setRules(rules: RuleFlags) = update(puzzle.copy(rules = rules))

    // ── start parameters ─────────────────────────────────────────────────────

    fun setSideToMove(side: String) {
        update(puzzle.copy(start = puzzle.start.copy(sideToMove = side)))
        checkEnPassant()
    }

    fun setCastling(castling: String) = update(puzzle.copy(start = puzzle.start.copy(castling = castling)))

    fun setEnPassantInput(text: String) {
        enPassantText = text
        checkEnPassant()
    }

    private fun checkEnPassant() {
        val ep = enPassantText.trim().ifEmpty { "-" }
        enPassantError = enPassantError(ep, puzzle.start.sideToMove)
        if (enPassantError == null) update(puzzle.copy(start = puzzle.start.copy(enPassant = ep)))
    }

    // ── board editing ────────────────────────────────────────────────────────

    fun selectPly(t: Int) {
        pliesEditBase = null
        selectedPly = t.coerceIn(0, puzzle.plies)
        syncFen()
    }

    /** Hybrid input: place the palette selection, or with the options tool open the square's options. */
    fun clickSquare(sq: Int) {
        if (paletteSelection == OPTIONS_TOOL) openOptions(OptionsTarget.Square(sq)) else editBoard(paletteClick(board, sq, paletteSelection))
    }

    fun dragPiece(from: Int, to: Int) = editBoard(movePiece(board, from, to))
    fun dragOff(from: Int) = editBoard(removePiece(board, from))
    fun setSquareTo(sq: Int, value: Char) = editBoard(setSquare(board, sq, value))
    fun setInitialBoard() = editBoard(INITIAL_BOARD)
    fun clearBoard() = editBoard(CLEAR_BOARD)
    fun openBoard() = editBoard(OPEN_BOARD)

    private fun editBoard(cells: List<Char>) {
        update(puzzle.withCellsAt(selectedPly, cells))
        syncFen()
    }

    // ── square options ───────────────────────────────────────────────────────

    fun openOptions(target: OptionsTarget) {
        optionsTarget = target
    }

    fun closeOptions() {
        optionsTarget = null
    }

    /** Applies the options dialog: [allowed] for its square, or as the default of the '?' squares. */
    fun applyOptions(allowed: Set<Char>) {
        val target = optionsTarget ?: return
        optionsTarget = null
        if (allowed.isEmpty()) return
        update(
            when (target) {
                is OptionsTarget.Square -> puzzle.withSquareOptions(selectedPly, target.sq, allowed)
                OptionsTarget.UnknownSquares -> puzzle.withDefaultOptions(selectedPly, allowed)
            },
        )
        syncFen()
    }

    /** Two-way FEN field: valid text updates the board, invalid text shows an inline error. */
    fun setFenInput(text: String) {
        fenText = text
        val edit = applyFenText(puzzle, selectedPly, text)
        fenError = edit.error
        edit.puzzle?.let { p ->
            update(p)
            enPassantText = p.start.enPassant
            enPassantError = null
        }
    }

    private fun syncFen() {
        fenText = puzzle.fenTextAt(selectedPly)
        fenError = null
    }

    // ── advanced: move patterns, square domains, projection ───────────────────

    fun setMovePattern(t: Int, pattern: MovePattern?) = update(puzzle.withMovePattern(t, pattern))
    fun setSquareDomain(t: Int, square: String, allowed: String) = update(puzzle.withSquareDomain(t, square, allowed))

    fun setProjectionInput(text: String) {
        projectionText = text
        val items = parseProjection(text)
        projectionError = items == null
        if (items != null) update(puzzle.copy(project = items))
    }

    private fun update(p: Puzzle) {
        puzzle = p
        pliesEditBase = null
    }

    // ── solving ──────────────────────────────────────────────────────────────

    fun solve() {
        if (!canSolve) return
        val solving = puzzle
        val maxSolutions = when (limit) {
            SolutionLimit.ONE -> 1
            SolutionLimit.N -> limitN.coerceAtLeast(1)
            SolutionLimit.ALL -> 0
        }
        run = SolveRun(running = true, puzzle = solving)
        clearSolutions()
        runStart = TimeSource.Monotonic.markNow()
        selectedSolution = 0
        replayPly = 0
        solverService.solve(solving, SolveOptions(solver, maxSolutions), ::onSolveEvent)
    }

    fun cancel() {
        solverService.cancel()
        if (run.running) {
            run = run.copy(running = false, cancelled = true)
            stopClock()
        }
    }

    /** Milliseconds since the current/last run started (for the live status line). */
    fun elapsedMillis(): Long = runStart?.elapsedNow()?.inWholeMilliseconds ?: 0

    private fun onSolveEvent(event: SolveEvent) {
        when (event) {
            is SolveEvent.Encoded -> run = run.copy(variables = event.variables, clauses = event.clauses)
            is SolveEvent.Found -> foundSolutions += event.solution
            is SolveEvent.Info -> run = run.copy(infos = run.infos + event.message)
            is SolveEvent.Done -> run = run.copy(running = false, done = event)
            is SolveEvent.Failed -> run = run.copy(running = false, error = event.message)
        }
        if (!run.running) stopClock()
    }

    private fun clearSolutions() {
        foundSolutions.clear()
        similarGrouper = SimilarGrouper()
    }

    /** The "Group similar solutions" groups of [solutions]; only solutions new since the last call are keyed. */
    fun similarGroups(): List<SimilarGroup> {
        for (i in similarGrouper.size until foundSolutions.size) similarGrouper.add(foundSolutions[i])
        return similarGrouper.groups()
    }

    private fun stopClock() {
        finishedMillis = elapsedMillis()
        runStart = null
    }

    // ── replay ───────────────────────────────────────────────────────────────

    val currentSolution: Solution? get() = foundSolutions.getOrNull(selectedSolution)

    fun selectSolution(index: Int) {
        selectedSolution = index.coerceIn(0, (foundSolutions.size - 1).coerceAtLeast(0))
        replayPly = 0
    }

    fun setReplay(ply: Int) {
        val last = (currentSolution?.moves?.size ?: 0)
        replayPly = ply.coerceIn(0, last)
    }

    fun stepReplay(delta: Int) = setReplay(replayPly + delta)

    /** Arrow keys / Home / End step the replay wherever the focus is (unless a text field takes them). */
    fun replayKey(key: Key): Boolean {
        val moves = currentSolution?.moves?.size ?: return false
        when (key) {
            Key.DirectionLeft -> stepReplay(-1)
            Key.DirectionRight -> stepReplay(1)
            Key.MoveHome -> setReplay(0)
            Key.MoveEnd -> setReplay(moves)
            else -> return false
        }
        return true
    }

    override fun onCleared() {
        solverService.cancel()
    }

    companion object {
        private val compactJson = Json(from = PuzzleJson.json) { prettyPrint = false }

        /** Default "Max solutions" N. */
        const val DEFAULT_LIMIT = 100

        /** "New": a 4-ply proof game from the initial position with B still open. */
        val NEW_PUZZLE = Puzzle(
            title = "New puzzle",
            question = Question.PROOF_GAME,
            plies = 4,
            project = listOf("moves"),
        )
    }
}
