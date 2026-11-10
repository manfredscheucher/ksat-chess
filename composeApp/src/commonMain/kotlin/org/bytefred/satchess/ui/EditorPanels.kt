package org.bytefred.satchess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.Goal
import org.bytefred.satchess.puzzle.MovePattern
import org.bytefred.satchess.ui.board.BoardColors
import org.bytefred.satchess.ui.board.BoardView
import org.bytefred.satchess.ui.board.drawCell
import org.bytefred.satchess.ui.board.drawEmptyMark
import org.bytefred.satchess.ui.board.drawOptions
import org.bytefred.satchess.ui.board.rememberPiecePainters
import org.bytefred.satchess.ui.model.ALL_OPTIONS
import org.bytefred.satchess.ui.model.ConstraintLevel
import org.bytefred.satchess.ui.model.EMPTY
import org.bytefred.satchess.ui.model.MAX_PLIES
import org.bytefred.satchess.ui.model.OPTIONS_TOOL
import org.bytefred.satchess.ui.model.PALETTE
import org.bytefred.satchess.ui.model.SquareState
import org.bytefred.satchess.ui.model.UNKNOWN
import org.bytefred.satchess.ui.model.boardAt
import org.bytefred.satchess.ui.model.boardName
import org.bytefred.satchess.ui.model.castlingAfterToggle
import org.bytefred.satchess.ui.model.constraintLevel
import org.bytefred.satchess.ui.model.defaultOptionsAt
import org.bytefred.satchess.ui.model.label
import org.bytefred.satchess.ui.model.optionsLabel
import org.bytefred.satchess.ui.model.plyName
import org.bytefred.satchess.ui.model.shownCastling
import org.bytefred.satchess.ui.model.sideToMoveAt
import org.bytefred.satchess.ui.model.squareStateAt

/** The editor board and the replay board share this width, so both look the same. */
val BOARD_MAX_WIDTH = 420.dp

// ── setup ────────────────────────────────────────────────────────────────────

/**
 * Who starts, the castling rights at A, the number of plies and the goal at the end. [collapsible]
 * (phone layout) starts folded to a one-line summary so the board is visible without scrolling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupSection(vm: PuzzleViewModel, modifier: Modifier = Modifier, collapsible: Boolean = false) {
    val p = vm.puzzle
    val start = p.start
    val cells = p.boardAt(0)
    val rights = shownCastling(start.castling, cells)
    var expanded by remember { mutableStateOf(!collapsible) }
    Section(
        if (expanded) "Setup" else "${p.plies} ${if (p.plies == 1) "ply" else "plies"}" + (if (p.goal == Goal.NONE) "" else " · ends in ${p.goal.label}"),
        modifier,
        expanded = expanded,
        onToggle = if (collapsible) ({ expanded = !expanded }) else null,
    ) {
        ChoiceChips("Side to start", start.sideToMove, listOf("w", "b", "?"), {
            when (it) { "w" -> "White"; "b" -> "Black"; else -> "either" }
        }, vm::setSideToMove)
        FieldLabel("Available castling options")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((right, name) in listOf('K' to "White O-O", 'Q' to "White O-O-O", 'k' to "Black O-O", 'q' to "Black O-O-O")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = right in rights,
                        onCheckedChange = { vm.setCastling(castlingAfterToggle(start.castling, cells, right, it)) },
                    )
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            CompactField(
                value = vm.pliesText,
                onValueChange = vm::setPliesInput,
                modifier = Modifier.width(150.dp).testTag("plies"),
                label = "Plies (half-moves)",
                isError = vm.pliesError,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel("Goal at the end")
                Spacer(Modifier.width(8.dp))
                ChoiceDropdown(p.goal, Goal.entries, { it.label }, vm::setGoal, Modifier.testTag("goal"))
            }
        }
        if (vm.pliesError) {
            Text(
                "Plies must be a whole number (0-$MAX_PLIES)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ── timeline ────────────────────────────────────────────────────────────────

/**
 * The boards and the plies between them, one row each: "Board 0 (start)", "Ply 1", "Board 1", ...
 * A board row opens that board in the editor, a ply row the move editor. Rows that constrain the
 * puzzle (a board with known squares, a ply with a move pattern or the goal) are marked.
 */
@Composable
fun TimelineSection(vm: PuzzleViewModel, modifier: Modifier = Modifier) {
    val p = vm.puzzle
    Section("Timeline", modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (t in 0..p.plies) {
                val level = constraintLevel(p.boardAt(t), restricted = !p.squares[t.toString()].isNullOrEmpty())
                TimelineRow(
                    label = boardName(t, p.plies),
                    detail = when (level) {
                        ConstraintLevel.FIXED -> "given"
                        ConstraintLevel.PARTIAL -> "partly given"
                        ConstraintLevel.OPEN -> null
                    },
                    constrains = level != ConstraintLevel.OPEN,
                    selected = vm.selectedMove == null && t == vm.selectedPly,
                    indent = false,
                    onClick = { vm.selectPly(t) },
                    tag = "board-$t",
                )
                if (t < p.plies) {
                    val pattern = p.moves[t.toString()]
                    val goal = p.goal.takeIf { t == p.plies - 1 && it != Goal.NONE }
                    TimelineRow(
                        label = plyName(t),
                        detail = listOfNotNull(pattern?.label(), goal?.label).joinToString(", ").ifEmpty { null },
                        constrains = pattern != null || goal != null,
                        selected = vm.selectedMove == t,
                        indent = true,
                        onClick = { vm.selectMove(t) },
                        tag = "move-$t",
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    label: String,
    detail: String?,
    constrains: Boolean,
    selected: Boolean,
    indent: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.primary else if (constrains) colors.tertiaryContainer else colors.surfaceContainer,
        contentColor = if (selected) colors.onPrimary else if (constrains) colors.onTertiaryContainer else colors.onSurface,
        modifier = Modifier.fillMaxWidth().padding(start = if (indent) 24.dp else 0.dp).testTag(tag),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (constrains) FontWeight.Bold else FontWeight.Normal,
            )
            if (detail != null) {
                Spacer(Modifier.width(8.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── move editor ─────────────────────────────────────────────────────────────

/**
 * The middle column while a ply is selected: what the move from board t to t+1 must be. Every
 * field is optional; an empty field means "anything". The last ply also carries the goal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveEditorSection(vm: PuzzleViewModel, t: Int, modifier: Modifier = Modifier) {
    val p = vm.puzzle
    val pattern = p.moves[t.toString()] ?: MovePattern()
    val side = sideToMoveAt(p.start.sideToMove, t)
    val mover = side?.let { if (it == 'w') "White" else "Black" }
    val title = "${plyName(t)} · ${boardName(t, p.plies)} to ${boardName(t + 1, p.plies)}" +
        " · " + (mover?.let { "$it moves" } ?: "either side moves")
    fun set(next: MovePattern) = vm.setMovePattern(t, next.takeIf { it != MovePattern() })
    fun letter(upper: Char) = if (side == 'b') upper.lowercaseChar() else upper

    var san by remember(t, pattern.san) { mutableStateOf(pattern.san ?: "") }
    var from by remember(t, pattern.from) { mutableStateOf(pattern.from ?: "") }
    var to by remember(t, pattern.to) { mutableStateOf(pattern.to ?: "") }
    val fromOk = from.isEmpty() || Sq.parse(from) != null
    val toOk = to.isEmpty() || Sq.parse(to) != null

    Section(title, modifier) {
        CompactField(
            value = san,
            onValueChange = { san = it; set(pattern.copy(san = it.trim().ifEmpty { null })) },
            modifier = Modifier.width(180.dp).testTag("moveSan"),
            label = "The move, e.g. Kf2 or exd5",
        )
        FieldLabel("or any of these")
        // the pieces of the side to move; both colours when either side may start
        val pieces = buildList {
            if (side != 'b') addAll("KQRBNP".toList())
            if (side != 'w') addAll("kqrbnp".toList())
        }
        ChoiceChips("Piece", pattern.piece, listOf<String?>(null) + pieces.map { it.toString() }, { it?.let(::pieceName) ?: "any" }) {
            set(pattern.copy(piece = it))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactField(
                value = from,
                onValueChange = { v -> from = v.trim(); if (from.isEmpty() || Sq.parse(from) != null) set(pattern.copy(from = from.ifEmpty { null })) },
                modifier = Modifier.width(110.dp).testTag("moveFrom"),
                label = "From square",
                isError = !fromOk,
            )
            CompactField(
                value = to,
                onValueChange = { v -> to = v.trim(); if (to.isEmpty() || Sq.parse(to) != null) set(pattern.copy(to = to.ifEmpty { null })) },
                modifier = Modifier.width(110.dp).testTag("moveTo"),
                label = "To square",
                isError = !toOk,
            )
        }
        if (!fromOk || !toOk) Text("A square is a letter and a digit, like e4", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        ChoiceChips("Capture", pattern.capture, listOf(null, true, false), { if (it == null) "any" else if (it) "yes" else "no" }) {
            set(pattern.copy(capture = it))
        }
        ChoiceChips("Promotion", pattern.promo, listOf<String?>(null) + "QRBN".map { letter(it).toString() }, { it?.let(::pieceName) ?: "none" }) {
            set(pattern.copy(promo = it))
        }
        val castles = listOf<String?>(null, "any") + if (side != null) listOf(letter('K').toString(), letter('Q').toString()) else emptyList()
        ChoiceChips("Castling", pattern.castle, castles, {
            when (it) { null -> "no"; "any" -> "yes"; "K", "k" -> "O-O"; else -> "O-O-O" }
        }) { set(pattern.copy(castle = it)) }
        if (t == p.plies - 1) {
            ChoiceChips("After this move (goal at the end)", p.goal, Goal.entries, { it.label }, vm::setGoal)
        } else {
            ChoiceChips("After this move", pattern.check, listOf(null, true), { if (it == true) "check" else "any" }) {
                set(pattern.copy(check = it))
            }
        }
        OutlinedButton(onClick = { set(MovePattern()) }, enabled = pattern != MovePattern(), modifier = Modifier.testTag("moveClear")) { Text("Clear") }
    }
}

private fun pieceName(letter: String): String {
    val name = when (letter.single().uppercaseChar()) {
        'K' -> "King"; 'Q' -> "Queen"; 'R' -> "Rook"; 'B' -> "Bishop"; 'N' -> "Knight"; else -> "Pawn"
    }
    return if (letter.single().isLowerCase()) "black $name" else "white $name"
}

// ── board editor ────────────────────────────────────────────────────────────

/**
 * The board of the selected ply with palette, square options, Initial / Clear / All ?, the default for
 * the '?' squares and the two-way FEN field.
 */
@Composable
fun BoardEditorSection(vm: PuzzleViewModel, modifier: Modifier = Modifier) {
    val p = vm.puzzle
    val t = vm.selectedPly
    val side = sideToMoveAt(p.start.sideToMove, t)
    val title = boardName(t, p.plies) + " · " + (side?.let { if (it == 'w') "White to move" else "Black to move" } ?: "side to move free")
    val openOptions = { sq: Int -> vm.openOptions(OptionsTarget.Square(sq)) }

    Section(title, modifier) {
        BoardView(
            cells = vm.board,
            modifier = Modifier.widthIn(max = BOARD_MAX_WIDTH).fillMaxWidth().testTag("editorBoard"),
            options = vm.boardOptions,
            showCoordinates = vm.showCoordinates,
            onTap = vm::clickSquare,
            onMove = vm::dragPiece,
            onDropOff = vm::dragOff,
            onOptions = openOptions,
        )
        BoardLegend()
        Palette(vm.paletteSelection, withOptionsTool = true, onSelect = vm::selectPalette)
        Text(
            "Pick a piece, then click squares. Drag to move, drag off the board to remove.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if ('?' in vm.board) {
            OutlinedButton(
                onClick = { vm.openOptions(OptionsTarget.UnknownSquares) },
                modifier = Modifier.testTag("unknownSquaresOptions"),
            ) { Text("Set all '?' squares to ...") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Set board to", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = vm::setInitialBoard) { Text("Initial") }
            OutlinedButton(onClick = vm::clearBoard) { Text("Clear") }
            OutlinedButton(onClick = vm::openBoard) { Text("All ?") }
        }
        CompactField(
            value = vm.fenText,
            onValueChange = vm::setFenInput,
            modifier = Modifier.fillMaxWidth().testTag("fen"),
            label = if (t == 0) "FEN ('?' = unknown; a full FEN also sets the start)" else "FEN placement ('?' = unknown)",
            isError = vm.fenError != null,
            supporting = vm.fenError,
        )
    }

    vm.optionsTarget?.let { target ->
        val (heading, initial) = when (target) {
            is OptionsTarget.Square -> "Square ${Sq.name(target.sq)}, ${boardName(t, p.plies)}" to
                when (val state = p.squareStateAt(t, target.sq)) {
                    is SquareState.Fixed -> setOf(state.cell)
                    is SquareState.Options -> state.allowed
                }
            OptionsTarget.UnknownSquares -> "All '?' squares, ${boardName(t, p.plies)}" to p.defaultOptionsAt(t)
        }
        OptionsDialog(heading, initial, onApply = vm::applyOptions, onDismiss = vm::closeOptions)
    }
}

/**
 * Checkboxes for the 12 pieces and "empty", with shortcuts. One checked option = a fixed square; the
 * default of the '?' squares may also be a single option (e.g. "empty").
 */
@Composable
private fun OptionsDialog(heading: String, initial: Set<Char>, onApply: (Set<Char>) -> Unit, onDismiss: () -> Unit) {
    var allowed by remember(heading, initial) { mutableStateOf(initial) }
    val painters = rememberPiecePainters()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            val vScroll = rememberScrollState()
            ScrollbarBox(vScroll) { Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(vScroll)) {
                Text("What can stand here?", style = MaterialTheme.typography.bodyMedium)
                for (row in listOf(ALL_OPTIONS.take(6), ALL_OPTIONS.drop(6))) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (option in row) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Canvas(Modifier.width(32.dp).aspectRatio(1f)) {
                                    if (option == EMPTY) drawEmptyMark(Offset.Zero, size.width)
                                    else with(painters.getValue(option)) { draw(size) }
                                }
                                Checkbox(
                                    checked = option in allowed,
                                    onCheckedChange = { on -> allowed = if (on) allowed + option else allowed - option },
                                    modifier = Modifier.testTag("option-$option"),
                                )
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((label, set) in OPTION_SHORTCUTS) {
                        OutlinedButton(onClick = { allowed = set }) { Text(label) }
                    }
                }
                Text(
                    if (allowed.isEmpty()) "Check at least one option." else "Here: ${optionsLabel(allowed)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (allowed.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } }
        },
        confirmButton = {
            TextButton(onClick = { onApply(allowed) }, enabled = allowed.isNotEmpty(), modifier = Modifier.testTag("applyOptions")) {
                Text("Apply")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val OPTION_SHORTCUTS: List<Pair<String, Set<Char>>> = listOf(
    "Any" to ALL_OPTIONS.toSet(),
    "Kings or empty" to setOf('K', 'k', EMPTY),
    "White king or empty" to setOf('K', EMPTY),
    "Any piece (not empty)" to ALL_OPTIONS.toSet() - EMPTY,
    "None" to emptySet(),
)

/** How the editor board shows fixed squares and options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardLegend() {
    val painters = rememberPiecePainters()
    val textMeasurer = rememberTextMeasurer()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("boardLegend"),
    ) {
        @Composable
        fun item(label: String, draw: DrawScope.(Float) -> Unit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(24.dp).aspectRatio(1f)) {
                    drawRect(BoardColors.light)
                    draw(size.width)
                }
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
        item("fixed piece") { drawCell('K', Offset.Zero, it, painters, textMeasurer) }
        item("fixed empty") { }
        item("one of these") { drawOptions(setOf('K', EMPTY), Offset.Zero, it, painters) }
        item("anything") { drawCell(UNKNOWN, Offset.Zero, it, painters, textMeasurer) }
    }
}

/**
 * 2 x 7 palette: white pieces + empty, black pieces + '?'. [withOptionsTool] adds the "Options" tool
 * (a click on a square then opens its options dialog).
 */
@Composable
fun Palette(selected: Char?, withOptionsTool: Boolean, onSelect: (Char) -> Unit) {
    val painters = rememberPiecePainters()
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val rows = listOf(PALETTE.take(6) + EMPTY, PALETTE.drop(6).take(6) + UNKNOWN)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.widthIn(max = 400.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (cell in row) {
                    val isSelected = cell == selected
                    val description = when (cell) {
                        EMPTY -> "empty"
                        UNKNOWN -> "unknown"
                        else -> "piece $cell"
                    }
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.primary else colors.outlineVariant, RoundedCornerShape(6.dp))
                            .clickable { onSelect(cell) }
                            .semantics { contentDescription = description }
                            .testTag("palette-$cell"),
                    ) {
                        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(3.dp)) {
                            val square = size.width
                            drawRect(if (isSelected) colors.primaryContainer else BoardColors.light)
                            if (cell == EMPTY) {
                                drawLine(colors.outline, Offset(square * 0.25f, square * 0.75f), Offset(square * 0.75f, square * 0.25f), square * 0.05f)
                                drawCircle(colors.outline, square * 0.25f, style = Stroke(square * 0.05f))
                            } else {
                                drawCell(cell, Offset.Zero, square, painters, textMeasurer)
                            }
                        }
                    }
                }
            }
        }
        if (withOptionsTool) {
            FilterChip(
                selected = selected == OPTIONS_TOOL,
                onClick = { onSelect(OPTIONS_TOOL) },
                label = { Text("One of several ...") },
                leadingIcon = {
                    Canvas(Modifier.width(18.dp).aspectRatio(1f)) { drawOptions(setOf('K', EMPTY), Offset.Zero, size.width, painters) }
                },
                modifier = Modifier.testTag("palette-options"),
            )
        }
    }
}
