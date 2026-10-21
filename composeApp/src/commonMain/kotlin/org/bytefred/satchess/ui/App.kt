package org.bytefred.satchess.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.bytefred.satchess.ui.platform.sharedPuzzleJson

/** Width from which the screen uses the multi-column desktop layout. */
private val WIDE_LAYOUT = 900.dp

/** Width from which the results get their own (third) column. */
private val THREE_COLUMNS = 1200.dp

/**
 * The app root (doc/spec.md section 7): top bar, then a two-column editor + solve/results layout on
 * wide screens, or a single column with Puzzle / Results tabs and a sticky Solve bar on phones.
 */
@Composable
fun App(vm: PuzzleViewModel = viewModel { PuzzleViewModel() }) {
    SatChessTheme {
        val examples = remember { loadExamples() }
        var showExamples by remember { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { vm.start(sharedPuzzleJson(), examples) }
        LaunchedEffect(vm.message) {
            vm.message?.let {
                snackbar.showSnackbar(it)
                vm.showMessage(null)
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= WIDE_LAYOUT
            // Replay keys bubble up here from any focused element that does not use them (text fields do).
            val replayKeys = Modifier.onKeyEvent { event ->
                event.type == KeyEventType.KeyDown && (wide || vm.resultsView) && vm.replayKey(event.key)
            }
            Scaffold(
                modifier = replayKeys,
                topBar = { TopBar(vm, compact = !wide, onExamples = { showExamples = true }) },
                bottomBar = {
                    if (!wide) {
                        SolveBar(vm, Modifier.padding(8.dp))
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                if (wide) {
                    WideLayout(vm, threeColumns = maxWidth >= THREE_COLUMNS, Modifier.padding(padding))
                } else {
                    NarrowLayout(vm, Modifier.padding(padding))
                }
            }
        }

        if (showExamples) {
            ExampleBrowser(
                examples = examples,
                onPick = { vm.load(it); showExamples = false },
                onDismiss = { showExamples = false },
            )
        }
    }
}

/**
 * Desktop: the board column (timeline + board editor), the question column (header, question, start,
 * advanced, Solve) and, from [THREE_COLUMNS] on, a separate results column.
 */
@Composable
private fun WideLayout(vm: PuzzleViewModel, threeColumns: Boolean, modifier: Modifier) {
    Row(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScrollColumn(Modifier.weight(1f).widthIn(max = 600.dp)) {
            TimelineSection(vm)
            BoardEditorSection(vm)
        }
        ScrollColumn(Modifier.weight(1f).widthIn(max = 600.dp)) {
            PuzzleHeader(vm, compact = false)
            QuestionSection(vm)
            StartSection(vm)
            AdvancedSection(vm)
            SolveBar(vm)
            if (!threeColumns) ResultsSection(vm)
        }
        if (threeColumns) {
            ScrollColumn(Modifier.weight(1f).widthIn(max = 600.dp)) {
                if (vm.run.puzzle == null) {
                    Section("Results") { Text("Solutions appear here, with a replay board.") }
                }
                ResultsSection(vm)
            }
        }
    }
}

@Composable
private fun ScrollColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun NarrowLayout(vm: PuzzleViewModel, modifier: Modifier) {
    val resultsTab = vm.resultsView
    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = if (resultsTab) 1 else 0) {
            Tab(selected = !resultsTab, onClick = { vm.resultsView = false }, text = { Text("Puzzle") })
            Tab(
                selected = resultsTab,
                onClick = { vm.resultsView = true },
                text = { Text(if (vm.solutions.isEmpty()) "Results" else "Results (${vm.solutions.size})") },
                modifier = Modifier.testTag("resultsTab"),
            )
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (resultsTab) {
                if (vm.run.puzzle == null) Text("Nothing solved yet. Set up the puzzle and press Solve.")
                ResultsSection(vm)
            } else {
                PuzzleHeader(vm, compact = true)
                QuestionSection(vm, collapsible = true)
                TimelineSection(vm)
                BoardEditorSection(vm)
                StartSection(vm)
                AdvancedSection(vm)
            }
        }
    }
}

/**
 * Title, composer / source and our description of the loaded puzzle. [compact] (phone) shows two
 * lines of the description; a click expands it.
 */
@Composable
private fun PuzzleHeader(vm: PuzzleViewModel, compact: Boolean) {
    val p = vm.puzzle
    var expanded by remember(p) { mutableStateOf(!compact) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(p.title.ifEmpty { "Untitled puzzle" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("puzzleTitle"))
        val meta = listOfNotNull(p.composer, p.source?.let { s -> listOfNotNull(s.name, s.year?.toString()).joinToString(", ") })
        if (meta.isNotEmpty()) {
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        p.description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}
