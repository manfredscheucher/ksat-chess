package org.bytefred.satchess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.Goal
import org.bytefred.satchess.puzzle.MovePattern
import org.bytefred.satchess.puzzle.Question
import org.bytefred.satchess.ui.board.BoardColors
import org.bytefred.satchess.ui.board.BoardView
import org.bytefred.satchess.ui.board.drawCell
import org.bytefred.satchess.ui.board.drawEmptyMark
import org.bytefred.satchess.ui.board.drawOptions
import org.bytefred.satchess.ui.board.rememberPiecePainters
import org.bytefred.satchess.ui.model.ALL_OPTIONS
import org.bytefred.satchess.ui.model.ConstraintLevel
import org.bytefred.satchess.ui.model.EMPTY
import org.bytefred.satchess.ui.model.OPTIONS_TOOL
import org.bytefred.satchess.ui.model.PALETTE
import org.bytefred.satchess.ui.model.PliesUnit
import org.bytefred.satchess.ui.model.SquareState
import org.bytefred.satchess.ui.model.UNKNOWN
import org.bytefred.satchess.ui.model.boardAt
import org.bytefred.satchess.ui.model.constraintLevel
import org.bytefred.satchess.ui.model.defaultOptionsAt
import org.bytefred.satchess.ui.model.formatMoves
import org.bytefred.satchess.ui.model.label
import org.bytefred.satchess.ui.model.movePatternPlies
import org.bytefred.satchess.ui.model.optionsLabel
import org.bytefred.satchess.ui.model.pliesSentence
import org.bytefred.satchess.ui.model.sideToMoveAt
import org.bytefred.satchess.ui.model.squareStateAt
import org.bytefred.satchess.ui.model.toggleCastlingRight

/** Display name of ply [t] in a window of [plies]: A, 1, 2, ..., B. */
fun plyName(t: Int, plies: Int): String = when (t) {
    0 -> if (plies == 0) "A = B" else "A"
    plies -> "B"
    else -> t.toString()
}

// ── question row ─────────────────────────────────────────────────────────────

/**
 * Question preset, N with plies|moves toggle, goal, and the live plies sentence. [collapsible] (phone
 * layout) starts folded to a one-line summary so the board is visible without scrolling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionSection(vm: PuzzleViewModel, modifier: Modifier = Modifier, collapsible: Boolean = false) {
    val p = vm.puzzle
    var expanded by remember { mutableStateOf(!collapsible) }
    Section(
        if (expanded) "Question" else "${p.question.label} · ${p.plies} ${if (p.plies == 1) "ply" else "plies"} = ${formatMoves(p.plies)} moves · goal ${p.goal.label}",
        modifier,
        expanded = expanded,
        onToggle = if (collapsible) ({ expanded = !expanded }) else null,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ChoiceDropdown(p.question, Question.entries, { it.label }, vm::setQuestion, Modifier.testTag("question"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompactField(
                    value = vm.pliesText,
                    onValueChange = vm::setPliesInput,
                    modifier = Modifier.width(88.dp).testTag("plies"),
                    label = "N",
                    isError = vm.pliesError,
                )
                Spacer(Modifier.width(6.dp))
                ChoiceChips(null, vm.pliesUnit, PliesUnit.entries, { it.name.lowercase() }, vm::changePliesUnit)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel("Goal at B")
                Spacer(Modifier.width(8.dp))
                ChoiceDropdown(p.goal, Goal.entries, { it.label }, vm::setGoal)
            }
        }
        Text(
            if (vm.pliesError) "N must be a number of plies (0-200), or moves like 6.5" else pliesSentence(p.plies, p.start.sideToMove),
            style = MaterialTheme.typography.bodyMedium,
            color = if (vm.pliesError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (p.goal != Goal.NONE) {
            Text(
                "Both sides cooperate (helpmate), no forced mates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── timeline ────────────────────────────────────────────────────────────────

/** The (A)-1-2-...-(B) chips with constraint icons, "+" and the list of move patterns. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineSection(vm: PuzzleViewModel, modifier: Modifier = Modifier) {
    val p = vm.puzzle
    var addMenu by remember { mutableStateOf(false) }
    var pickPly by remember { mutableStateOf(false) }
    var editPattern by remember { mutableStateOf<Int?>(null) }
    val patternPlies = p.movePatternPlies()

    Section("Timeline", modifier) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (t in 0..p.plies) {
                if (t > 0) Connector(hasPattern = (t - 1) in patternPlies)
                PlyChip(
                    label = plyName(t, p.plies),
                    level = constraintLevel(p.boardAt(t), restricted = !p.squares[t.toString()].isNullOrEmpty()),
                    selected = t == vm.selectedPly,
                    onClick = { vm.selectPly(t) },
                )
            }
            Spacer(Modifier.width(12.dp))
            Box {
                OutlinedButton(onClick = { addMenu = true }, modifier = Modifier.testTag("timelineAdd")) { Text("+") }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Board at ply ...") },
                        onClick = { addMenu = false; pickPly = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Move pattern ...") },
                        enabled = p.plies > 0,
                        onClick = { addMenu = false; editPattern = vm.selectedPly.coerceAtMost(p.plies - 1) },
                    )
                }
            }
        }
        if (p.moves.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((key, pattern) in p.moves.entries.sortedBy { it.key.toIntOrNull() ?: 0 }) {
                    val t = key.toIntOrNull() ?: continue
                    InputChip(
                        selected = false,
                        onClick = { editPattern = t },
                        label = { Text("${plyName(t, p.plies)} to ${plyName(t + 1, p.plies)}: ${pattern.label()}") },
                        trailingIcon = {
                            Box(Modifier.clickable { vm.setMovePattern(t, null) }.semantics { contentDescription = "Remove" }) {
                                CloseGlyph(MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                }
            }
        }
        Text(
            "Filled: board given · half: some squares '?' · empty: all '?' · thick bar: move pattern",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (pickPly) {
        PlyPickerDialog(p.plies, onPick = { vm.selectPly(it); pickPly = false }, onDismiss = { pickPly = false })
    }
    editPattern?.let { t ->
        MovePatternDialog(
            initialPly = t,
            initial = p.moves[t.toString()],
            plies = p.plies,
            onSave = { ply, pattern ->
                if (ply != t) vm.setMovePattern(t, null)
                vm.setMovePattern(ply, pattern)
                editPattern = null
            },
            onDismiss = { editPattern = null },
        )
    }
}

@Composable
private fun PlyChip(label: String, level: ConstraintLevel, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.primary else colors.surfaceContainer,
        contentColor = if (selected) colors.onPrimary else colors.onSurface,
        modifier = Modifier.testTag("ply-$label"),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ConstraintGlyph(level, if (selected) colors.onPrimary else colors.primary)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Connector(hasPattern: Boolean) {
    val color = if (hasPattern) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.width(14.dp).height(10.dp)) {
        val stroke = if (hasPattern) size.height * 0.6f else size.height * 0.2f
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
    }
}

@Composable
private fun PlyPickerDialog(plies: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val t = text.trim().toIntOrNull()?.takeIf { it in 0..plies }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which ply?") },
        text = {
            CompactField(
                value = text,
                onValueChange = { text = it },
                label = "Ply (0 = A ... $plies = B)",
                isError = text.isNotBlank() && t == null,
            )
        },
        confirmButton = { TextButton(onClick = { t?.let(onPick) }, enabled = t != null) { Text("Edit board") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Edits the move pattern for the move from ply t to t+1 (SAN and/or individual fields). */
@Composable
private fun MovePatternDialog(
    initialPly: Int,
    initial: MovePattern?,
    plies: Int,
    onSave: (Int, MovePattern) -> Unit,
    onDismiss: () -> Unit,
) {
    var ply by remember { mutableStateOf(initialPly.toString()) }
    var san by remember { mutableStateOf(initial?.san ?: "") }
    var piece by remember { mutableStateOf(initial?.piece ?: "") }
    var from by remember { mutableStateOf(initial?.from ?: "") }
    var to by remember { mutableStateOf(initial?.to ?: "") }
    var promo by remember { mutableStateOf(initial?.promo ?: "") }
    var capture by remember { mutableStateOf(initial?.capture) }
    var check by remember { mutableStateOf(initial?.check) }
    var castle by remember { mutableStateOf(initial?.castle) }

    val t = ply.trim().toIntOrNull()?.takeIf { it in 0 until plies }
    val error = when {
        t == null -> "Ply must be between 0 and ${plies - 1}"
        piece.isNotEmpty() && (piece.length != 1 || piece[0] !in "KQRBNPkqrbnp") -> "Piece: one FEN letter"
        from.isNotEmpty() && Sq.parse(from) == null -> "From: a square like e2"
        to.isNotEmpty() && Sq.parse(to) == null -> "To: a square like e4"
        promo.isNotEmpty() && (promo.length != 1 || promo[0] !in "QRBNqrbn") -> "Promotion: Q, R, B or N"
        else -> null
    }
    val pattern = MovePattern(
        san = san.trim().ifEmpty { null },
        piece = piece.ifEmpty { null },
        from = from.ifEmpty { null },
        to = to.ifEmpty { null },
        capture = capture,
        promo = promo.ifEmpty { null },
        castle = castle,
        check = check,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move pattern") },
        text = {
            val vScroll = rememberScrollState()
            ScrollbarBox(vScroll) { Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(vScroll)) {
                CompactField(ply, { ply = it }, label = "Ply t (the move from t to t+1)", isError = t == null)
                CompactField(san, { san = it }, label = "SAN, e.g. Kf2 (optional)")
                FieldLabel("or any of these fields")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { CompactField(piece, { piece = it.trim() }, Modifier.fillMaxWidth(), label = "Piece") }
                    Box(Modifier.weight(1f)) { CompactField(from, { from = it.trim() }, Modifier.fillMaxWidth(), label = "From") }
                    Box(Modifier.weight(1f)) { CompactField(to, { to = it.trim() }, Modifier.fillMaxWidth(), label = "To") }
                    Box(Modifier.weight(1f)) { CompactField(promo, { promo = it.trim() }, Modifier.fillMaxWidth(), label = "Promo") }
                }
                TriStateChips("Capture", capture) { capture = it }
                TriStateChips("Check", check) { check = it }
                ChoiceChips("Castling", castle ?: "", listOf("", "any", "K", "Q", "k", "q"), { it.ifEmpty { "-" } }) {
                    castle = it.ifEmpty { null }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            } }
        },
        confirmButton = { TextButton(onClick = { t?.let { onSave(it, pattern) } }, enabled = error == null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** any / yes / no chips for an optional Boolean. */
@Composable
fun TriStateChips(title: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    ChoiceChips(title, value, listOf(null, true, false), { if (it == null) "any" else if (it) "yes" else "no" }, onChange)
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
    var dialogSquare by remember { mutableStateOf<Int?>(null) }
    val side = sideToMoveAt(p.start.sideToMove, t)
    val title = "Board at ${plyName(t, p.plies)}" + (if (t in 1 until p.plies) " (after ply $t)" else "") +
        " · " + (side?.let { if (it == 'w') "White to move" else "Black to move" } ?: "side to move free")
    val openOptions = { sq: Int -> vm.openOptions(OptionsTarget.Square(sq)) }

    Section(title, modifier) {
        val hybrid = vm.inputMode == InputMode.HYBRID
        BoardView(
            cells = vm.board,
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().testTag("editorBoard"),
            options = vm.boardOptions,
            showCoordinates = vm.showCoordinates,
            onTap = if (hybrid) vm::clickSquare else { sq -> dialogSquare = sq },
            onMove = vm::dragPiece,
            onDropOff = vm::dragOff,
            onOptions = openOptions,
        )
        BoardLegend()
        if (hybrid) Palette(vm.paletteSelection, withOptionsTool = true, onSelect = { vm.paletteSelection = it })
        Text(
            if (hybrid) {
                "Click a square to put the selected piece there, click again to empty it. Long press or right-click " +
                    "a square to allow several pieces on it. Drag a piece to move it, drag it off the board to remove it."
            } else {
                "Click a square and pick what stands there. Long press or right-click to allow several. " +
                    "Drag a piece to move it, drag it off the board to remove it."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::setInitialBoard) { Text("Initial") }
            OutlinedButton(onClick = vm::clearBoard) { Text("Clear") }
            OutlinedButton(onClick = vm::openBoard) { Text("All ?") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Every '?' square: ${optionsLabel(p.defaultOptionsAt(t))}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { vm.openOptions(OptionsTarget.UnknownSquares) },
                modifier = Modifier.testTag("unknownSquaresOptions"),
            ) { Text("Change ...") }
        }
        CompactField(
            value = vm.fenText,
            onValueChange = vm::setFenInput,
            modifier = Modifier.fillMaxWidth().testTag("fen"),
            label = if (t == 0) "FEN ('?' = unknown; a full FEN also sets the start)" else "FEN placement ('?' = unknown)",
            isError = vm.fenError != null,
            supporting = vm.fenError,
            mono = true,
        )
    }

    dialogSquare?.let { sq ->
        AlertDialog(
            onDismissRequest = { dialogSquare = null },
            title = { Text("Square ${Sq.name(sq)}") },
            text = {
                Palette(vm.board[sq], withOptionsTool = false, onSelect = { vm.setSquareTo(sq, it); dialogSquare = null })
            },
            confirmButton = {
                TextButton(onClick = { dialogSquare = null; openOptions(sq) }, modifier = Modifier.testTag("dialogOptions")) {
                    Text("Several options ...")
                }
            },
            dismissButton = { TextButton(onClick = { dialogSquare = null }) { Text("Cancel") } },
        )
    }
    vm.optionsTarget?.let { target ->
        val (heading, initial) = when (target) {
            is OptionsTarget.Square -> "Square ${Sq.name(target.sq)} at ${plyName(t, p.plies)}" to
                when (val state = p.squareStateAt(t, target.sq)) {
                    is SquareState.Fixed -> setOf(state.cell)
                    is SquareState.Options -> state.allowed
                }
            OptionsTarget.UnknownSquares -> "Every '?' square at ${plyName(t, p.plies)}" to p.defaultOptionsAt(t)
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
fun Palette(selected: Char, withOptionsTool: Boolean, onSelect: (Char) -> Unit) {
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

// ── start parameters ─────────────────────────────────────────────────────────

/** Side to move, castling and en passant at A. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartSection(vm: PuzzleViewModel, modifier: Modifier = Modifier) {
    val start = vm.puzzle.start
    Section("Start (A)", modifier) {
        ChoiceChips("Side to move", start.sideToMove, listOf("w", "b", "?"), {
            when (it) { "w" -> "White"; "b" -> "Black"; else -> "? (either)" }
        }, vm::setSideToMove)
        val mode = when (start.castling) { "auto", "-", "?" -> start.castling; else -> "custom" }
        ChoiceChips("Castling rights", mode, listOf("auto", "-", "custom", "?"), {
            when (it) { "auto" -> "auto"; "-" -> "none"; "custom" -> "custom"; else -> "? (free)" }
        }) { vm.setCastling(if (it == "custom") "KQkq" else it) }
        if (mode == "custom") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((right, name) in listOf('K' to "White O-O", 'Q' to "White O-O-O", 'k' to "Black O-O", 'q' to "Black O-O-O")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = right in start.castling,
                            onCheckedChange = { vm.setCastling(toggleCastlingRight(start.castling, right, it)) },
                        )
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Text(
            when (mode) {
                "auto" -> "A right exists iff king and rook are on their home squares."
                "?" -> "Each right is free (king and rook must still be at home)."
                else -> "Exactly these rights."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CompactField(
            value = vm.enPassantText,
            onValueChange = vm::setEnPassantInput,
            modifier = Modifier.width(260.dp),
            label = "En passant square (- or e.g. e3)",
            isError = vm.enPassantError != null,
            supporting = vm.enPassantError,
        )
    }
}
