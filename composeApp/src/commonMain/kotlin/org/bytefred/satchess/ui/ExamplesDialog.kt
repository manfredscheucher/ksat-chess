package org.bytefred.satchess.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.serialization.SerializationException
import org.bytefred.satchess.puzzle.EXAMPLE_PUZZLE_SOURCES
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.PuzzleJson
import org.bytefred.satchess.ui.board.BoardView
import org.bytefred.satchess.ui.model.boardAt

/** A bundled example puzzle with its category. */
data class Example(val category: String, val puzzle: Puzzle)

/** Decodes the bundled examples; a broken entry is skipped instead of breaking the whole list. */
fun loadExamples(): List<Example> = EXAMPLE_PUZZLE_SOURCES.mapNotNull { source ->
    try {
        Example(source.category, PuzzleJson.decode(source.json))
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * What the Examples dialog remembers between openings: the open categories (all start collapsed),
 * the selected example and the scroll position. Reopening shows the tree exactly as it was left.
 */
class ExamplesTreeState {
    val expanded = mutableStateMapOf<String, Boolean>()
    var selected by mutableStateOf<Example?>(null)
    val scroll = ScrollState(0)
}

/** The bundled examples as a tree, like ktoy's: a click only selects; Load loads the selected one. */
@Composable
fun ExamplesDialog(
    examples: List<Example>,
    state: ExamplesTreeState,
    onLoad: (Puzzle) -> Unit,
    onDismiss: () -> Unit,
) {
    val byCategory = remember(examples) { examples.groupBy { it.category } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Examples") },
        text = {
            ScrollbarBox(state.scroll, Modifier.heightIn(max = 560.dp)) {
                Column(Modifier.verticalScroll(state.scroll).testTag("examples-tree")) {
                    for ((category, list) in byCategory) {
                        val open = state.expanded[category] == true
                        Row(
                            Modifier.fillMaxWidth().clickable { state.expanded[category] = !open }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChevronGlyph(open, MaterialTheme.colorScheme.onSurface)
                            Text(category, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                        }
                        HorizontalDivider()
                        if (open) {
                            for (example in list) {
                                ExampleLeaf(example.puzzle, example == state.selected) { state.selected = example }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { state.selected?.let { onLoad(it.puzzle) } },
                enabled = state.selected != null,
                modifier = Modifier.testTag("loadExample"),
            ) { Text("Load") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** An example: a radio button with the title; the selected one also shows its board and credits. */
@Composable
private fun ExampleLeaf(puzzle: Puzzle, selected: Boolean, onSelect: () -> Unit) {
    Column(Modifier.padding(start = 16.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(puzzle.title.ifEmpty { puzzle.id }, modifier = Modifier.padding(start = 8.dp))
        }
        if (selected) ExampleDetails(puzzle)
    }
}

@Composable
private fun ExampleDetails(puzzle: Puzzle) {
    val uriHandler = LocalUriHandler.current
    val thumbnailPly = if (puzzle.positions.containsKey(puzzle.plies.toString())) puzzle.plies else 0
    val cells = remember(puzzle) {
        try {
            puzzle.boardAt(thumbnailPly)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    Row(Modifier.padding(start = 32.dp, top = 4.dp, bottom = 8.dp)) {
        if (cells != null) {
            BoardView(cells, Modifier.size(92.dp), showCoordinates = false)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val meta = listOfNotNull(
                puzzle.composer,
                puzzle.source?.let { s -> listOfNotNull(s.name, s.year?.toString()).joinToString(", ") },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            puzzle.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            puzzle.source?.url?.let { url ->
                Text(
                    "Source",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { openUriSafely(uriHandler, url) },
                )
            }
        }
    }
}
