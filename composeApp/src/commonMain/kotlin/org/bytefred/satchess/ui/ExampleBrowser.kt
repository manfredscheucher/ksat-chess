package org.bytefred.satchess.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.SerializationException
import org.bytefred.satchess.puzzle.EXAMPLE_PUZZLE_SOURCES
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.PuzzleJson
import org.bytefred.satchess.ui.board.BoardView
import org.bytefred.satchess.ui.model.boardAt
import org.bytefred.satchess.ui.model.formatMoves
import org.bytefred.satchess.ui.model.label

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
 * Full-screen example browser like ktoy's: grouped by category (collapsible), each entry shows a
 * thumbnail of its target board, title, composer, source link, our description and the plies.
 */
@Composable
fun ExampleBrowser(examples: List<Example>, onPick: (Puzzle) -> Unit, onDismiss: () -> Unit) {
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    val byCategory = remember(examples) { examples.groupBy { it.category } }
    Surface(Modifier.fillMaxSize().testTag("exampleBrowser"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Examples", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            if (examples.isEmpty()) Text("No examples bundled.")
            LazyColumn(Modifier.fillMaxSize()) {
                for ((category, list) in byCategory) {
                    val open = collapsed[category] != true
                    item(key = "cat:$category") {
                        Row(
                            Modifier.fillMaxWidth().clickable { collapsed[category] = open }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChevronGlyph(open, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("  ${list.size}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                    if (open) {
                        items(list, key = { "ex:${it.category}/${it.puzzle.id}/${it.puzzle.title}" }) { example ->
                            ExampleRow(example.puzzle) { onPick(example.puzzle) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExampleRow(puzzle: Puzzle, onPick: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val thumbnailPly = if (puzzle.positions.containsKey(puzzle.plies.toString())) puzzle.plies else 0
    val cells = remember(puzzle) {
        try {
            puzzle.boardAt(thumbnailPly)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (cells != null) BoardView(cells, Modifier.size(92.dp), showCoordinates = false)
        Column(Modifier.weight(1f).widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(puzzle.title.ifEmpty { puzzle.id }, style = MaterialTheme.typography.titleSmall)
            val meta = listOfNotNull(
                puzzle.composer,
                puzzle.source?.let { s -> listOfNotNull(s.name, s.year?.toString()).joinToString(", ") },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${puzzle.question.label} · ${puzzle.plies} plies = PG ${formatMoves(puzzle.plies)}" +
                    (puzzle.expected?.let { " · expected $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            puzzle.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            puzzle.source?.url?.let { url ->
                Text(
                    "Source",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { uriHandler.openUri(url) },
                )
            }
        }
    }
}
