package org.bytefred.satchess.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.bytefred.ksat.facade.Solver
import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.RuleFlags
import org.bytefred.satchess.sat.Solution
import org.bytefred.satchess.ui.board.BoardView
import org.bytefred.satchess.ui.model.answerSummary
import org.bytefred.satchess.ui.model.countSummary
import org.bytefred.satchess.ui.model.groupByProjection
import org.bytefred.satchess.ui.model.groupSimilar
import org.bytefred.satchess.ui.model.isPathProjection
import org.bytefred.satchess.ui.model.sanLine
import org.bytefred.satchess.ui.model.similaritySummary
import org.bytefred.satchess.ui.model.squareDomainError

private val Solver.label: String
    get() = when (this) {
        Solver.CADICAL -> "CaDiCaL"
        Solver.MINISAT -> "MiniSat"
        Solver.KISSAT -> "kissat"
        Solver.MICROSAT -> "microsat"
    }

// ── advanced ────────────────────────────────────────────────────────────────

/** Solver, max solutions, shortest, projection, rule flags and square domains (collapsible). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedSection(vm: PuzzleViewModel, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val p = vm.puzzle
    Section("Advanced", modifier, expanded = expanded, onToggle = { expanded = !expanded }) {
        ChoiceChips("SAT solver", vm.solver, listOf(Solver.CADICAL, Solver.MINISAT, Solver.KISSAT), { it.label }) { vm.solver = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChoiceChips("Max solutions", vm.limit, SolutionLimit.entries, {
                when (it) { SolutionLimit.ONE -> "1"; SolutionLimit.N -> "N"; SolutionLimit.ALL -> "all" }
            }) { vm.limit = it }
            if (vm.limit == SolutionLimit.N) {
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = vm.limitN.toString(),
                    onValueChange = { text -> text.toIntOrNull()?.takeIf { it > 0 }?.let { vm.limitN = it } },
                    label = { Text("N") },
                    singleLine = true,
                    modifier = Modifier.width(90.dp),
                )
            }
        }
        CheckRow("Shortest: also prove that N - 2 plies are not enough", p.shortest, vm::setShortest)
        OutlinedTextField(
            value = vm.projectionText,
            onValueChange = vm::setProjectionInput,
            label = { Text("Projection (what makes two solutions distinct)") },
            isError = vm.projectionError,
            supportingText = {
                Text(
                    if (vm.projectionError) "Items: moves, board@t, square@t:h4, piece@t:K, sideToMove@0 (comma separated)"
                    else "e.g. moves, board@0 / square@0:h4 / piece@0:K",
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FieldLabel("Rules")
        CheckRow("Exactly one king per side", p.rules.oneKingEach) { vm.setRules(p.rules.copy(oneKingEach = it)) }
        CheckRow("Check rules (no move into / staying in check)", p.rules.checkRules) { vm.setRules(p.rules.copy(checkRules = it)) }
        ChoiceChips("Material sanity (pawn and piece counts, bishop colours)", p.rules.materialSane, listOf(null, true, false), {
            when (it) { null -> "auto (on with '?')"; true -> "on"; false -> "off" }
        }) { vm.setRules(RuleFlags(p.rules.oneKingEach, p.rules.checkRules, it)) }
        SquareDomains(vm)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Per-ply square domains, e.g. ply 0, h4 must hold one of QRBNqrbn. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SquareDomains(vm: PuzzleViewModel) {
    val p = vm.puzzle
    FieldLabel("Square domains (allowed contents, '.' = empty)")
    for ((ply, domains) in p.squares.entries.sortedBy { it.key.toIntOrNull() ?: 0 }) {
        for ((square, allowed) in domains) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ply ${plyName(ply.toIntOrNull() ?: 0, p.plies)}: $square in $allowed", fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { vm.setSquareDomain(ply.toIntOrNull() ?: 0, square, "") }) { Text("Remove") }
            }
        }
    }
    var ply by remember { mutableStateOf("0") }
    var square by remember { mutableStateOf("") }
    var allowed by remember { mutableStateOf("") }
    val t = ply.trim().toIntOrNull()?.takeIf { it in 0..p.plies }
    val sqOk = Sq.parse(square.trim()) != null
    val allowedError = squareDomainError(allowed.trim())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(ply, { ply = it }, label = { Text("Ply") }, singleLine = true, isError = t == null, modifier = Modifier.width(72.dp))
        OutlinedTextField(square, { square = it }, label = { Text("Square") }, singleLine = true, modifier = Modifier.width(90.dp))
        OutlinedTextField(allowed, { allowed = it }, label = { Text("Allowed") }, singleLine = true, modifier = Modifier.width(140.dp))
        OutlinedButton(
            onClick = {
                vm.setSquareDomain(t ?: 0, square.trim(), allowed.trim())
                square = ""
                allowed = ""
            },
            enabled = t != null && sqOk && allowedError == null,
        ) { Text("Add") }
    }
}

// ── solve bar ───────────────────────────────────────────────────────────────

/** Solve / Cancel with the live status (vars / clauses, elapsed, found k). */
@Composable
fun SolveBar(vm: PuzzleViewModel, modifier: Modifier = Modifier) {
    val run = vm.run
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(run.running) {
        while (run.running) {
            elapsed = vm.elapsedMillis()
            delay(100)
        }
        elapsed = vm.finishedMillis
    }
    Surface(
        modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (run.running) {
                OutlinedButton(onClick = vm::cancel, modifier = Modifier.testTag("cancel")) { Text("Cancel") }
            } else {
                Button(onClick = vm::solve, enabled = vm.canSolve, modifier = Modifier.testTag("solve")) { Text("Solve") }
            }
            Text(
                solveStatus(run, elapsed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun solveStatus(run: SolveRun, elapsedMillis: Long): String {
    if (run.puzzle == null) return "Set up the question, then solve."
    val parts = mutableListOf<String>()
    if (run.variables != null) parts += "${run.variables} vars / ${run.clauses} clauses"
    else if (run.running) parts += "encoding ..."
    parts += formatSeconds(elapsedMillis)
    parts += "found ${run.solutions.size}"
    when {
        run.error != null -> parts += "failed"
        run.cancelled -> parts += "cancelled"
        run.running -> parts += "solving ..."
    }
    return parts.joinToString(" · ")
}

private fun formatSeconds(millis: Long): String {
    val tenths = millis / 100
    return "${tenths / 10}.${tenths % 10} s"
}

// ── results ─────────────────────────────────────────────────────────────────

/** Count line, answer summary (non-path projections), solution list and the replay board. */
@Composable
fun ResultsSection(vm: PuzzleViewModel, modifier: Modifier = Modifier) {
    val run = vm.run
    val solved = run.puzzle ?: return
    Section("Results", modifier) {
        run.error?.let { Text("Solver error: $it", color = MaterialTheme.colorScheme.error) }
        run.infos.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        run.done?.let { done ->
            Text(
                countSummary(run.solutions.size, done.complete, solved.expected),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("countSummary"),
            )
        }
        if (run.solutions.isEmpty()) {
            if (run.running) Text("No solution yet ...", style = MaterialTheme.typography.bodyMedium)
            return@Section
        }
        if (!isPathProjection(solved.project)) {
            val groups = groupByProjection(run.solutions)
            FieldLabel("Answer")
            Text(answerSummary(groups), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        FieldLabel("Solutions")
        SolutionList(vm, showKey = !isPathProjection(solved.project))
        HorizontalDivider()
        ReplayPanel(vm)
    }
}

/** The flat solution list (default), or with "Group similar solutions" the groups of [groupSimilar]. */
@Composable
private fun SolutionList(vm: PuzzleViewModel, showKey: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.groupSimilar = !vm.groupSimilar }) {
        Switch(checked = vm.groupSimilar, onCheckedChange = { vm.groupSimilar = it }, modifier = Modifier.testTag("groupSimilar"))
        Spacer(Modifier.width(8.dp))
        Text("Group similar solutions", style = MaterialTheme.typography.bodyMedium)
    }
    val solutions = vm.run.solutions
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!vm.groupSimilar) {
            for (i in solutions.indices) SolutionRow(vm, i, showKey)
            return@Column
        }
        val groups = remember(solutions) { groupSimilar(solutions) }
        for (group in groups) key(group.indices.first()) {
            var open by remember { mutableStateOf(false) }
            Surface(
                onClick = { open = !open },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("similarGroup"),
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChevronGlyph(open, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(similaritySummary(group.key), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    val n = group.indices.size
                    Text("$n ${if (n == 1) "line" else "lines"}", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (open) {
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in group.indices) SolutionRow(vm, i, showKey)
                }
            }
        }
    }
}

@Composable
private fun SolutionRow(vm: PuzzleViewModel, i: Int, showKey: Boolean) {
    val solution = vm.run.solutions[i]
    Surface(
        onClick = { vm.selectSolution(i) },
        color = if (i == vm.selectedSolution) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(36.dp))
            Column(Modifier.weight(1f)) {
                if (showKey) Text(solution.projectedKey, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    sanLine(solution.moves.map { it.san }, whiteStarts(solution)).ifEmpty { "(no moves)" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            VerifiedBadge(solution.verified)
        }
    }
}

@Composable
private fun VerifiedBadge(verified: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (verified) colors.primaryContainer else colors.errorContainer,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (verified) {
                CheckGlyph(colors.primary)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                if (verified) "verified" else "not verified",
                style = MaterialTheme.typography.labelSmall,
                color = if (verified) colors.onPrimaryContainer else colors.onErrorContainer,
            )
        }
    }
}

private fun whiteStarts(solution: Solution): Boolean =
    solution.fens.firstOrNull()?.split(' ')?.getOrNull(1) != "b"

/** Board of the selected solution at the replay ply, slider, step buttons, SAN list, arrow keys. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplayPanel(vm: PuzzleViewModel) {
    val solution = vm.currentSolution ?: return
    val ply = vm.replayPly
    val cells = remember(solution, ply) { replayCells(solution, ply) }
    val lastMove = solution.moves.getOrNull(ply - 1)
    val focus = remember { FocusRequester() }
    Column(
        Modifier
            .focusRequester(focus)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { vm.stepReplay(-1); true }
                    Key.DirectionRight -> { vm.stepReplay(1); true }
                    Key.MoveHome -> { vm.setReplay(0); true }
                    Key.MoveEnd -> { vm.setReplay(solution.moves.size); true }
                    else -> false
                }
            }
            .focusable(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FieldLabel("Replay of solution #${vm.selectedSolution + 1} (arrow keys step)")
        Box(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
            if (cells != null) {
                BoardView(
                    cells = cells,
                    modifier = Modifier.fillMaxWidth().testTag("replayBoard"),
                    showCoordinates = vm.showCoordinates,
                    highlights = lastMove?.let { setOf(it.from, it.to) } ?: emptySet(),
                    onTap = { focus.requestFocus() },
                )
            } else {
                Text("Cannot show this position (invalid FEN from the solver).", color = MaterialTheme.colorScheme.error)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.stepReplay(-1) }, enabled = ply > 0) { Text("<") }
            Text("ply $ply / ${solution.moves.size}", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { vm.stepReplay(1) }, enabled = ply < solution.moves.size) { Text(">") }
        }
        if (solution.moves.isNotEmpty()) {
            Slider(
                value = ply.toFloat(),
                onValueChange = { vm.setReplay(it.toInt()) },
                valueRange = 0f..solution.moves.size.toFloat(),
                steps = (solution.moves.size - 1).coerceAtLeast(0),
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        if (solution.moves.isNotEmpty()) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val labels = sanLine(solution.moves.map { it.san }, whiteStarts(solution)).split(' ')
            var moveIndex = 0
            for (token in labels) {
                if (token.endsWith(".")) {
                    Text(token, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp))
                    continue
                }
                val target = ++moveIndex
                val current = target == ply
                Surface(
                    onClick = { vm.setReplay(target); focus.requestFocus() },
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(token, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
        }
    }
}

/** The board of [solution] at [ply], or null if its FEN is unreadable. */
private fun replayCells(solution: Solution, ply: Int): List<Char>? {
    val fen = solution.fens.getOrNull(ply) ?: return null
    return try {
        Fen.parsePattern(fen.substringBefore(' ')).cells
    } catch (e: IllegalArgumentException) {
        null
    }
}
