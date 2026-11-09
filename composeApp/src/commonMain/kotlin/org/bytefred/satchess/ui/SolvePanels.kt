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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.UNKNOWN_SQUARES
import org.bytefred.satchess.sat.ShortestCheck
import org.bytefred.satchess.sat.Solution
import org.bytefred.satchess.ui.board.BoardView
import org.bytefred.satchess.ui.model.answerSummary
import org.bytefred.satchess.ui.model.countSummary
import org.bytefred.satchess.ui.model.groupByProjection
import org.bytefred.satchess.ui.model.isPathProjection
import org.bytefred.satchess.ui.model.sanLine
import org.bytefred.satchess.ui.model.similaritySummary
import org.bytefred.satchess.ui.model.squareDomainError

// ── advanced ────────────────────────────────────────────────────────────────

/** Max solutions, shortest and square domains (collapsible). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedSection(vm: PuzzleViewModel, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val p = vm.puzzle
    Section("Advanced", modifier, expanded = expanded, onToggle = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChoiceChips("Max solutions", vm.limit, SolutionLimit.entries, {
                when (it) { SolutionLimit.ONE -> "1"; SolutionLimit.N -> "limit"; SolutionLimit.ALL -> "all" }
            }) { vm.limit = it }
            if (vm.limit == SolutionLimit.N) {
                Spacer(Modifier.width(8.dp))
                CompactField(
                    value = vm.limitN.toString(),
                    onValueChange = { text -> text.toIntOrNull()?.takeIf { it > 0 }?.let { vm.limitN = it } },
                    modifier = Modifier.width(90.dp),
                    label = "how many",
                )
            }
        }
        CheckRow("Shortest: also prove that two plies less are not enough", p.shortest, vm::setShortest)
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
    FieldLabel("Allowed on a square ('.' = empty)")
    for ((ply, domains) in p.squares.entries.sortedBy { it.key.toIntOrNull() ?: 0 }) {
        for ((square, allowed) in domains) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val where = if (square == UNKNOWN_SQUARES) "every '?' square" else square
                Text("ply ${plyName(ply.toIntOrNull() ?: 0, p.plies)}: $where in $allowed", style = MaterialTheme.typography.bodyMedium)
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
        CompactField(ply, { ply = it }, Modifier.width(72.dp), label = "Ply", isError = t == null)
        CompactField(square, { square = it }, Modifier.width(90.dp), label = "Square")
        CompactField(allowed, { allowed = it }, Modifier.width(140.dp), label = "Allowed")
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
                solveStatus(run, vm.solutions.size, elapsed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun solveStatus(run: SolveRun, found: Int, elapsedMillis: Long): String {
    if (run.puzzle == null) return "Set up the puzzle, then press Solve."
    val parts = mutableListOf<String>()
    if (run.variables != null) parts += "${run.variables} vars / ${run.clauses} clauses"
    else if (run.running) parts += "encoding ..."
    parts += formatSeconds(elapsedMillis)
    parts += "found $found"
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
    val solutions = vm.solutions
    Section("Results", modifier) {
        run.error?.let { Text("Solver error: $it", color = MaterialTheme.colorScheme.error) }
        run.infos.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        run.done?.let { done ->
            val unverified = remember(solutions.size) { solutions.count { !it.verified } }
            Text(
                countSummary(solutions.size, done.complete, solved.expected, unverified),
                style = MaterialTheme.typography.titleMedium,
                color = if (unverified > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("countSummary"),
            )
            done.shortestCheck?.let { Text(shortestLabel(it, solved.plies), style = MaterialTheme.typography.bodyMedium) }
        }
        if (solutions.isEmpty()) {
            if (run.running) Text("No solution yet ...", style = MaterialTheme.typography.bodyMedium)
            return@Section
        }
        if (!isPathProjection(solved.project)) {
            val summary = remember(solutions.size) { answerSummary(groupByProjection(solutions)) }
            FieldLabel("Answer")
            Text(summary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        FieldLabel("Solutions")
        SolutionList(vm, showKey = !isPathProjection(solved.project))
        HorizontalDivider()
        ReplayPanel(vm)
    }
}

private fun shortestLabel(check: ShortestCheck, plies: Int): String = when (check) {
    ShortestCheck.PROVEN -> "Shortest: yes, ${plies - 2} plies are impossible."
    ShortestCheck.NOT_SHORTEST -> "Shortest: no, ${plies - 2} plies are enough."
    ShortestCheck.NOT_PROVEN_RELAXED ->
        "Shortest: unclear. ${plies - 2} plies are enough without the move patterns and middle boards."
    ShortestCheck.CANCELLED -> "Shortest: not checked."
}

/**
 * The flat solution list (default), or with "Group similar solutions" the groups of
 * [PuzzleViewModel.similarGroups]. Lazy and height-capped, so even hundreds of thousands of lines
 * cost only the visible rows.
 */
@Composable
private fun SolutionList(vm: PuzzleViewModel, showKey: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.groupSimilar = !vm.groupSimilar }) {
        Switch(checked = vm.groupSimilar, onCheckedChange = { vm.groupSimilar = it }, modifier = Modifier.testTag("groupSimilar"))
        Spacer(Modifier.width(8.dp))
        Text("Group similar solutions", style = MaterialTheme.typography.bodyMedium)
    }
    val count = vm.solutions.size
    val listModifier = Modifier.fillMaxWidth().heightIn(max = SOLUTION_LIST_HEIGHT).testTag("solutionList")
    if (!vm.groupSimilar) {
        LazyColumn(listModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(count) { i -> SolutionRow(vm, i, showKey) }
        }
        return
    }
    val groups = remember(count) { vm.similarGroups() }
    val open = remember { mutableStateMapOf<Int, Boolean>() }
    LazyColumn(listModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (group in groups) {
            val first = group.indices.first()
            val isOpen = open[first] == true
            item(key = "group-$first") {
                Surface(
                    onClick = { open[first] = !isOpen },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("similarGroup"),
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChevronGlyph(isOpen, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(similaritySummary(group.key), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        val n = group.indices.size
                        Text("$n ${if (n == 1) "solution" else "solutions"}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (isOpen) {
                items(group.indices, key = { "line-$it" }) { i ->
                    Box(Modifier.padding(start = 16.dp)) { SolutionRow(vm, i, showKey) }
                }
            }
        }
    }
}

/** Height of the solution list (it scrolls on its own inside the scrolling results column). */
private val SOLUTION_LIST_HEIGHT = 360.dp

@Composable
private fun SolutionRow(vm: PuzzleViewModel, i: Int, showKey: Boolean) {
    val solution = vm.solutions[i]
    Surface(
        onClick = { vm.selectSolution(i) },
        color = if (i == vm.selectedSolution) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
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
    var running by remember(solution) { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && vm.replayPly < solution.moves.size) {
            delay(RUN_STEP_MILLIS)
            vm.stepReplay(1)
        }
        running = false
    }
    // Arrow keys work right away: the replay takes the focus when a solution is shown; App's root key
    // handler covers the case where the focus is elsewhere (but not in a text field).
    LaunchedEffect(vm.run.puzzle, vm.selectedSolution) { focus.requestFocus() }
    Column(
        Modifier
            .focusRequester(focus)
            .focusable(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FieldLabel("Solution #${vm.selectedSolution + 1} (arrow keys to step)")
        Box(Modifier.widthIn(max = BOARD_MAX_WIDTH).fillMaxWidth()) {
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
        // same order as ktoy: reset, undo, step, run
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { running = false; vm.setReplay(0) },
                enabled = ply > 0,
                modifier = Modifier.testTag("replayReset"),
            ) { Text("Reset") }
            OutlinedButton(
                onClick = { running = false; vm.stepReplay(-1) },
                enabled = ply > 0,
                modifier = Modifier.testTag("replayBack"),
            ) { Text("Back") }
            Button(
                onClick = { running = false; vm.stepReplay(1) },
                enabled = ply < solution.moves.size,
                modifier = Modifier.testTag("replayStep"),
            ) { Text("Step") }
            Button(
                onClick = { running = !running },
                enabled = running || ply < solution.moves.size,
                modifier = Modifier.testTag("replayRun"),
            ) { Text(if (running) "Stop" else "Run") }
            Text("ply $ply / ${solution.moves.size}", style = MaterialTheme.typography.labelLarge)
        }
        if (solution.moves.isNotEmpty()) {
            Slider(
                value = ply.toFloat(),
                onValueChange = { vm.setReplay(it.toInt()) },
                valueRange = 0f..solution.moves.size.toFloat(),
                steps = (solution.moves.size - 1).coerceAtLeast(0),
                modifier = Modifier.widthIn(max = BOARD_MAX_WIDTH),
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

private const val RUN_STEP_MILLIS = 1000L

/** The board of [solution] at [ply], or null if its FEN is unreadable. */
private fun replayCells(solution: Solution, ply: Int): List<Char>? {
    val fen = solution.fens.getOrNull(ply) ?: return null
    return try {
        Fen.parsePattern(fen.substringBefore(' ')).cells
    } catch (e: IllegalArgumentException) {
        null
    }
}
